package com.sportshub.app.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Pure functions turning ESPN's JSON into our models (unit-tested without a network). */
object EspnParse {
    fun teamRef(team: JsonObject?): TeamRef = TeamRef(
        id = team.str("id") ?: "?",
        name = team.str("displayName") ?: team.str("name") ?: team.str("shortDisplayName") ?: "?",
        abbreviation = team.str("abbreviation"),
        logo = team.str("logo") ?: team.a("logos").objects().firstOrNull().str("href"),
    )

    /** Scores arrive as a plain string in scoreboards but as {value, displayValue} in schedules. */
    private fun score(v: JsonElement?): String? {
        val p = (if (v is JsonObject) v["displayValue"] ?: v["value"] else v) as? JsonPrimitive ?: return null
        val s = p.content
        if (s.isEmpty() || s == "null") return null
        if (!p.isString) p.doubleOrNull?.let { if (it % 1.0 == 0.0) return it.toInt().toString() }
        return s
    }

    fun parseEvent(ev: JsonObject, league: League): Fixture? {
        val comp = ev.a("competitions").objects().firstOrNull()
        val competitors = comp.a("competitors").objects()
        var home = competitors.firstOrNull { it.str("homeAway") == "home" }
        var away = competitors.firstOrNull { it.str("homeAway") == "away" }
        if (home == null && away == null && competitors.size == 2) { // cricket / neutral venue
            home = competitors[0]
            away = competitors[1]
        }
        val date = parseInstant(ev.str("date") ?: comp.str("date")) ?: return null

        val statusType = (comp.o("status") ?: ev.o("status")).o("type")
        var state = when (statusType.str("state")) {
            "in" -> "live"
            "post" -> "final"
            else -> "scheduled"
        }
        if (statusType.str("name") in setOf("STATUS_POSTPONED", "STATUS_CANCELED", "STATUS_SUSPENDED")) state = "postponed"

        return Fixture(
            id = ev.str("id") ?: return null,
            leagueId = league.id,
            leagueName = league.name,
            date = date.toString(),
            status = state,
            statusDetail = statusType.str("shortDetail") ?: statusType.str("description"),
            title = ev.str("shortName") ?: ev.str("name") ?: "",
            home = home?.o("team")?.let { teamRef(it) },
            away = away?.o("team")?.let { teamRef(it) },
            homeScore = score(home?.get("score")),
            awayScore = score(away?.get("score")),
            venue = (comp.o("venue") ?: ev.o("venue")).str("fullName"),
        )
    }

    /** Standings nest differently per sport (league -> conference -> division). */
    private fun collectStandingNodes(node: JsonObject, out: MutableList<JsonObject>) {
        if (node.o("standings").a("entries").isNotEmpty()) out += node
        node.a("children").objects().forEach { collectStandingNodes(it, out) }
    }

    private fun statMap(entry: JsonObject): MutableMap<String, Double> {
        val stats = mutableMapOf<String, Double>()
        for (s in entry.a("stats").objects()) {
            val name = s.str("name") ?: continue
            val value = s.dbl("value") ?: s.str("displayValue")?.replace("+", "")?.toDoubleOrNull() ?: continue
            stats[name] = value
        }
        return stats
    }

    private val KNOWN_STATS = setOf("rank", "gamesPlayed", "wins", "ties", "draws", "losses", "points", "pointDifferential")

    fun parseStandings(data: JsonObject): List<StandingGroup> {
        val nodes = mutableListOf<JsonObject>()
        collectStandingNodes(data, nodes)
        return nodes.map { node ->
            val rows = node.o("standings").a("entries").objects().mapIndexed { idx, entry ->
                val st = statMap(entry)
                // Cricket names its stats differently; fold them onto the common ones.
                for ((cricket, common) in CRICKET_STATS) {
                    if (cricket in st && common !in st) st[common] = st.remove(cricket)!!
                }
                st.remove("netrr")?.let { st["netRunRate"] = it }
                StandingRow(
                    rank = st["rank"]?.toInt() ?: (idx + 1),
                    team = teamRef(entry.o("team")),
                    played = st["gamesPlayed"]?.toInt(),
                    wins = st["wins"]?.toInt(),
                    draws = (st["ties"] ?: st["draws"])?.toInt(),
                    losses = st["losses"]?.toInt(),
                    points = st["points"],
                    goalDiff = st["pointDifferential"],
                    extra = st.filterKeys { it !in KNOWN_STATS },
                )
            }.sortedBy { it.rank }
            StandingGroup(node.str("name") ?: node.str("abbreviation") ?: "Standings", rows)
        }
    }

    private val CRICKET_STATS = listOf(
        "matchesPlayed" to "gamesPlayed", "matchesWon" to "wins", "matchesLost" to "losses",
        "matchPoints" to "points", "noresult" to "ties",
    )

    fun parseNews(data: JsonObject, leagueId: String): List<NewsItem> = data.a("articles").objects().map { a ->
        NewsItem(
            id = a.str("id") ?: a.str("dataSourceIdentifier") ?: a.str("headline") ?: "",
            headline = a.str("headline") ?: "",
            description = a.str("description"),
            published = parseInstant(a.str("published"))?.toString(),
            url = a.o("links").o("web").str("href"),
            image = a.a("images").objects().firstOrNull().str("url"),
            leagueId = leagueId,
        )
    }
}

/** ESPN's public (unofficial) API: one URL shape serves soccer worldwide, IPL and the US sports. */
class EspnProvider(private val gate: HttpGate, private val cache: TtlCache) {
    private companion object {
        const val SITE = "https://site.api.espn.com/apis/site/v2/sports"
        const val SITE_V2 = "https://site.api.espn.com/apis/v2/sports"
        val DATES: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        const val MINUTE = 60_000L
    }

    suspend fun standings(league: League): List<StandingGroup> = cache.getOrSet("st:${league.id}", 10 * MINUTE) {
        EspnParse.parseStandings(gate.getJson("$SITE_V2/${league.sport}/${league.slug}/standings").asObj())
    }

    suspend fun teams(league: League): List<Team> = cache.getOrSet("teams:${league.id}", 24 * 60 * MINUTE) {
        try {
            val raw = gate.getJson("$SITE/${league.sport}/${league.slug}/teams").asObj()
                .a("sports").objects().first().a("leagues").objects().first().a("teams").objects()
            raw.map { it.o("team") }.map { t ->
                val ref = EspnParse.teamRef(t)
                Team(ref.id, ref.name, ref.abbreviation, ref.logo, league.id, t.str("shortDisplayName"), t.str("color"))
            }.also { check(it.isNotEmpty()) }
        } catch (e: UpstreamBlocked) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. cricket has no /teams endpoint; the standings table lists every team
            standings(league).flatMap { it.rows }.map {
                Team(it.team.id, it.team.name, it.team.abbreviation, it.team.logo, league.id)
            }
        }
    }

    suspend fun scoreboard(league: League, daysBack: Long = 0, daysAhead: Long = 7): List<Fixture> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val range = "${today.minusDays(daysBack).format(DATES)}-${today.plusDays(daysAhead).format(DATES)}"
        return cache.getOrSet("sb:${league.id}:$range", MINUTE) {
            val data = gate.getJson(
                "$SITE/${league.sport}/${league.slug}/scoreboard", mapOf("dates" to range, "limit" to "300"),
            ).asObj()
            data.a("events").objects().mapNotNull { EspnParse.parseEvent(it, league) }.sortedBy { it.date }
        }
    }

    /** ESPN returns only played games by default; fixture=true returns the upcoming ones. */
    suspend fun teamSchedule(league: League, teamId: String): List<Fixture> =
        cache.getOrSet("sched:${league.id}:$teamId", 5 * MINUTE) {
            val url = "$SITE/${league.sport}/${league.slug}/teams/$teamId/schedule"
            val played = gate.getJson(url).asObj()
            val upcoming = try {
                gate.getJson(url, mapOf("fixture" to "true")).asObj()
            } catch (e: UpstreamBlocked) {
                throw e
            } catch (e: java.io.IOException) {
                JsonObject(emptyMap()) // still show results if the upcoming list fails
            }
            val events = LinkedHashMap<String?, JsonObject>()
            (played.a("events").objects() + upcoming.a("events").objects()).forEach { events[it.str("id")] = it }
            events.values.mapNotNull { EspnParse.parseEvent(it, league) }.sortedBy { it.date }
        }

    suspend fun news(league: League, teamId: String? = null, limit: Int = 15): List<NewsItem> =
        cache.getOrSet("news:${league.id}:$teamId:$limit", 10 * MINUTE) {
            val params = buildMap { put("limit", "$limit"); teamId?.let { put("team", it) } }
            EspnParse.parseNews(gate.getJson("$SITE/${league.sport}/${league.slug}/news", params).asObj(), league.id)
        }
}
