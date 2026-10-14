package com.sportshub.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneOffset

/** National-team competitions, aggregated so following "India" shows friendlies, qualifiers and tournaments together. */
object National {
    val FOOTBALL = League("national:football", "national", "football", "National teams · Football")
    val CRICKET = League("national:cricket", "national", "cricket", "National teams · Cricket")

    fun isNational(leagueId: String) = leagueId == FOOTBALL.id || leagueId == CRICKET.id

    /** Cricinfo team ids on ESPN (verified against the ICC T20 World Cup table). */
    private val CRICKET_NATIONS = listOf(
        "6" to "India", "2" to "Australia", "1" to "England", "3" to "South Africa", "5" to "New Zealand",
        "7" to "Pakistan", "8" to "Sri Lanka", "4" to "West Indies", "25" to "Bangladesh", "40" to "Afghanistan",
        "9" to "Zimbabwe", "29" to "Ireland", "15" to "Netherlands", "30" to "Scotland", "11" to "United States of America",
        "33" to "Nepal", "27" to "United Arab Emirates", "37" to "Oman", "28" to "Namibia", "17" to "Canada", "31" to "Italy",
    )

    val cricketTeams: List<Team> = CRICKET_NATIONS.map { (id, name) ->
        Team(id, name, abbreviation = null, logo = "https://a.espncdn.com/i/teamlogos/cricket/500/$id.png", leagueId = CRICKET.id)
    }

    /** Qualifier slug per confederation: whichever team list contains a team tells us where it belongs. */
    val CONFEDERATIONS: Map<String, List<String>> = mapOf(
        "fifa.worldq.uefa" to listOf("uefa.nations", "uefa.euro", "uefa.euroq"),
        "fifa.worldq.afc" to listOf("afc.asian.cup", "afc.cupq"),
        "fifa.worldq.caf" to listOf("caf.nations", "caf.nations_qual"),
        "fifa.worldq.conmebol" to listOf("conmebol.america"),
        "fifa.worldq.concacaf" to listOf("concacaf.gold", "concacaf.gold_qual", "concacaf.nations.league"),
        "fifa.worldq.ofc" to emptyList(),
    )
    val ALWAYS = listOf("fifa.friendly", "fifa.world")

    private val NAMES = mapOf(
        "fifa.friendly" to "International Friendly", "fifa.world" to "FIFA World Cup", "uefa.nations" to "UEFA Nations League",
        "uefa.euro" to "UEFA European Championship", "uefa.euroq" to "UEFA Euro Qualifying", "afc.asian.cup" to "AFC Asian Cup",
        "afc.cupq" to "AFC Asian Cup Qualifiers", "caf.nations" to "Africa Cup of Nations", "caf.nations_qual" to "AFCON Qualifying",
        "conmebol.america" to "Copa America", "concacaf.gold" to "Concacaf Gold Cup", "concacaf.gold_qual" to "Gold Cup Qualifying",
        "concacaf.nations.league" to "Concacaf Nations League",
        "fifa.worldq.uefa" to "World Cup Qualifying - UEFA", "fifa.worldq.afc" to "World Cup Qualifying - AFC",
        "fifa.worldq.caf" to "World Cup Qualifying - CAF", "fifa.worldq.conmebol" to "World Cup Qualifying - CONMEBOL",
        "fifa.worldq.concacaf" to "World Cup Qualifying - Concacaf", "fifa.worldq.ofc" to "World Cup Qualifying - OFC",
    )

    fun footballLeague(slug: String) = League("soccer:$slug", "soccer", slug, NAMES[slug] ?: slug)

    /** Women's, youth and "A" sides share ESPN series; keep a men's senior team's page clean. */
    private val EXCLUDED_SERIES = Regex("women|under-?\\d|\\bu-?\\d{2}\\b|emerging|academy|\\bXI\\b|\\bA tour\\b", RegexOption.IGNORE_CASE)

    fun seriesMentions(text: String, teamName: String): Boolean =
        !EXCLUDED_SERIES.containsMatchIn(text) && Regex("\\b${Regex.escape(teamName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

class NationalProvider(private val espn: EspnProvider, private val cache: TtlCache, private val disk: DiskCache? = null) {
    private companion object {
        const val DAY = 24 * 60 * 60_000L
    }

    private suspend fun persisted(key: String, maxAgeMs: Long, compute: suspend () -> String): String =
        disk?.get(key, maxAgeMs) ?: compute().also { disk?.put(key, it) }

    suspend fun footballTeams(): List<Team> =
        espn.teams(National.footballLeague("fifa.friendly")).map { it.copy(leagueId = National.FOOTBALL.id) }

    private suspend fun confederationSlugs(teamId: String): List<String> = cache.getOrSet("nat:conf:$teamId", DAY) {
        persisted("conf:$teamId", 7 * DAY) { findConfederation(teamId).joinToString(",") }.split(',').filter { it.isNotEmpty() }
    }

    private suspend fun findConfederation(teamId: String): List<String> {
        for ((qualifier, extra) in National.CONFEDERATIONS) {
            val members = try {
                espn.teams(National.footballLeague(qualifier))
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpstreamBlocked) {
                throw e
            } catch (e: Exception) {
                continue // an inactive qualifier cycle can 404; try the next confederation
            }
            if (members.any { it.id == teamId }) return listOf(qualifier) + extra
        }
        return emptyList()
    }

    suspend fun footballFixtures(teamId: String): List<Fixture> {
        val slugs = National.ALWAYS + confederationSlugs(teamId)
        var failure: Exception? = null
        val all = mutableListOf<Fixture>()
        for (slug in slugs) {
            try {
                all += espn.teamSchedule(National.footballLeague(slug), teamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpstreamBlocked) {
                throw e
            } catch (e: Exception) {
                failure = e // team not in this competition: expected, skip it
            }
        }
        if (all.isEmpty() && failure != null && slugs.size == National.ALWAYS.size) throw failure
        return all.distinctBy { it.id }.sortedBy { it.date }
    }

    /** International tours: found via the header feed at sampled dates, then each series' full schedule. */
    suspend fun cricketFixtures(teamId: String): List<Fixture> {
        val team = National.cricketTeams.firstOrNull { it.id == teamId } ?: return emptyList()
        val today = LocalDate.now(ZoneOffset.UTC)
        val series = persisted("series:$teamId", 6 * 60 * 60_000L) {
            discoverSeries(team, today).joinToString("\n") { "${it.id}\t${it.name}" }
        }.lines().filter { it.isNotBlank() }.map { line -> line.split('\t', limit = 2).let { CricketSeries(it[0], it.getOrElse(1) { "" }, emptyList()) } }
        val fixtures = mutableListOf<Fixture>()
        for (s in series) {
            val league = League("cricket:${s.id}", "cricket", s.id, s.name)
            for (year in listOf(today.year, today.year + 1)) {
                try {
                    fixtures += espn.seasonEvents(league, year).filter { f -> f.home?.id == teamId || f.away?.id == teamId }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UpstreamBlocked) {
                    throw e
                } catch (e: Exception) {
                    continue
                }
            }
        }
        return fixtures.distinctBy { it.id }.sortedBy { it.date }
    }

    private suspend fun discoverSeries(team: Team, today: LocalDate): List<CricketSeries> {
        // Tours are short, so sample every 5 days for ~3 months; the header feed only lists series active on a date.
        val sampled = coroutineScope {
            (0L..90L step 5).map { offset ->
                async {
                    try {
                        espn.cricketSeries(today.plusDays(offset))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: UpstreamBlocked) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        val series = linkedMapOf<String, CricketSeries>()
        for (s in sampled.flatten()) {
            if (National.seriesMentions(s.name, team.name) || s.eventNames.any { National.seriesMentions(it, team.name) }) series.putIfAbsent(s.id, s)
        }
        return series.values.toList()
    }
}
