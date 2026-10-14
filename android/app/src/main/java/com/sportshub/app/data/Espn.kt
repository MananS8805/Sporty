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

    private fun player(a: JsonObject, captain: Boolean = false, keeper: Boolean = false): Player? {
        val name = a.str("displayName") ?: a.str("fullName") ?: a.str("name") ?: return null
        val pos = a.o("position")
        return Player(
            id = a.str("id") ?: name,
            name = name,
            position = pos.str("displayName") ?: pos.str("name") ?: pos.str("abbreviation"),
            jersey = a.str("jersey"),
            age = a.dbl("age")?.toInt(),
            country = a.str("citizenship"),
            headshot = a.o("headshot").str("href"),
            captain = captain || a.str("captain") == "true",
            keeper = keeper || a.str("keeper") == "true",
        )
    }

    /** Football-style roster: a flat athletes list (US sports nest them in position groups under `items`). */
    fun parseRoster(data: JsonObject): List<Player> =
        data.a("athletes").objects().flatMap { g -> if (g.a("items").isNotEmpty()) g.a("items").objects() else listOf(g) }
            .mapNotNull { player(it) }

    /** Cricket squad from a match summary: the full `squads` list if present, otherwise the playing XI. */
    fun parseCricketSquad(summary: JsonObject, teamId: String): Squad? {
        summary.a("squads").objects().firstOrNull { it.o("team").str("id") == teamId }?.let { sq ->
            val players = sq.a("athletes").objects().mapNotNull { player(it) }
            if (players.isNotEmpty()) return Squad(players)
        }
        summary.a("rosters").objects().firstOrNull { it.o("team").str("id") == teamId }?.let { r ->
            val players = r.a("roster").objects().mapNotNull { e ->
                e.o("athlete")?.let { player(it, captain = e.str("captain") == "true", keeper = e.str("keeper") == "true") }
            }
            if (players.isNotEmpty()) return Squad(players, "Playing XI from the latest match. The full squad list isn't published for this competition.")
        }
        return null
    }

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

    /** Today's slate. ESPN rejects date ranges for soccer (HTTP 400), so there is no window parameter. */
    suspend fun scoreboard(league: League): List<Fixture> = cache.getOrSet("sb:${league.id}", MINUTE) {
        val data = gate.getJson("$SITE/${league.sport}/${league.slug}/scoreboard", mapOf("limit" to "300")).asObj()
        data.a("events").objects().mapNotNull { EspnParse.parseEvent(it, league) }.sortedBy { it.date }
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

    suspend fun summary(league: League, eventId: String): JsonObject = cache.getOrSet("sum:${league.id}:$eventId", 10 * MINUTE) {
        gate.getJson("$SITE/${league.sport}/${league.slug}/summary", mapOf("event" to eventId)).asObj()
    }

    suspend fun roster(league: League, teamId: String): List<Player> = cache.getOrSet("roster:${league.id}:$teamId", 6 * 60 * MINUTE) {
        EspnParse.parseRoster(gate.getJson("$SITE/${league.sport}/${league.slug}/teams/$teamId/roster").asObj())
    }

    /** Cricket scoreboards return a whole season when given `dates=<year>` (ranges are rejected). */
    suspend fun seasonEvents(league: League, year: Int): List<Fixture> = cache.getOrSet("season:${league.id}:$year", 15 * MINUTE) {
        gate.getJson("$SITE/${league.sport}/${league.slug}/scoreboard", mapOf("dates" to "$year")).asObj()
            .a("events").objects().mapNotNull { EspnParse.parseEvent(it, league) }.sortedBy { it.date }
    }

    /** Active cricket series on a date, found through ESPN's scoreboard header feed (used for international tours). */
    suspend fun cricketSeries(date: LocalDate): List<CricketSeries> = cache.getOrSet("series:$date", 3 * 60 * MINUTE) {
        val params = mapOf("sport" to "cricket", "region" to "in", "lang" to "en", "dates" to date.format(DATES))
        gate.getJson("https://site.web.api.espn.com/apis/personalized/v2/scoreboard/header", params).asObj()
            .a("sports").objects().firstOrNull().a("leagues").objects().mapNotNull { l ->
                CricketSeries(l.str("id") ?: return@mapNotNull null, l.str("name") ?: "", l.a("events").objects().mapNotNull { it.str("name") })
            }
    }
}

data class CricketSeries(val id: String, val name: String, val eventNames: List<String>)
