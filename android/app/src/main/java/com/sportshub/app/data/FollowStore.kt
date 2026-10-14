package com.sportshub.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "follows")

class FollowStore(private val context: Context) {
    private val key = stringPreferencesKey("followed_teams")
    private val serializer = ListSerializer(FollowedTeam.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    val teams: Flow<List<FollowedTeam>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
    }

    suspend fun add(team: FollowedTeam) = update { list ->
        if (list.any { it.key == team.key }) list else list + team
    }

    suspend fun remove(followKey: String) = update { list -> list.filterNot { it.key == followKey } }

    private suspend fun update(transform: (List<FollowedTeam>) -> List<FollowedTeam>) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
                ?: emptyList()
            prefs[key] = json.encodeToString(serializer, transform(current))
        }
    }
}
