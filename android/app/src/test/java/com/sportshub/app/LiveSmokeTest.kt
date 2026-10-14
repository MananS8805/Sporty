package com.sportshub.app

import com.sportshub.app.data.EspnProvider
import com.sportshub.app.data.F1Provider
import com.sportshub.app.data.F1_LEAGUE
import com.sportshub.app.data.HttpGate
import com.sportshub.app.data.EspnParse
import com.sportshub.app.data.League
import com.sportshub.app.data.National
import com.sportshub.app.data.NationalProvider
import com.sportshub.app.data.Predictions
import com.sportshub.app.data.WatchRights
import com.sportshub.app.data.a
import com.sportshub.app.data.o
import com.sportshub.app.data.objects
import com.sportshub.app.data.str
import com.sportshub.app.data.TsdbProvider
import com.sportshub.app.data.TtlCache
import com.sportshub.app.data.splitFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Hits the real APIs (about a dozen requests) to check the parsers against live data.
 * Skipped unless LIVE_TESTS=1, so normal test runs never touch the network.
 */
class LiveSmokeTest {
    private val gate = HttpGate()
    private val cache = TtlCache()
    private val espn = EspnProvider(gate, cache)
    private val epl = League("soccer:eng.1", "soccer", "eng.1", "Premier League")
    private val ipl = League("cricket:8048", "cricket", "8048", "Indian Premier League")

    @Test
    fun football() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val table = espn.standings(epl).single().rows
        println("EPL table: ${table.size} teams, leader ${table.first().team.name} ${table.first().points}")
        assertTrue(table.size >= 18)
        val teams = espn.teams(epl)
        assertTrue(teams.size >= 18)
        val schedule = espn.teamSchedule(epl, "360")
        val (_, upcoming, recent) = splitFixtures(schedule)
        println("Man United: next ${upcoming.firstOrNull()?.title}, last ${recent.firstOrNull()?.title} ${recent.firstOrNull()?.homeScore}-${recent.firstOrNull()?.awayScore}")
        assertTrue(upcoming.isNotEmpty() && recent.isNotEmpty())
        val news = espn.news(epl, "360", 5)
        println("News: ${news.firstOrNull()?.headline}")
        assertTrue(news.isNotEmpty())
    }

    @Test
    fun cricket() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val rows = espn.standings(ipl).first().rows
        println("IPL: ${rows.first().team.name} played=${rows.first().played} pts=${rows.first().points} extra=${rows.first().extra}")
        assertTrue(rows.first().points != null)
        val teams = espn.teams(ipl)
        assertTrue(teams.size >= 8)
        val fixtures = TsdbProvider(gate, cache).fixtures(ipl, teams)
        println("IPL fixtures from TheSportsDB: ${fixtures.size}, first ${fixtures.firstOrNull()?.title} -> ${fixtures.firstOrNull()?.home?.id}")
        assertTrue(fixtures.isNotEmpty())
    }

    @Test
    fun formula1() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val f1 = F1Provider(gate, cache)
        val teams = f1.teams()
        assertTrue(teams.any { it.id == "ferrari" && it.color == "DC0000" })
        val groups = f1.standings()
        println("F1: ${groups.map { it.name to it.rows.size }}, constructors leader ${groups[0].rows.first().team.name}")
        val (_, upcoming, recent) = splitFixtures(f1.fixtures("ferrari"))
        println("Ferrari: next ${upcoming.firstOrNull()?.title}, last ${recent.firstOrNull()?.title} ${recent.firstOrNull()?.summary}")
        assertTrue(upcoming.isNotEmpty() && recent.first().summary != null)
        val news = espn.news(League("racing:f1", "racing", "f1", F1_LEAGUE.name), limit = 5)
        assertTrue(news.isNotEmpty())
    }

    @Test
    fun footballForecastFromBookmakerOdds() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val next = splitFixtures(espn.teamSchedule(epl, "360")).second.first()
        val summary = espn.summary(epl, next.id)
        val p = Predictions.fromSummary(summary, next)
        println("Forecast ${next.title}: home=${p.home} draw=${p.draw} away=${p.away} source=${p.source}")
        p.factors.forEach { println("   factor: ${it.label} = ${it.value}") }
        assertTrue(p.available)
        assertTrue(Math.abs(p.home!! + p.draw!! + p.away!! - 1.0) < 1e-9)
        val broadcasts = summary.a("broadcasts").objects().mapNotNull { b -> b.str("region")?.let { it to b.o("media").str("name").orEmpty() } }
        val watch = WatchRights.forRegion("IN", next, broadcasts, "India")
        println("Watch in India: ${watch.options.map { it.provider }} verified ${watch.verifiedOn}")
        assertTrue(watch.options.isNotEmpty())
    }

    @Test
    fun nationalFootballIndia() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val nat = NationalProvider(espn, cache)
        val teams = nat.footballTeams()
        println("National football teams: ${teams.size}")
        assertTrue(teams.size > 150 && teams.any { it.id == "4385" })
        val fixtures = nat.footballFixtures("4385")
        val (_, upcoming, recent) = splitFixtures(fixtures)
        println("India: ${fixtures.size} fixtures across ${fixtures.map { it.leagueName }.distinct()}")
        println("   next ${upcoming.firstOrNull()?.title} (${upcoming.firstOrNull()?.leagueName}), last ${recent.firstOrNull()?.title}")
        assertTrue(fixtures.isNotEmpty())
    }

    @Test
    fun nationalCricketIndiaAndSquad() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val nat = NationalProvider(espn, cache)
        val fixtures = nat.cricketFixtures("6")
        val (live, upcoming, recent) = splitFixtures(fixtures)
        println("India cricket: ${fixtures.size} fixtures, series ${fixtures.map { it.leagueName }.distinct()}")
        println("   live=${live.size} next=${upcoming.firstOrNull()?.title} ${upcoming.firstOrNull()?.date} last=${recent.firstOrNull()?.title}")
        assertTrue(fixtures.isNotEmpty())
        val any = (live + upcoming + recent).first()
        val eventLeague = League(any.leagueId, "cricket", any.leagueId.substringAfter(':'), any.leagueName)
        val squad = EspnParse.parseCricketSquad(espn.summary(eventLeague, any.id), "6")
        println("India squad: ${squad?.players?.size} players, e.g. ${squad?.players?.take(3)?.map { it.name + (if (it.captain) " (c)" else "") }}")
        assertTrue(squad != null && squad.players.size >= 11)
    }

    @Test
    fun iplSeasonFromEspnAndPlayingXi() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val season = espn.seasonEvents(ipl, 2026)
        val rcb = season.filter { it.home?.id == "335970" || it.away?.id == "335970" }
        println("IPL 2026 from ESPN: ${season.size} matches, RCB ${rcb.size}, last ${rcb.last().title} ${rcb.last().statusDetail}")
        assertTrue(season.size > 60 && rcb.size >= 14)
        val squad = EspnParse.parseCricketSquad(espn.summary(ipl, rcb.last().id), "335970")
        println("RCB squad: ${squad?.players?.size} (${squad?.note?.take(40)})")
        assertTrue(squad != null)
    }

    @Test
    fun footballRosterAndF1Drivers() = runBlocking {
        assumeTrue(System.getenv("LIVE_TESTS") == "1")
        val roster = espn.roster(epl, "360")
        println("Man United roster: ${roster.size}, e.g. ${roster.take(3).map { it.name + " " + it.position }}")
        assertTrue(roster.size > 20)
        val drivers = F1Provider(gate, cache).drivers("ferrari")
        println("Ferrari drivers: ${drivers.map { it.name + " #" + it.jersey + " " + it.detail }}")
        assertTrue(drivers.size >= 2)
    }
}
