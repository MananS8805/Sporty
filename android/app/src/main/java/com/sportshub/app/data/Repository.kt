package com.sportshub.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class NotFoundException(message: String) : Exception(message)

/** `soccer:eng.1:360` -> (`soccer:eng.1`, `360`) */
fun parseFollow(key: String): Pair<String, String> {
    val league = key.substringBeforeLast(':', "")
    val team = key.substringAfterLast(':', "")
    require(':' in league && team.isNotEmpty()) { "invalid follow key: '$key' (expected '<sport>:<league>:<team>')" }
    return league to team
}

fun splitFixtures(fixtures: List<Fixture>, upcomingN: Int = 5, recentN: Int = 5): Triple<List<Fixture>, List<Fixture>, List<Fixture>> =
    Triple(
        fixtures.filter { it.status == "live" },
        fixtures.filter { it.status == "scheduled" }.sortedBy { it.date }.take(upcomingN),
        fixtures.filter { it.status == "final" }.sortedByDescending { it.date }.take(recentN),
    )

/** Combines per-team overviews into one feed, de-duplicating games between two followed teams. */
fun mergeFeed(results: List<Pair<String, Result<TeamOverview>>>): Feed {
    val live = LinkedHashMap<String, Fixture>()
    val upcoming = LinkedHashMap<String, Fixture>()
    val recent = LinkedHashMap<String, Fixture>()
    val news = LinkedHashMap<String, NewsItem>()
    val errors = mutableListOf<String>()
    for ((key, result) in results) {
        val overview = result.getOrNull()
        if (overview == null) {
            errors += "$key: ${result.exceptionOrNull()?.message}"
            continue
        }
        overview.live.forEach { live[it.id] = it }
        overview.upcoming.forEach { upcoming[it.id] = it }
        overview.recent.forEach { recent[it.id] = it }
        overview.news.forEach { news[it.id] = it }
    }
    return Feed(
        live = live.values.sortedBy { it.date },
        upcoming = upcoming.values.sortedBy { it.date }.take(30),
        recent = recent.values.sortedByDescending { it.date }.take(20),
        news = news.values.sortedByDescending { it.published ?: "" }.take(40),
        errors = errors,
    )
}

private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Region used for where-to-watch: a user override if set, otherwise the phone's region. */
object Region {
    private const val PREFS = "settings"
    private const val KEY = "region_override"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: context.resources.configuration.locales[0].country.ifEmpty { "US" }

    fun override(context: Context, code: String?) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (code == null) remove(KEY) else putString(KEY, code) }.apply()

    fun countryName(code: String): String = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifEmpty { code }
}

// F1 news comes from ESPN's racing section; national cricket news from the cricket section.
private val F1_NEWS = League(id = "racing:f1", sport = "racing", slug = "f1", name = "Formula 1")
private val CRICKET_NEWS = League(id = "cricket:8048", sport = "cricket", slug = "8048", name = "Cricket")

// Pinned first in the league list so the big competitions are not buried under e.g. AFC qualifiers.
private val POPULAR = listOf(
    "soccer:eng.1", "cricket:8048", "f1:f1", "national:football", "national:cricket", "soccer:esp.1", "soccer:ita.1",
    "soccer:ger.1", "soccer:fra.1", "soccer:uefa.champions", "soccer:uefa.europa", "soccer:eng.2", "soccer:usa.1",
    "soccer:ned.1", "soccer:por.1", "soccer:sco.1", "soccer:bra.1", "soccer:arg.1", "soccer:mex.1", "soccer:tur.1",
    "cricket:8044", "basketball:nba", "football:nfl", "baseball:mlb", "hockey:nhl",
)
private val SPORT_ORDER = listOf("f1", "soccer", "national", "cricket", "basketball", "football", "baseball", "hockey", "rugby")
private val POSITION_ORDER = listOf("goalkeeper", "defender", "midfielder", "forward", "attacker")

fun sortPlayers(players: List<Player>): List<Player> = players.sortedWith(
    compareBy(
        { p -> POSITION_ORDER.indexOfFirst { p.position?.lowercase()?.contains(it) == true }.let { if (it < 0) 99 else it } },
        { it.jersey?.toIntOrNull() ?: 999 },
        { it.name },
    ),
)

/** Everything the UI needs. Routes each league to the right data source, all from the phone. */
class Repository(
    private val context: Context,
    val follows: FollowStore,
    gate: HttpGate = HttpGate(),
    disk: DiskCache = DiskCache(context),
) {
    private val cache = TtlCache()
    private val espn = EspnProvider(gate, cache)
    private val f1 = F1Provider(gate, cache)
    private val tsdb = TsdbProvider(gate, cache)
    private val national = NationalProvider(espn, cache, disk)

    // The league list ships inside the app (assets/leagues.json), so no discovery requests are needed.
    private val leagueList: List<League> by lazy {
        val bundled = context.assets.open("leagues.json").bufferedReader().use { it.readText() }
        (listOf(F1_LEAGUE, National.FOOTBALL, National.CRICKET) + Json { ignoreUnknownKeys = true }.decodeFromString<List<League>>(bundled))
            .sortedWith(
                compareBy(
                    { POPULAR.indexOf(it.id).let { i -> if (i < 0) 999 else i } },
                    { SPORT_ORDER.indexOf(it.sport).let { i -> if (i < 0) 99 else i } },
                    { it.name },
                ),
            )
    }

    suspend fun leagues(): List<League> = withContext(Dispatchers.IO) { leagueList }

    suspend fun league(id: String): League? = leagues().firstOrNull { it.id == id }

    private suspend fun requireLeague(id: String) = resolveLeague(id) ?: throw NotFoundException("Unknown league $id")

    /** Fixtures can belong to leagues that are not in the bundled list (e.g. a cricket tour discovered at runtime). */
    private suspend fun resolveLeague(id: String): League? = league(id) ?: when {
        id.startsWith("cricket:") -> League(id, "cricket", id.substringAfter(':'), "Cricket")
        id.startsWith("soccer:") -> League(id, "soccer", id.substringAfter(':'), id.substringAfter(':'))
        else -> null
    }

    suspend fun teams(leagueId: String): List<Team> = teams(requireLeague(leagueId))

    private suspend fun teams(league: League): List<Team> = when {
        league.id == National.FOOTBALL.id -> national.footballTeams()
        league.id == National.CRICKET.id -> National.cricketTeams
        league.sport == "f1" -> f1.teams()
        else -> espn.teams(league)
    }

    suspend fun standings(leagueId: String): List<StandingGroup> {
        val league = requireLeague(leagueId)
        return when {
            National.isNational(league.id) -> emptyList()
            league.sport == "f1" -> f1.standings()
            else -> espn.standings(league)
        }
    }

    private suspend fun cricketFixtures(league: League, teamId: String?): List<Fixture> {
        val year = LocalDate.now(ZoneOffset.UTC).year
        val all = listOf(year, year + 1).flatMap { y -> attempt { espn.seasonEvents(league, y) }.getOrDefault(emptyList()) }
        val fromEspn = if (teamId == null) all else all.filter { teamId == it.home?.id || teamId == it.away?.id }
        if (fromEspn.isNotEmpty() || !tsdb.supports(league.id)) return fromEspn
        return tsdb.fixtures(league, teams(league)).let { f -> if (teamId == null) f else f.filter { teamId == it.home?.id || teamId == it.away?.id } }
    }

    private suspend fun fixtures(league: League, teamId: String?): List<Fixture> = when {
        league.id == National.FOOTBALL.id -> teamId?.let { national.footballFixtures(it) }.orEmpty()
        league.id == National.CRICKET.id -> teamId?.let { national.cricketFixtures(it) }.orEmpty()
        league.sport == "f1" -> f1.fixtures(teamId)
        league.sport == "cricket" -> cricketFixtures(league, teamId)
        teamId != null -> espn.teamSchedule(league, teamId)
        else -> espn.scoreboard(league)
    }

    /** A team's full fixture list (used by background checks as well as the UI). */
    suspend fun fixtures(leagueId: String, teamId: String): List<Fixture> = fixtures(requireLeague(leagueId), teamId)

    private suspend fun news(league: League, teamId: String?, limit: Int): List<NewsItem> {
        when {
            league.id == National.FOOTBALL.id ->
                return espn.news(National.footballLeague("fifa.friendly"), teamId, limit).map { it.copy(leagueId = league.id) }
            league.id == National.CRICKET.id -> {
                val items = espn.news(CRICKET_NEWS, limit = 40).map { it.copy(leagueId = league.id) }
                val name = National.cricketTeams.firstOrNull { it.id == teamId }?.name ?: return items.take(limit)
                val hits = items.filter { National.seriesMentions("${it.headline} ${it.description}", name) }
                return hits.ifEmpty { items }.take(limit)
            }
            league.sport != "f1" -> return espn.news(league, teamId, limit)
        }
        val items = espn.news(F1_NEWS, limit = 40).map { it.copy(leagueId = league.id) }
        if (teamId == null) return items.take(limit)
        // ESPN has no per-constructor feed, so filter by team name mentions
        val name = f1.teams().firstOrNull { it.id == teamId }?.name?.lowercase() ?: return items.take(limit)
        val hits = items.filter { name in "${it.headline} ${it.description}".lowercase() }
        return (hits.ifEmpty { items }).take(limit)
    }

    suspend fun overview(leagueId: String, teamId: String): TeamOverview = coroutineScope {
        val league = requireLeague(leagueId)
        val team = teams(league).firstOrNull { it.id == teamId } ?: throw NotFoundException("Unknown team $teamId in $leagueId")

        // One failing source should not blank the whole screen, so each is fetched independently.
        val fixtures = async { attempt { fixtures(league, teamId) } }
        val standings = async { attempt { standings(leagueId) } }
        val news = async { attempt { news(league, teamId, 8) } }
        val (fx, st, nw) = Triple(fixtures.await(), standings.await(), news.await())
        if (fx.isFailure && st.isFailure && nw.isFailure) throw fx.exceptionOrNull()!!

        var overview = TeamOverview(team = team)
        fx.getOrNull()?.let { all ->
            val (live, upcoming, recent) = splitFixtures(all)
            overview = overview.copy(live = live, upcoming = upcoming, recent = recent)
        }
        st.getOrNull()?.forEach { group ->
            if (overview.standing == null) group.rows.firstOrNull { it.team.id == teamId }?.let {
                overview = overview.copy(standing = it, standingGroup = group.name)
            }
        }
        nw.getOrNull()?.let { overview = overview.copy(news = it) }
        overview
    }

    suspend fun feed(followKeys: List<String>): Feed = coroutineScope {
        val results = followKeys.map { key ->
            async {
                val (leagueId, teamId) = parseFollow(key)
                key to attempt { overview(leagueId, teamId) }
            }
        }.awaitAll()
        // If every team failed (offline, blocked), surface an error rather than an empty feed.
        if (results.isNotEmpty() && results.all { it.second.isFailure }) throw results.first().second.exceptionOrNull()!!
        mergeFeed(results)
    }

    // ---- squads -----------------------------------------------------------------------------------

    suspend fun squad(leagueId: String, teamId: String): Squad {
        val league = requireLeague(leagueId)
        return when {
            league.sport == "f1" -> Squad(f1.drivers(teamId))
            league.id == National.FOOTBALL.id -> Squad(sortPlayers(nationalFootballRoster(teamId)))
            league.sport == "cricket" || league.id == National.CRICKET.id -> cricketSquad(league, teamId)
            else -> Squad(sortPlayers(espn.roster(league, teamId)))
        }
    }

    private suspend fun nationalFootballRoster(teamId: String): List<Player> {
        for (slug in listOf("fifa.friendly", "fifa.world")) {
            val players = attempt { espn.roster(National.footballLeague(slug), teamId) }.getOrNull().orEmpty()
            if (players.isNotEmpty()) return players
        }
        return emptyList()
    }

    /** ESPN publishes cricket squads per match, so look at the team's next and latest matches until one has them. */
    private suspend fun cricketSquad(league: League, teamId: String): Squad {
        val (_, upcoming, recent) = splitFixtures(fixtures(league, teamId), upcomingN = 2, recentN = 2)
        var fallback: Squad? = null
        for (fixture in upcoming + recent) {
            if (fixture.id.startsWith("tsdb-")) continue
            val eventLeague = resolveLeague(fixture.leagueId) ?: continue
            val summary = attempt { espn.summary(eventLeague, fixture.id) }.getOrNull() ?: continue
            val squad = EspnParse.parseCricketSquad(summary, teamId) ?: continue
            if (squad.note == null) return squad.copy(players = sortCricket(squad.players))
            if (fallback == null) fallback = squad
        }
        return fallback?.let { it.copy(players = sortCricket(it.players)) } ?: Squad(emptyList(), "No squad has been published for this team yet.")
    }

    private fun sortCricket(players: List<Player>) = players.sortedWith(compareByDescending<Player> { it.captain }.thenBy { it.name })

    // ---- match insight: expert forecast + where to watch -------------------------------------------

    suspend fun insight(fixture: Fixture): MatchInsight {
        val league = resolveLeague(fixture.leagueId)
        val region = Region.current(context)
        val country = Region.countryName(region)
        val usable = league != null && league.sport != "f1" && !fixture.id.startsWith("tsdb-")
        val summary: JsonObject? = if (usable) attempt { espn.summary(league!!, fixture.id) }.getOrNull() else null

        val prediction = when {
            league?.sport == "f1" -> f1Context()
            summary == null -> Predictions.unavailable("The forecast couldn't be loaded right now.")
            else -> {
                val ranks = tableRanks(league!!, fixture)
                Predictions.fromSummary(summary, fixture, ranks).let {
                    if (it.available) it else it.copy(unavailableReason = noForecastReason(league.sport))
                }
            }
        }
        val broadcasts = summary?.a("broadcasts")?.objects().orEmpty().mapNotNull { b ->
            val r = b.str("region") ?: return@mapNotNull null
            val name = b.o("media").str("name") ?: return@mapNotNull null
            r to name
        }
        return MatchInsight(prediction, WatchRights.forRegion(region, fixture, broadcasts, country))
    }

    private fun noForecastReason(sport: String) = when (sport) {
        "cricket" -> "No free expert forecast exists for cricket. ESPN doesn't publish odds for this match."
        else -> "No bookmaker odds are published for this match yet."
    }

    private suspend fun tableRanks(league: League, fixture: Fixture): Map<String, Int> {
        val groups = attempt { standings(league.id) }.getOrNull().orEmpty()
        val wanted = listOfNotNull(fixture.home?.id, fixture.away?.id).toSet()
        return groups.flatMap { it.rows }.filter { it.team.id in wanted }.associate { it.team.id to it.rank }
    }

    private suspend fun f1Context(): Prediction {
        val top = attempt { f1.standings() }.getOrNull()?.firstOrNull()?.rows?.take(5).orEmpty()
        val factors = if (top.isEmpty()) emptyList() else listOf(
            Factor(
                "Constructors' championship", top.joinToString(" · ") { "${it.team.name} ${it.points?.toInt()}" },
                "Points after the races so far; the closest thing to a form guide.",
            ),
        )
        return Predictions.unavailable("No expert forecast is published for Formula 1 races. Here's the form that matters.", factors)
    }

    suspend fun follow(team: Team, leagueName: String) = follows.add(
        FollowedTeam(team.leagueId, leagueName, team.id, team.name, team.logo, team.color),
    )

    suspend fun unfollow(followKey: String) = follows.remove(followKey)
}
