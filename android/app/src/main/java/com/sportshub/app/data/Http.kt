package com.sportshub.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The host asked us to slow down (403/429); we stop calling it for a while. */
class UpstreamBlocked(message: String) : IOException(message)

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/**
 * Every network call goes through here. The free sports APIs rate-limit by IP and will block
 * a burst, so each host gets a concurrency cap, a gap between calls, and a cool-down after 403/429.
 */
class HttpGate(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val hostLimits = mapOf("sports.core.api.espn.com" to 4, "site.api.espn.com" to 4, "api.jolpi.ca" to 3)
    private val semaphores = ConcurrentHashMap<String, Semaphore>()
    private val gapLocks = ConcurrentHashMap<String, Mutex>()
    private val lastCall = ConcurrentHashMap<String, Long>()
    private val blockedUntil = ConcurrentHashMap<String, Long>()

    suspend fun getJson(url: String, params: Map<String, String> = emptyMap(), retries: Int = 2): JsonElement {
        val httpUrl = url.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val host = httpUrl.host
        if ((blockedUntil[host] ?: 0L) > now()) throw UpstreamBlocked("$host is cooling down after a rate-limit response")
        val semaphore = semaphores.getOrPut(host) { Semaphore(hostLimits[host] ?: DEFAULT_LIMIT) }

        var last: Exception? = null
        for (attempt in 0..retries) {
            try {
                val body = semaphore.withPermit {
                    pace(host)
                    fetch(httpUrl.toString(), host)
                }
                return Json.parseToJsonElement(body)
            } catch (e: UpstreamBlocked) {
                throw e
            } catch (e: HttpStatusException) {
                if (e.code < 500) throw e // other 4xx will not get better on retry
                last = e
            } catch (e: IOException) {
                last = e
            } catch (e: SerializationException) {
                last = e
            }
            delay(500L * (attempt + 1))
        }
        throw last!!
    }

    private suspend fun pace(host: String) = gapLocks.getOrPut(host) { Mutex() }.withLock {
        val wait = (lastCall[host] ?: 0L) + MIN_GAP_MS - now()
        if (wait > 0) delay(wait)
        lastCall[host] = now()
    }

    private suspend fun fetch(url: String, host: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 403 || resp.code == 429) {
                blockedUntil[host] = now() + BLOCK_COOLDOWN_MS
                throw UpstreamBlocked("$host returned ${resp.code}")
            }
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, url)
            resp.body?.string() ?: throw IOException("empty body from $url")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 4
        const val MIN_GAP_MS = 150L
        const val BLOCK_COOLDOWN_MS = 5 * 60_000L
        const val USER_AGENT = "SportsHub/0.1 (Android personal app)"
    }
}
