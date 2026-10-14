package com.sportshub.app

import com.sportshub.app.data.EspnParse
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.National
import com.sportshub.app.data.Odds
import com.sportshub.app.data.Player
import com.sportshub.app.data.Predictions
import com.sportshub.app.data.TeamRef
import com.sportshub.app.data.WatchRights
import com.sportshub.app.data.sortPlayers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun obj(json: String) = Json.parseToJsonElement(json) as JsonObject

private fun match(leagueId: String = "soccer:eng.1", title: String = "MAN @ LEE") = Fixture(
    id = "1", leagueId = leagueId, leagueName = "League", date = "2026-10-18T13:00:00Z", status = "scheduled", title = title,
    home = TeamRef("357", "Leeds United"), away = TeamRef("360", "Manchester United"),
)

class InsightsTest {
    @Test
    fun americanOddsToProbability() {
        assertEquals(0.5, Odds.americanToProb(100.0), 1e-9)
        assertEquals(0.5, Odds.americanToProb(-100.0), 1e-9)
        assertEquals(100.0 / 235.0, Odds.americanToProb(135.0), 1e-9) // +135 underdog
        assertEquals(160.0 / 260.0, Odds.americanToProb(-160.0), 1e-9) // -160 favourite
        assertEquals("+135", Odds.formatAmerican(135.0))
        assertEquals("-160", Odds.formatAmerican(-160.0))
    }

    @Test
    fun marginIsRemovedSoProbabilitiesSumToOne() {
        val raw = listOf(Odds.americanToProb(135.0), Odds.americanToProb(230.0), Odds.americanToProb(185.0))
        val (norm, margin) = Odds.normalise(raw)
        assertEquals(1.0, norm.sum(), 1e-9)
        assertTrue("bookmakers always keep a margin", margin > 0)
        assertEquals(raw.sum() - 1.0, margin, 1e-9)
    }

    private val footballSummary = obj(
        """
        {"pickcenter":[{"provider":{"name":"DraftKings"},"overUnder":2.5,"spread":-0.5,"overOdds":-160.0,
           "homeTeamOdds":{"moneyLine":135},"awayTeamOdds":{"moneyLine":185},"drawOdds":{"moneyLine":230}}],
         "lastFiveGames":[{"team":{"displayName":"Leeds United"},"events":[{"gameResult":"W"},{"gameResult":"D"}]},
                          {"team":{"displayName":"Manchester United"},"events":[{"gameResult":"L"}]}],
         "seasonseries":[{"type":"head-to-head","summary":"MAN leads series 2-1-2"}]}
        """.trimIndent(),
    )

    @Test
    fun footballPredictionFromBookmakerOdds() {
        val p = Predictions.fromSummary(footballSummary, match(), mapOf("357" to 9, "360" to 13))
        assertTrue(p.available)
        assertEquals(1.0, p.home!! + p.draw!! + p.away!!, 1e-9)
        assertTrue("home +135 is shorter than away +185", p.home!! > p.away!!)
        assertEquals("DraftKings odds via ESPN", p.source)
        val labels = p.factors.map { it.label }
        assertTrue(labels.containsAll(listOf("Bookmaker odds", "Margin removed", "Total goals line", "Handicap", "Recent form", "Head-to-head", "League position")))
        assertEquals("Leeds United 9th · Manchester United 13th", p.factors.first { it.label == "League position" }.value)
    }

    @Test
    fun twoWayMarketHasNoDraw() {
        val s = obj("""{"pickcenter":[{"provider":{"name":"X"},"homeTeamOdds":{"moneyLine":-150},"awayTeamOdds":{"moneyLine":130}}]}""")
        val p = Predictions.fromSummary(s, match("basketball:nba"))
        assertNull(p.draw)
        assertEquals(1.0, p.home!! + p.away!!, 1e-9)
    }

    @Test
    fun noOddsMeansNoNumberButContextRemains() {
        val s = obj("""{"pickcenter":[],"odds":[],"seasonseries":[{"type":"head-to-head","summary":"India leads 3-1"}]}""")
        val p = Predictions.fromSummary(s, match("cricket:24289", "IND v WI"))
        assertFalse(p.available)
        assertNotNull(p.unavailableReason)
        assertEquals(listOf("Head-to-head"), p.factors.map { it.label })
    }

    @Test
    fun ordinals() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 22).map(Predictions::ordinal))
    }

    @Test
    fun indiaRightsComeFromCuratedTableWithVerificationDate() {
        val epl = WatchRights.forRegion("IN", match(), emptyList(), "India")
        assertEquals(listOf("JioHotstar", "Star Sports"), epl.options.map { it.provider })
        assertEquals(WatchRights.VERIFIED_ON, epl.verifiedOn)
        assertTrue(epl.searchUrl.startsWith("https://www.google.com/search?q="))
        assertEquals("FanCode", WatchRights.forRegion("IN", match("f1:f1", "Singapore GP"), emptyList(), "India").options.single().provider)
        assertEquals("Sony LIV", WatchRights.forRegion("IN", match("soccer:uefa.champions"), emptyList(), "India").options.first().provider)
    }

    @Test
    fun unknownRightsFallBackToSearchNotAGuess() {
        val laliga = WatchRights.forRegion("IN", match("soccer:esp.1"), emptyList(), "India")
        assertTrue(laliga.options.isEmpty())
        val bilateral = WatchRights.forRegion("IN", match("cricket:24289", "India v West Indies"), emptyList(), "India")
        assertTrue("bilateral series are flagged unconfirmed", bilateral.options.all { !it.confirmed })
    }

    @Test
    fun otherRegionsOnlyShowApiBroadcastersForThatRegion() {
        val us = WatchRights.forRegion("US", match(), listOf("us" to "USA Network", "gb" to "Sky"), "United States")
        assertEquals(listOf("USA Network"), us.options.map { it.provider })
        assertNull(us.verifiedOn)
        assertTrue(WatchRights.forRegion("BR", match(), listOf("us" to "USA Network"), "Brazil").options.isEmpty())
    }

    @Test
    fun cricketSquadPrefersFullSquadElseFallsBackToPlayingXi() {
        val full = obj(
            """{"squads":[{"team":{"id":"6"},"athletes":[
                 {"id":"1","displayName":"Shreyas Iyer","captain":true,"position":{"abbreviation":"TBT","name":"Top-order batter"}},
                 {"id":"2","displayName":"Rishabh Pant","keeper":true,"position":{"name":"Wicketkeeper batter"}}]}]}""",
        )
        val squad = EspnParse.parseCricketSquad(full, "6")!!
        assertNull(squad.note)
        assertEquals(2, squad.players.size)
        assertTrue(squad.players[0].captain)
        assertTrue(squad.players[1].keeper)

        val xi = obj("""{"squads":[],"rosters":[{"team":{"id":"335970"},"roster":[{"captain":true,"athlete":{"id":"9","displayName":"Rajat Patidar"}}]}]}""")
        val fallback = EspnParse.parseCricketSquad(xi, "335970")!!
        assertNotNull(fallback.note)
        assertTrue(fallback.players.single().captain)
        assertNull(EspnParse.parseCricketSquad(xi, "999"))
    }

    @Test
    fun footballRosterFlatOrGroupedAndSortedByPosition() {
        val flat = obj(
            """{"athletes":[
                 {"id":"3","displayName":"Bruno Fernandes","jersey":"8","age":31,"position":{"displayName":"Midfielder"},"citizenship":"Portugal"},
                 {"id":"1","displayName":"Senne Lammens","jersey":"1","position":{"displayName":"Goalkeeper"}},
                 {"id":"2","displayName":"Luke Shaw","jersey":"23","position":{"displayName":"Defender"}}]}""",
        )
        assertEquals(listOf("Senne Lammens", "Luke Shaw", "Bruno Fernandes"), sortPlayers(EspnParse.parseRoster(flat)).map { it.name })
        assertEquals("Portugal", EspnParse.parseRoster(flat).first().country)

        val grouped = obj("""{"athletes":[{"position":"offense","items":[{"id":"7","displayName":"QB One","position":{"abbreviation":"QB"}}]}]}""")
        assertEquals(listOf("QB One"), EspnParse.parseRoster(grouped).map { it.name })
    }

    @Test
    fun playerSortFallsBackToJerseyThenName() {
        val p = listOf(Player("a", "Zed", "Forward", "9"), Player("b", "Amy", "Forward", "10"), Player("c", "Bob", "Forward", null))
        assertEquals(listOf("Zed", "Amy", "Bob"), sortPlayers(p).map { it.name })
    }

    @Test
    fun nationalSeriesMatchingIsStrict() {
        assertTrue(National.seriesMentions("West Indies tour of India 2026/27", "India"))
        assertTrue(National.seriesMentions("India tour of New Zealand 2026/27", "India"))
        assertFalse("Indian Premier League is not India", National.seriesMentions("Indian Premier League", "India"))
        assertFalse(National.seriesMentions("England Women Under-19s tour of India", "India"))
        assertFalse(National.seriesMentions("India Women tour of Australia", "India"))
        assertFalse(National.seriesMentions("Sri Lanka tour of Pakistan 2026/27", "India"))
    }

    @Test
    fun nationalCricketTeamsHaveLogosAndAreInTheNationalLeague() {
        assertTrue(National.cricketTeams.size >= 15)
        val india = National.cricketTeams.first { it.id == "6" }
        assertEquals("India", india.name)
        assertEquals(National.CRICKET.id, india.leagueId)
        assertTrue(india.logo!!.endsWith("/6.png"))
    }
}
