package com.sportshub.app

import com.sportshub.app.data.EspnParse
import com.sportshub.app.data.F1Provider
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.League
import com.sportshub.app.data.Team
import com.sportshub.app.data.TeamOverview
import com.sportshub.app.data.TeamRef
import com.sportshub.app.data.TsdbProvider
import com.sportshub.app.data.TtlCache
import com.sportshub.app.data.mergeFeed
import com.sportshub.app.data.parseFollow
import com.sportshub.app.data.splitFixtures
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

private val LEAGUE = League("soccer:eng.1", "soccer", "eng.1", "Premier League")

private const val NRR = ",{\"name\":\"netRunRate\",\"value\":0.5}"

private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

private fun event(state: String = "post", name: String = "STATUS_FULL_TIME", homeScore: String = """{"displayValue":"2"}""") = obj(
    """
    {"id":"1","date":"2026-10-10T16:30Z","shortName":"TOT @ MUN","competitions":[{
      "status":{"type":{"state":"$state","name":"$name","shortDetail":"FT"}},
      "venue":{"fullName":"Old Trafford"},
      "competitors":[
        {"homeAway":"home","score":$homeScore,"team":{"id":"360","displayName":"Manchester United"}},
        {"homeAway":"away","score":"1","team":{"id":"367","displayName":"Tottenham","logos":[{"href":"x.png"}]}}]}]}
    """.trimIndent(),
)

private fun fx(id: String, status: String, day: Int) = Fixture(
    id = id, leagueId = "l", leagueName = "L", date = "2026-10-%02dT00:00:00Z".format(day), status = status, title = id,
)

private fun overview(vararg upcoming: Fixture) =
    TeamOverview(team = Team("t", "T", leagueId = "l"), upcoming = upcoming.toList())

class DataLayerTest {
    @Test
    fun eventStatusMapping() {
        assertEquals("scheduled", EspnParse.parseEvent(event("pre", "STATUS_SCHEDULED"), LEAGUE)!!.status)
        assertEquals("live", EspnParse.parseEvent(event("in", "STATUS_IN_PROGRESS"), LEAGUE)!!.status)
        assertEquals("final", EspnParse.parseEvent(event("post"), LEAGUE)!!.status)
        assertEquals("postponed", EspnParse.parseEvent(event("pre", "STATUS_POSTPONED"), LEAGUE)!!.status)
    }

    @Test
    fun eventTeamsScoresAndLogo() {
        val f = EspnParse.parseEvent(event(), LEAGUE)!!
        assertEquals("Manchester United", f.home?.name)
        assertEquals("Tottenham", f.away?.name)
        assertEquals("2", f.homeScore) // object-style score
        assertEquals("1", f.awayScore) // plain-string score
        assertEquals("x.png", f.away?.logo)
        assertEquals("Old Trafford", f.venue)
        assertEquals("2026-10-10T16:30:00Z", f.date) // normalised to full ISO-8601 UTC
    }

    @Test
    fun eventWithoutDateIsDropped() {
        val ev = obj("""{"id":"1","competitions":[{"competitors":[]}]}""")
        assertNull(EspnParse.parseEvent(ev, LEAGUE))
    }

    @Test
    fun neutralVenueEventUsesCompetitorOrder() {
        val ev = obj(
            """{"id":"9","date":"2026-05-01T10:00Z","competitions":[{"competitors":[
               {"team":{"id":"1","displayName":"A"}},{"team":{"id":"2","displayName":"B"}}]}]}""",
        )
        val f = EspnParse.parseEvent(ev, LEAGUE)!!
        assertEquals("1", f.home?.id)
        assertEquals("2", f.away?.id)
    }

    private fun entry(id: String, name: String, rank: Int, points: Int, extra: String = "") = """
        {"team":{"id":"$id","displayName":"$name"},"stats":[
          {"name":"rank","value":$rank},{"name":"points","value":$points},{"name":"gamesPlayed","value":6},
          {"name":"wins","value":5},{"name":"ties","value":1},{"name":"losses","value":0},
          {"name":"pointDifferential","value":11}$extra]}"""

    @Test
    fun standingsAreSortedAndKeepExtras() {
        val data = obj(
            """{"children":[{"name":"League","standings":{"entries":[
               ${entry("2", "B", 2, 10)},
               ${entry("1", "A", 1, 12, NRR)}]}}]}""",
        )
        val group = EspnParse.parseStandings(data).single()
        assertEquals(listOf("A", "B"), group.rows.map { it.team.name })
        val top = group.rows[0]
        assertEquals(6, top.played); assertEquals(5, top.wins); assertEquals(1, top.draws); assertEquals(0, top.losses)
        assertEquals(12.0, top.points!!, 0.0); assertEquals(11.0, top.goalDiff!!, 0.0)
        assertEquals(mapOf("netRunRate" to 0.5), top.extra)
    }

    @Test
    fun standingsFlattenNestedConferences() {
        val data = obj(
            """{"children":[
               {"name":"East","children":[{"name":"Atlantic","standings":{"entries":[${entry("1", "A", 1, 3)}]}}]},
               {"name":"West","standings":{"entries":[${entry("2", "B", 1, 3)}]}}]}""",
        )
        assertEquals(listOf("Atlantic", "West"), EspnParse.parseStandings(data).map { it.name })
    }

    @Test
    fun standingsMapCricketStatNames() {
        val data = obj(
            """{"children":[{"name":"IPL","standings":{"entries":[{"team":{"id":"1","displayName":"RCB"},"stats":[
               {"name":"matchesPlayed","value":14},{"name":"matchesWon","value":9},{"name":"matchesLost","value":5},
               {"name":"matchPoints","value":18},{"name":"netrr","value":0.783}]}]}}]}""",
        )
        val row = EspnParse.parseStandings(data).single().rows.single()
        assertEquals(14, row.played); assertEquals(9, row.wins); assertEquals(5, row.losses)
        assertEquals(18.0, row.points!!, 0.0)
        assertEquals(mapOf("netRunRate" to 0.783), row.extra)
    }

    @Test
    fun newsParsing() {
        val items = EspnParse.parseNews(
            obj(
                """{"articles":[{"id":7,"headline":"H","published":"2026-10-10T10:00:00Z",
                   "links":{"web":{"href":"http://a"}},"images":[{"url":"i.png"}]}]}""",
            ),
            "x:y",
        )
        assertEquals("7", items[0].id); assertEquals("http://a", items[0].url); assertEquals("i.png", items[0].image)
        assertEquals("2026-10-10T10:00:00Z", items[0].published)
    }

    @Test
    fun followKeys() {
        assertEquals("soccer:eng.1" to "360", parseFollow("soccer:eng.1:360"))
        assertEquals("f1:f1" to "red_bull", parseFollow("f1:f1:red_bull"))
        for (bad in listOf("360", "soccer:360", "soccer:eng.1:")) {
            try { parseFollow(bad); fail("expected failure for $bad") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun splitFixturesOrdersAndLimits() {
        val all = listOf(fx("p1", "final", 1), fx("p2", "final", 2), fx("n2", "scheduled", 20), fx("n1", "scheduled", 15), fx("live", "live", 10))
        val (live, upcoming, recent) = splitFixtures(all, upcomingN = 1, recentN = 5)
        assertEquals(listOf("live"), live.map { it.id })
        assertEquals(listOf("n1"), upcoming.map { it.id }) // soonest first
        assertEquals(listOf("p2", "p1"), recent.map { it.id }) // most recent first
    }

    @Test
    fun raceStatusAndSessions() {
        val race = obj(
            """{"round":"5","raceName":"Test GP","date":"2026-06-07","time":"13:00:00Z",
               "Circuit":{"circuitName":"Circuit","Location":{"locality":"Town","country":"Land"}},
               "Qualifying":{"date":"2026-06-06","time":"14:00:00Z"},
               "FirstPractice":{"date":"2026-06-05","time":"11:30:00Z"}}""",
        )
        val before = F1Provider.parseRace(race, "2026", Instant.parse("2026-06-01T00:00:00Z"))
        assertEquals("scheduled", before.status)
        assertEquals(listOf("Practice 1", "Qualifying"), before.sessions.map { it.name })
        assertEquals("Circuit, Town, Land", before.venue)
        assertEquals("live", F1Provider.parseRace(race, "2026", Instant.parse("2026-06-07T14:00:00Z")).status)
        assertEquals("final", F1Provider.parseRace(race, "2026", Instant.parse("2026-06-08T00:00:00Z")).status)
    }

    @Test
    fun tsdbMatchesSpellingVariants() {
        val teams = listOf(Team("1", "Royal Challengers Bengaluru", leagueId = "c"), Team("2", "Gujarat Titans", leagueId = "c"))
        assertEquals("1", TsdbProvider.matchTeam("Royal Challengers Bangalore", teams)?.id)
        assertNull(TsdbProvider.matchTeam("Totally Different FC", teams))
        val f = TsdbProvider.parseEvent(
            obj(
                """{"idEvent":"9","strTimestamp":"2026-05-31T14:00:00","strEvent":"GT vs RCB",
                   "strHomeTeam":"Gujarat Titans","strAwayTeam":"Royal Challengers Bangalore",
                   "strStatus":"Match Finished","strResult":"RCB won by 5 wickets"}""",
            ),
            League("cricket:8048", "cricket", "8048", "IPL"), teams,
        )!!
        assertEquals("final", f.status); assertEquals("2", f.home?.id); assertEquals("1", f.away?.id)
        assertEquals("2026-05-31T14:00:00Z", f.date)
    }

    @Test
    fun cacheServesStaleWhenRefreshFails() = runBlocking {
        var clock = 0L
        val cache = TtlCache { clock }
        assertEquals("fresh", cache.getOrSet("k", 10) { "fresh" })
        clock = 100 // expired
        assertEquals("fresh", cache.getOrSet<String>("k", 10) { throw java.io.IOException("down") })
        try { cache.getOrSet<String>("never", 10) { throw java.io.IOException("down") }; fail() } catch (_: java.io.IOException) {}
        clock = 0
        assertEquals("fresh", cache.getOrSet("k", 10) { "not called" }) // fresh entry is not refetched
    }

    @Test
    fun feedDedupesSharedFixturesAndReportsErrors() {
        val derby = fx("derby", "scheduled", 20)
        val feed = mergeFeed(
            listOf(
                "a:b:one" to Result.success(overview(derby, fx("own-one", "scheduled", 25))),
                "a:b:two" to Result.success(overview(derby, fx("own-two", "scheduled", 26))),
                "a:b:bad" to Result.failure(RuntimeException("boom")),
            ),
        )
        assertEquals(listOf("derby", "own-one", "own-two"), feed.upcoming.map { it.id })
        assertEquals(1, feed.errors.size)
        assertTrue("bad" in feed.errors[0])
    }
}
