package com.sportshub.app.data

import kotlinx.serialization.json.JsonObject
import java.time.LocalDateTime
import java.time.ZoneOffset

/** TheSportsDB (free key): fills the gap for cricket fixtures, which ESPN lacks. */
class TsdbProvider(private val gate: HttpGate, private val cache: TtlCache) {
    companion object {
        private const val BASE = "https://www.thesportsdb.com/api/v1/json/3"

        // ESPN league id -> TheSportsDB league id. Extend as leagues are verified.
        val LEAGUE_MAP = mapOf("cricket:8048" to "4460") // Indian Premier League

        /** 1.0 = identical. Edit-distance based, enough to match "Bangalore" with "Bengaluru". */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            val prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var diag = prev[0]
                prev[0] = i
                for (j in 1..b.length) {
                    val tmp = prev[j]
                    prev[j] = minOf(prev[j] + 1, prev[j - 1] + 1, diag + if (a[i - 1] == b[j - 1]) 0 else 1)
                    diag = tmp
                }
            }
            return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
        }

        fun matchTeam(name: String?, teams: List<Team>): Team? {
            if (name == null) return null
            var best: Team? = null
            var bestRatio = 0.0
            for (t in teams) for (cand in listOfNotNull(t.name, t.shortName)) {
                val r = similarity(name.lowercase(), cand.lowercase())
                if (r > bestRatio) { best = t; bestRatio = r }
            }
            return if (bestRatio >= 0.75) best else null
        }

        fun parseEvent(ev: JsonObject, league: League, teams: List<Team>): Fixture? {
            val date = runCatching { LocalDateTime.parse(ev.str("strTimestamp")).toInstant(ZoneOffset.UTC) }.getOrNull()
                ?: return null

            fun ref(name: String?): TeamRef? {
                if (name == null) return null
                val t = matchTeam(name, teams) ?: return TeamRef(name, name)
                return TeamRef(t.id, t.name, t.abbreviation, t.logo)
            }

            val done = ev.str("strStatus") in setOf("Match Finished", "FT") || !ev.str("strResult").isNullOrEmpty()
            return Fixture(
                id = "tsdb-${ev.str("idEvent")}", leagueId = league.id, leagueName = league.name, date = date.toString(),
                status = if (done) "final" else "scheduled",
                statusDetail = ev.str("strResult")?.takeIf { it.isNotEmpty() } ?: ev.str("strStatus"),
                title = ev.str("strEvent") ?: "", home = ref(ev.str("strHomeTeam")), away = ref(ev.str("strAwayTeam")),
                homeScore = ev.str("intHomeScore"), awayScore = ev.str("intAwayScore"), venue = ev.str("strVenue"),
            )
        }
    }

    fun supports(leagueId: String) = leagueId in LEAGUE_MAP

    suspend fun fixtures(league: League, teams: List<Team>): List<Fixture> {
        val id = LEAGUE_MAP.getValue(league.id)

        suspend fun one(kind: String): List<JsonObject> = cache.getOrSet("tsdb:$kind:$id", 10 * 60_000L) {
            gate.getJson("$BASE/$kind.php", mapOf("id" to id)).asObj().a("events").objects()
        }
        return (one("eventspastleague") + one("eventsnextleague"))
            .mapNotNull { parseEvent(it, league, teams) }.sortedBy { it.date }
    }
}
