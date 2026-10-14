package com.sportshub.app

import com.sportshub.app.data.Feed
import com.sportshub.app.data.FollowedTeam
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelsTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun decodesBackendFeedShape() {
        // Shape taken from the backend's /feed response (snake_case keys, optional fields omitted or null).
        val feed = json.decodeFromString<Feed>(
            """
            {"live": [],
             "upcoming": [{"id":"1","league_id":"soccer:eng.1","league_name":"Premier League",
               "date":"2026-10-18T13:00:00Z","status":"scheduled","status_detail":null,"title":"MAN @ LEE",
               "home":{"id":"360","name":"Manchester United","logo":"x.png"},
               "away":{"id":"357","name":"Leeds"}, "home_score":null,"unknown_future_field":1}],
             "recent": [{"id":"f1-2026-17","league_id":"f1:f1","league_name":"Formula 1",
               "date":"2026-10-11T12:00:00Z","status":"final","title":"Singapore Grand Prix",
               "sessions":[{"name":"Qualifying","date":"2026-10-10T13:00:00Z"}],"summary":"P2 Leclerc"}],
             "news": [], "errors": ["x"]}
            """.trimIndent(),
        )
        val match = feed.upcoming.single()
        assertEquals("Manchester United", match.home?.name)
        assertEquals("soccer:eng.1", match.leagueId)
        assertNull(match.homeScore)
        val race = feed.recent.single()
        assertNull(race.home) // F1 events have no two sides
        assertEquals("P2 Leclerc", race.summary)
        assertEquals(1, race.sessions.size)
    }

    @Test
    fun followKeyMatchesBackendFormat() {
        val team = FollowedTeam("soccer:eng.1", "Premier League", "360", "Manchester United")
        assertEquals("soccer:eng.1:360", team.key)
        assertEquals("f1:f1:ferrari", FollowedTeam("f1:f1", "Formula 1", "ferrari", "Ferrari").key)
    }
}
