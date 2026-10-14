package com.sportshub.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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

// F1 news comes from ESPN's racing section.
private val F1_NEWS = League(id = "racing:f1", sport = "racing", slug = "f1", name = "Formula 1")
// Pinned first in the league list so the big competitions are not buried under e.g. AFC qualifiers.
private val POPULAR = listOf(
    "soccer:eng.1", "cricket:8048", "f1:f1", "soccer:esp.1", "soccer:ita.1", "soccer:ger.1", "soccer:fra.1",
    "soccer:uefa.champions", "soccer:uefa.europa", "soccer:eng.2", "soccer:usa.1", "soccer:ned.1", "soccer:por.1",
    "soccer:sco.1", "soccer:bra.1", "soccer:arg.1", "soccer:mex.1", "soccer:tur.1", "cricket:8044",
    "basketball:nba", "football:nfl", "baseball:mlb", "hockey:nhl",
)
private val SPORT_ORDER = listOf("f1", "soccer", "cricket", "basketball", "football", "baseball", "hockey", "rugby")

/** Everything the UI needs. Routes each league to the right data source, all from the phone. */
class Repository(private val context: Context, val follows: FollowStore, gate: HttpGate = HttpGate()) {
    private val cache = TtlCache()
    private val espn = EspnProvider(gate, cache)
    private val f1 = F1Provider(gate, cache)
    private val tsdb = TsdbProvider(gate, cache)

    // The league list ships inside the app (assets/leagues.json), so no discovery requests are needed.
    private val leagueList: List<League> by lazy {
        val bundled = context.assets.open("leagues.json").bufferedReader().use { it.readText() }
        (listOf(F1_LEAGUE) + Json { ignoreUnknownKeys = true }.decodeFromString<List<League>>(bundled))
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

    private suspend fun requireLeague(id: String) = league(id) ?: throw NotFoundException("Unknown league $id")

    suspend fun teams(leagueId: String): List<Team> = teams(requireLeague(leagueId))

    private suspend fun teams(league: League) = if (league.sport == "f1") f1.teams() else espn.teams(league)

    suspend fun standings(leagueId: String): List<StandingGroup> = requireLeague(leagueId).let {
        if (it.sport == "f1") f1.standings() else espn.standings(it)
    }

    private suspend fun fixtures(league: League, teamId: String?): List<Fixture> = when {
        league.sport == "f1" -> f1.fixtures(teamId)
        tsdb.supports(league.id) -> tsdb.fixtures(league, teams(league)).let { all ->
            if (teamId == null) all else all.filter { teamId == it.home?.id || teamId == it.away?.id }
        }
        teamId != null -> espn.teamSchedule(league, teamId)
        else -> espn.scoreboard(league, daysBack = 3, daysAhead = 14)
    }

    private suspend fun news(league: League, teamId: String?, limit: Int): List<NewsItem> {
        if (league.sport != "f1") return espn.news(league, teamId, limit)
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

    suspend fun follow(team: Team, leagueName: String) = follows.add(
        FollowedTeam(team.leagueId, leagueName, team.id, team.name, team.logo, team.color),
    )

    suspend fun unfollow(followKey: String) = follows.remove(followKey)
}
