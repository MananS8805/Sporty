package com.sportshub.app.data

import com.sportshub.app.BuildConfig
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface Api {
    @GET("leagues")
    suspend fun leagues(): List<League>

    // League ids contain ':' (e.g. "soccer:eng.1"), so keep them unencoded in the path.
    @GET("leagues/{league}/teams")
    suspend fun teams(@Path("league", encoded = true) league: String): List<Team>

    @GET("leagues/{league}/standings")
    suspend fun standings(@Path("league", encoded = true) league: String): List<StandingGroup>

    @GET("leagues/{league}/teams/{team}")
    suspend fun overview(
        @Path("league", encoded = true) league: String,
        @Path("team", encoded = true) team: String,
    ): TeamOverview

    @GET("feed")
    suspend fun feed(@Query("follow") follow: List<String>): Feed

    companion object {
        fun create(): Api {
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            // The backend's first call can take ~20s while it discovers leagues.
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(Api::class.java)
        }
    }
}
