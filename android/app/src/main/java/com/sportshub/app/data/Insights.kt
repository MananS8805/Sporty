package com.sportshub.app.data

import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.util.Locale

/** A player, driver or squad member. [detail] holds a sport-specific extra such as "125 pts". */
data class Player(
    val id: String,
    val name: String,
    val position: String? = null,
    val jersey: String? = null,
    val age: Int? = null,
    val country: String? = null,
    val headshot: String? = null,
    val captain: Boolean = false,
    val keeper: Boolean = false,
    val detail: String? = null,
)

data class Squad(val players: List<Player>, val note: String? = null)

/** One input the prediction is based on, shown to the user so the number is never a black box. */
data class Factor(val label: String, val value: String, val detail: String? = null)

/**
 * A forecast for one match. Probabilities come from an external expert source (never trained on
 * the phone). When no source exists, [home]/[away] are null and [unavailableReason] says why.
 */
data class Prediction(
    val home: Double? = null,
    val draw: Double? = null,
    val away: Double? = null,
    val source: String = "",
    val factors: List<Factor> = emptyList(),
    val unavailableReason: String? = null,
) {
    val available get() = home != null && away != null
}

data class WatchOption(val provider: String, val kind: String, val note: String? = null, val confirmed: Boolean = true)

data class WatchInfo(
    val region: String,
    val options: List<WatchOption>,
    val searchUrl: String,
    /** Date the curated rights table was last checked against news sources; null if options came from an API. */
    val verifiedOn: String? = null,
)

data class MatchInsight(val prediction: Prediction, val watch: WatchInfo)

/** Betting-market maths: turns bookmaker odds into probabilities with the bookmaker's margin removed. */
object Odds {
    fun americanToProb(moneyLine: Double): Double =
        if (moneyLine > 0) 100.0 / (moneyLine + 100.0) else -moneyLine / (-moneyLine + 100.0)

    fun formatAmerican(moneyLine: Double): String = (if (moneyLine > 0) "+" else "") + moneyLine.toInt()

    /** Scales raw implied probabilities so they sum to 1. Returns the normalised values and the margin (overround). */
    fun normalise(raw: List<Double>): Pair<List<Double>, Double> {
        val total = raw.sum()
        return raw.map { it / total } to (total - 1.0)
    }
}

object Predictions {
    private const val NO_SOURCE = "No expert forecast is published for this competition."

    /** ESPN match summaries carry bookmaker odds for most football and US-sport fixtures. */
    fun fromSummary(summary: JsonObject, fixture: Fixture, tableRanks: Map<String, Int> = emptyMap()): Prediction {
        val pick = summary.a("pickcenter").objects().firstOrNull { it.o("homeTeamOdds").dbl("moneyLine") != null || it.o("awayTeamOdds").dbl("moneyLine") != null }
            ?: summary.a("odds").objects().firstOrNull { it.o("homeTeamOdds").dbl("moneyLine") != null }
            ?: return Prediction(unavailableReason = NO_SOURCE, factors = contextFactors(summary, fixture, tableRanks))

        val homeMl = pick.o("homeTeamOdds").dbl("moneyLine")
        val awayMl = pick.o("awayTeamOdds").dbl("moneyLine")
        val drawMl = pick.o("drawOdds").dbl("moneyLine")
        if (homeMl == null || awayMl == null) {
            return Prediction(unavailableReason = "Odds for this match are incomplete.", factors = contextFactors(summary, fixture, tableRanks))
        }
        val raw = listOfNotNull(Odds.americanToProb(homeMl), drawMl?.let { Odds.americanToProb(it) }, Odds.americanToProb(awayMl))
        val (norm, margin) = Odds.normalise(raw)
        val hasDraw = drawMl != null

        val provider = pick.o("provider").str("name") ?: "bookmaker"
        val factors = mutableListOf<Factor>()
        val oddsText = buildList {
            add("${fixture.home?.name ?: "Home"} ${Odds.formatAmerican(homeMl)}")
            if (drawMl != null) add("Draw ${Odds.formatAmerican(drawMl)}")
            add("${fixture.away?.name ?: "Away"} ${Odds.formatAmerican(awayMl)}")
        }.joinToString(" · ")
        factors += Factor("Bookmaker odds", oddsText, "Moneylines from $provider, the market's view of each outcome.")
        factors += Factor(
            "Margin removed", String.format(Locale.US, "%.1f%%", margin * 100),
            "Bookmakers build in a profit margin, so raw odds add up to more than 100%. We rescale them.",
        )
        pick.dbl("overUnder")?.let { line ->
            val over = pick.dbl("overOdds")?.let { " (over ${Odds.formatAmerican(it)})" }.orEmpty()
            factors += Factor("Total goals line", "$line$over", "The market expects about this many goals or points in total.")
        }
        pick.dbl("spread")?.let { factors += Factor("Handicap", it.toString(), "Goal handicap the market gives the home side.") }
        factors += contextFactors(summary, fixture, tableRanks)

        return Prediction(
            home = norm[0], draw = if (hasDraw) norm[1] else null, away = norm.last(),
            source = "$provider odds via ESPN", factors = factors,
        )
    }

    /** Context the expert numbers sit alongside: recent form, head-to-head and league position. */
    fun contextFactors(summary: JsonObject, fixture: Fixture, tableRanks: Map<String, Int> = emptyMap()): List<Factor> {
        val factors = mutableListOf<Factor>()
        val form = summary.a("lastFiveGames").objects().mapNotNull { team ->
            val name = team.o("team").str("displayName") ?: return@mapNotNull null
            val results = team.a("events").objects().mapNotNull { it.str("gameResult") }
            if (results.isEmpty()) null else "$name ${results.joinToString(" ")}"
        }
        if (form.isNotEmpty()) factors += Factor("Recent form", form.joinToString(" · "), "Results of each side's last five matches, newest first.")
        summary.a("seasonseries").objects().firstOrNull { it.str("type") == "head-to-head" }?.str("summary")
            ?.let { factors += Factor("Head-to-head", it, "How these two sides have fared against each other recently.") }
        val ranks = listOfNotNull(
            fixture.home?.let { h -> tableRanks[h.id]?.let { "${h.name} ${ordinal(it)}" } },
            fixture.away?.let { a -> tableRanks[a.id]?.let { "${a.name} ${ordinal(it)}" } },
        )
        if (ranks.isNotEmpty()) factors += Factor("League position", ranks.joinToString(" · "), "Current standing in the table.")
        return factors
    }

    /** For sports with no published odds (F1, most cricket) show what we know instead of inventing a number. */
    fun unavailable(reason: String, factors: List<Factor> = emptyList()) = Prediction(unavailableReason = reason, factors = factors)

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }
}

/**
 * Where-to-watch. Free APIs carry almost no per-country broadcast data, so for India this uses a small
 * hand-checked table (broadcast rights change every season, hence [VERIFIED_ON]). Everything else falls
 * back to a web search link rather than a guess.
 */
object WatchRights {
    const val VERIFIED_ON = "2026-10-14"

    private val JIOSTAR = listOf(
        WatchOption("JioHotstar", "stream", "Paid subscription"),
        WatchOption("Star Sports", "tv"),
    )
    private val SONY_UEFA = listOf(
        WatchOption("Sony LIV", "stream", "Paid subscription"),
        WatchOption("Sony Sports Network", "tv"),
    )
    private val ZEE = listOf(
        WatchOption("ZEE5", "stream", "Paid subscription"),
        WatchOption("Unite8 Sports", "tv"),
    )

    /** Curated India rights keyed by league id. Missing = unknown, so the UI shows a search link. */
    private val INDIA: Map<String, List<WatchOption>> = mapOf(
        "soccer:eng.1" to JIOSTAR,
        "soccer:uefa.champions" to SONY_UEFA,
        "soccer:uefa.europa" to SONY_UEFA,
        "soccer:uefa.europa.conf" to SONY_UEFA,
        "soccer:ita.1" to ZEE,
        "soccer:ita.coppa_italia" to ZEE,
        "soccer:ger.1" to ZEE,
        "cricket:8048" to JIOSTAR,
        "f1:f1" to listOf(WatchOption("FanCode", "stream", "Paid race pass or season pass; no TV broadcast in India")),
    )

    private val CRICKET_INDIA_BILATERAL = listOf(
        WatchOption("JioHotstar", "stream", "Usual home of BCCI series; not confirmed for this series", confirmed = false),
        WatchOption("Star Sports", "tv", "Not confirmed for this series", confirmed = false),
    )

    fun forRegion(region: String, fixture: Fixture, espnBroadcasts: List<Pair<String, String>>, countryName: String): WatchInfo {
        val search = "https://www.google.com/search?q=" +
            URLEncoder.encode("${fixture.title} where to watch live in $countryName", "UTF-8")
        if (region.equals("IN", ignoreCase = true)) {
            val curated = INDIA[fixture.leagueId]
                ?: CRICKET_INDIA_BILATERAL.takeIf { fixture.leagueId.startsWith("cricket:") && fixture.title.contains("India") }
            if (curated != null) return WatchInfo("IN", curated, search, VERIFIED_ON)
            return WatchInfo("IN", emptyList(), search, VERIFIED_ON)
        }
        // Other regions: show whatever the API reports (ESPN lists US networks), clearly labelled.
        val fromApi = espnBroadcasts.filter { (r, _) -> r.equals(region, ignoreCase = true) }
            .map { (r, name) -> WatchOption(name, "tv", "Listed by ESPN for ${r.uppercase()}") }
        return WatchInfo(region.uppercase(), fromApi, search, null)
    }
}
