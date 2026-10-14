package com.sportshub.app

import com.sportshub.app.data.Fixture
import com.sportshub.app.data.TeamRef
import com.sportshub.app.notify.AlertKind
import com.sportshub.app.notify.MatchWatcher
import com.sportshub.app.notify.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun game(
    id: String = "1", status: String = "scheduled", home: String? = "1", away: String? = "0",
    leagueId: String = "soccer:eng.1", date: String = "2026-10-18T13:00:00Z", detail: String? = null,
) = Fixture(
    id = id, leagueId = leagueId, leagueName = "Premier League", date = date, status = status, title = "MAN @ LEE",
    home = TeamRef("357", "Leeds"), away = TeamRef("360", "Man United"), homeScore = home, awayScore = away, statusDetail = detail,
)

class NotifyTest {
    @Test
    fun firstSightingNeverAlerts() {
        val (alerts, state) = MatchWatcher.diff(emptyMap(), listOf(game(status = "live"), game("2", status = "final")))
        assertTrue("following a team must not dump old matches on you", alerts.isEmpty())
        assertEquals(2, state.size)
    }

    @Test
    fun kickoffWhenScheduledBecomesLive() {
        val before = MatchWatcher.diff(emptyMap(), listOf(game(status = "scheduled", home = null, away = null))).second
        val (alerts, _) = MatchWatcher.diff(before, listOf(game(status = "live", home = "0", away = "0")))
        assertEquals(listOf(AlertKind.Kickoff), alerts.map { it.kind })
        assertEquals("Kickoff: Leeds v Man United", alerts.single().title)
    }

    @Test
    fun goalWhenLiveScoreChanges() {
        val before = MatchWatcher.diff(emptyMap(), listOf(game(status = "live", home = "0", away = "0"))).second
        val (alerts, state) = MatchWatcher.diff(before, listOf(game(status = "live", home = "1", away = "0")))
        assertEquals(listOf(AlertKind.Score), alerts.map { it.kind })
        assertEquals("Goal! Leeds 1–0 Man United", alerts.single().title)
        // Same score again: nothing new to say.
        assertTrue(MatchWatcher.diff(state, listOf(game(status = "live", home = "1", away = "0"))).first.isEmpty())
    }

    @Test
    fun fullTimeReportsTheScore() {
        val before = MatchWatcher.diff(emptyMap(), listOf(game(status = "live", home = "2", away = "2"))).second
        val (alerts, _) = MatchWatcher.diff(before, listOf(game(status = "final", home = "2", away = "3")))
        assertEquals(AlertKind.FullTime, alerts.single().kind)
        assertEquals("Leeds 2–3 Man United", alerts.single().text)
    }

    @Test
    fun matchMissedWhileLiveStillGetsAFullTimeAlert() {
        val before = MatchWatcher.diff(emptyMap(), listOf(game(status = "scheduled", home = null, away = null))).second
        val (alerts, _) = MatchWatcher.diff(before, listOf(game(status = "final", home = "1", away = "0")))
        assertEquals(listOf(AlertKind.FullTime), alerts.map { it.kind })
    }

    @Test
    fun cricketScoresAreNotGoalAlertsButResultsAre() {
        val live = game(leagueId = "cricket:8048", status = "live", home = "120/3", away = null)
        val before = MatchWatcher.diff(emptyMap(), listOf(live)).second
        val noisy = MatchWatcher.diff(before, listOf(live.copy(homeScore = "131/3")))
        assertTrue("cricket scorelines change every ball", noisy.first.isEmpty())
        val done = MatchWatcher.diff(noisy.second, listOf(live.copy(status = "final", statusDetail = "RCB won by 5 wickets.")))
        assertEquals("RCB won by 5 wickets.", done.first.single().text)
    }

    @Test
    fun f1RaceStartAndResult() {
        val race = Fixture("f1-2026-18", "f1:f1", "Formula 1", "2026-10-25T20:00:00Z", "scheduled", title = "United States Grand Prix")
        val before = MatchWatcher.diff(emptyMap(), listOf(race)).second
        val start = MatchWatcher.diff(before, listOf(race.copy(status = "live")))
        assertEquals("United States Grand Prix is under way", start.first.single().title)
        val end = MatchWatcher.diff(start.second, listOf(race.copy(status = "final", summary = "P2 Leclerc · P6 Hamilton")))
        assertEquals("P2 Leclerc · P6 Hamilton", end.first.single().text)
    }

    @Test
    fun postponedMatchesStaySilent() {
        val before = MatchWatcher.diff(emptyMap(), listOf(game(status = "scheduled"))).second
        assertTrue(MatchWatcher.diff(before, listOf(game(status = "postponed"))).first.isEmpty())
    }

    @Test
    fun stateMapIsCapped() {
        val many = (1..700).map { game("g$it", status = "scheduled") }
        assertEquals(600, MatchWatcher.diff(emptyMap(), many).second.size)
    }

    @Test
    fun remindersOnlyForFutureKickoffsInsideTheHorizon() {
        val now = Instant.parse("2026-10-14T12:00:00Z").toEpochMilli()
        val soon = game("soon", date = "2026-10-14T13:00:00Z")
        val tooSoon = game("tooSoon", date = "2026-10-14T12:10:00Z") // inside the 15-minute lead
        val tomorrow = game("tomorrow", date = "2026-10-15T12:00:00Z")
        val farAway = game("far", date = "2026-12-01T12:00:00Z")
        val finished = game("done", status = "final", date = "2026-10-13T12:00:00Z")
        val due = Reminders.due(listOf(soon, tooSoon, tomorrow, farAway, finished), now, leadMinutes = 15)
        assertEquals(listOf("soon", "tomorrow"), due.map { it.first.id })
        assertEquals(45 * 60_000L, due.first { it.first.id == "soon" }.second)
    }
}
