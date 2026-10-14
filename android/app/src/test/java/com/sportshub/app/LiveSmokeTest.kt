package com.sportshub.app

import com.sportshub.app.data.EspnProvider
import com.sportshub.app.data.F1Provider
import com.sportshub.app.data.F1_LEAGUE
import com.sportshub.app.data.HttpGate
import com.sportshub.app.data.League
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
}
