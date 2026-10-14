package com.sportshub.app.ui

import androidx.compose.ui.graphics.Color
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun String.toLocalTime(): ZonedDateTime? =
    runCatching { OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault()) }.getOrNull()

private fun String.format(pattern: String): String =
    toLocalTime()?.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())) ?: this

fun formatKickoff(iso: String) = iso.format("EEE d MMM, HH:mm")
fun formatTime(iso: String) = iso.format("HH:mm")
fun formatDay(iso: String) = iso.format("EEE d MMM")
fun formatShortDay(iso: String) = iso.format("d MMM")

/** Backend sends colours as hex without '#', e.g. "DC0000". */
fun parseHex(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor("#$it")) }.getOrNull() }

fun sportLabel(sport: String) = when (sport) {
    "soccer" -> "Football"
    "football" -> "American football"
    "f1" -> "Formula 1"
    "cricket" -> "Cricket"
    "basketball" -> "Basketball"
    "baseball" -> "Baseball"
    "hockey" -> "Hockey"
    "rugby" -> "Rugby"
    else -> sport.replaceFirstChar { it.uppercase() }
}
