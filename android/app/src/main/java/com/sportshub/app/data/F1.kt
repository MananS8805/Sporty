package com.sportshub.app.data

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

val F1_LEAGUE = League(id = "f1:f1", sport = "f1", slug = "f1", name = "Formula 1", abbreviation = "F1")

/** Formula 1 via Jolpica-F1 (the Ergast successor). "Teams" are constructors. */
class F1Provider(private val gate: HttpGate, private val cache: TtlCache) {
    companion object {
        private const val BASE = "https://api.jolpi.ca/ergast/f1"
        private const val MINUTE = 60_000L
        private val RACE_DURATION: Duration = Duration.ofMinutes(150)

        // Jolpica has no branding data; keep team colours here for themed UI.
        val COLORS = mapOf(
            "ferrari" to "DC0000", "mercedes" to "27F4D2", "red_bull" to "3671C6", "mclaren" to "FF8000",
            "aston_martin" to "229971", "alpine" to "0093CC", "williams" to "64C4FF", "rb" to "6692FF",
            "haas" to "B6BABD", "audi" to "F50537", "cadillac" to "9AA0A6",
        )
        private val SESSION_KEYS = listOf(
            "FirstPractice" to "Practice 1", "SecondPractice" to "Practice 2", "ThirdPractice" to "Practice 3",
            "SprintQualifying" to "Sprint Qualifying", "SprintShootout" to "Sprint Shootout",
            "Sprint" to "Sprint", "Qualifying" to "Qualifying",
        )

        private fun dt(date: String, time: String?): Instant =
            OffsetDateTime.parse(if (time != null) "${date}T$time" else "${date}T12:00:00Z").toInstant()

        fun parseRace(race: kotlinx.serialization.json.JsonObject, season: String, now: Instant): Fixture {
            val start = dt(race.str("date")!!, race.str("time"))
            val sessions = SESSION_KEYS.mapNotNull { (key, label) ->
                race.o(key)?.let { Session(label, dt(it.str("date")!!, it.str("time")).toString()) }
            }.sortedBy { it.date }
            val status = when {
                now.isAfter(start.plus(RACE_DURATION)) -> "final"
                !now.isBefore(start) -> "live"
                else -> "scheduled"
            }
            val circuit = race.o("Circuit")
            val loc = circuit.o("Location")
            val venue = listOfNotNull(circuit.str("circuitName"), loc.str("locality"), loc.str("country")).joinToString(", ")
            return Fixture(
                id = "f1-$season-${race.str("round")}", leagueId = F1_LEAGUE.id, leagueName = F1_LEAGUE.name,
                date = start.toString(), status = status, title = race.str("raceName") ?: "Grand Prix",
                venue = venue, sessions = sessions,
            )
        }
    }

    private suspend fun get(path: String, ttlMs: Long) = cache.getOrSet("f1:$path", ttlMs) {
        gate.getJson("$BASE/$path.json", mapOf("limit" to "100")).asObj().o("MRData").asObj()
    }

    suspend fun teams(): List<Team> =
        get("current/constructors", 24 * 60 * MINUTE).o("ConstructorTable").a("Constructors").objects().map {
            val id = it.str("constructorId")!!
            Team(id = id, name = it.str("name") ?: id, leagueId = F1_LEAGUE.id, color = COLORS[id])
        }

    suspend fun standings(): List<StandingGroup> {
        val cons = get("current/constructorStandings", 10 * MINUTE)
        val drivers = get("current/driverStandings", 10 * MINUTE)
        val constructorRows = cons.o("StandingsTable").a("StandingsLists").objects().firstOrNull()
            .a("ConstructorStandings").objects().map {
                val c = it.o("Constructor")
                StandingRow(
                    rank = it.str("position")!!.toInt(), points = it.dbl("points"), wins = it.str("wins")?.toIntOrNull(),
                    team = TeamRef(c.str("constructorId")!!, c.str("name") ?: "?"),
                )
            }
        val driverRows = drivers.o("StandingsTable").a("StandingsLists").objects().firstOrNull()
            .a("DriverStandings").objects().map {
                val d = it.o("Driver")
                StandingRow(
                    rank = it.str("position")!!.toInt(), points = it.dbl("points"), wins = it.str("wins")?.toIntOrNull(),
                    subtitle = it.a("Constructors").objects().firstOrNull().str("name"),
                    team = TeamRef(d.str("driverId")!!, "${d.str("givenName")} ${d.str("familyName")}", d.str("code")),
                )
            }
        return listOf(StandingGroup("Constructors", constructorRows), StandingGroup("Drivers", driverRows))
    }

    suspend fun fixtures(teamId: String? = null, now: Instant = Instant.now()): List<Fixture> {
        val races = get("current", 15 * MINUTE).o("RaceTable")
        val season = races.str("season") ?: ""
        val fixtures = races.a("Races").objects().map { parseRace(it, season, now) }
        if (teamId == null) return fixtures
        // attach how this constructor did in finished races
        val byRound = get("current/constructors/$teamId/results", 15 * MINUTE).o("RaceTable").a("Races").objects()
            .associateBy { it.str("round") }
        return fixtures.map { f ->
            val race = byRound[f.id.substringAfterLast('-')] ?: return@map f
            f.copy(summary = race.a("Results").objects().joinToString(" · ") {
                "P${it.str("position")} ${it.o("Driver").str("familyName")}"
            })
        }
    }
}
