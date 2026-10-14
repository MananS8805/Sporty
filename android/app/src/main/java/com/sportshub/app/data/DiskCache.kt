package com.sportshub.app.data

import android.content.Context

/**
 * Tiny persistent cache for results that are expensive to rebuild (which series a national team plays in,
 * which confederation it belongs to). Background checks start a fresh process each time, so an in-memory
 * cache alone would repeat dozens of requests on every run.
 */
class DiskCache(context: Context) {
    private val prefs = context.getSharedPreferences("disk_cache", Context.MODE_PRIVATE)

    fun get(key: String, maxAgeMs: Long, now: Long = System.currentTimeMillis()): String? {
        val savedAt = prefs.getLong("$key:at", 0L)
        return if (now - savedAt <= maxAgeMs) prefs.getString(key, null) else null
    }

    fun put(key: String, value: String, now: Long = System.currentTimeMillis()) {
        prefs.edit().putString(key, value).putLong("$key:at", now).apply()
    }
}
