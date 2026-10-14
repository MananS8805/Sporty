package com.sportshub.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory TTL cache. If a refresh fails it serves the stale value, so a flaky free API
 * degrades to old data instead of an error screen.
 */
class TtlCache(private val now: () -> Long = System::currentTimeMillis) {
    private class Entry(val expiresAt: Long, val value: Any?)

    private val data = ConcurrentHashMap<String, Entry>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getOrSet(key: String, ttlMs: Long, factory: suspend () -> T): T {
        data[key]?.takeIf { it.expiresAt > now() }?.let { return it.value as T }
        return locks.getOrPut(key) { Mutex() }.withLock {
            val hit = data[key]
            if (hit != null && hit.expiresAt > now()) return@withLock hit.value as T
            try {
                factory().also { data[key] = Entry(now() + ttlMs, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hit != null) hit.value as T else throw e
            }
        }
    }
}
