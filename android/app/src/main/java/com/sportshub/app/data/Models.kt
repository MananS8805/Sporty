package com.sportshub.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors backend/app/schemas.py.

@Serializable
data class League(
    val id: String,
    val sport: String,
    val slug: String,
    val name: String,
    val abbreviation: String? = null,
    val logo: String? = null,
)

@Serializable
data class TeamRef(
    val id: String,
    val name: String,
    val abbreviation: String? = null,
    val logo: String? = null,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val abbreviation: String? = null,
    val logo: String? = null,
    @SerialName("league_id") val leagueId: String,
    @SerialName("short_name") val shortName: String? = null,
    val color: String? = null,
)

@Serializable
data class Session(val name: String, val date: String)

@Serializable
data class Fixture(
    val id: String,
    @SerialName("league_id") val leagueId: String,
    @SerialName("league_name") val leagueName: String,
    val date: String,
    val status: String, // scheduled | live | final | postponed
    @SerialName("status_detail") val statusDetail: String? = null,
    val title: String,
    val home: TeamRef? = null,
    val away: TeamRef? = null,
    @SerialName("home_score") val homeScore: String? = null,
    @SerialName("away_score") val awayScore: String? = null,
    val venue: String? = null,
    val sessions: List<Session> = emptyList(),
    val summary: String? = null,
)

@Serializable
data class StandingRow(
    val rank: Int,
    val team: TeamRef,
    val subtitle: String? = null,
    val played: Int? = null,
    val wins: Int? = null,
    val draws: Int? = null,
    val losses: Int? = null,
    val points: Double? = null,
    @SerialName("goal_diff") val goalDiff: Double? = null,
    val extra: Map<String, Double> = emptyMap(),
)

@Serializable
data class StandingGroup(val name: String, val rows: List<StandingRow>)

@Serializable
data class NewsItem(
    val id: String,
    val headline: String,
    val description: String? = null,
    val published: String? = null,
    val url: String? = null,
    val image: String? = null,
    @SerialName("league_id") val leagueId: String? = null,
)

@Serializable
data class TeamOverview(
    val team: Team,
    val standing: StandingRow? = null,
    @SerialName("standing_group") val standingGroup: String? = null,
    val live: List<Fixture> = emptyList(),
    val upcoming: List<Fixture> = emptyList(),
    val recent: List<Fixture> = emptyList(),
    val news: List<NewsItem> = emptyList(),
)

@Serializable
data class Feed(
    val live: List<Fixture> = emptyList(),
    val upcoming: List<Fixture> = emptyList(),
    val recent: List<Fixture> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val errors: List<String> = emptyList(),
)

/** A team the user follows; stored on-device so the app needs no account. */
@Serializable
data class FollowedTeam(
    val leagueId: String,
    val leagueName: String,
    val teamId: String,
    val name: String,
    val logo: String? = null,
    val color: String? = null,
) {
    /** The `<sport>:<league>:<team>` key the backend's /feed endpoint expects. */
    val key: String get() = "$leagueId:$teamId"
}
