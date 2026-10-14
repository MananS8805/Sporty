package com.sportshub.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.OffsetDateTime

// The upstream APIs are large and loosely shaped, so we walk the JSON tree instead of
// declaring DTOs for every field. All helpers are null-safe: a missing field is just null/empty.

fun JsonElement?.asObj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

fun JsonObject?.o(key: String): JsonObject? = this?.get(key) as? JsonObject

fun JsonObject?.a(key: String): JsonArray = this?.get(key) as? JsonArray ?: JsonArray(emptyList())

fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull

fun JsonObject?.dbl(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull

fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

fun parseInstant(value: String?): java.time.Instant? =
    value?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
