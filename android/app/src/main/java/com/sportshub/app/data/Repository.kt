package com.sportshub.app.data

class Repository(private val api: Api, val follows: FollowStore) {
    private var leagueCache: List<League>? = null

    suspend fun leagues(): List<League> = leagueCache ?: api.leagues().also { leagueCache = it }

    suspend fun league(id: String): League? = leagues().firstOrNull { it.id == id }

    suspend fun teams(leagueId: String) = api.teams(leagueId)

    suspend fun standings(leagueId: String) = api.standings(leagueId)

    suspend fun overview(leagueId: String, teamId: String) = api.overview(leagueId, teamId)

    suspend fun feed(followKeys: List<String>) = api.feed(followKeys)

    suspend fun follow(team: Team, leagueName: String) = follows.add(
        FollowedTeam(team.leagueId, leagueName, team.id, team.name, team.logo, team.color),
    )

    suspend fun unfollow(followKey: String) = follows.remove(followKey)
}
