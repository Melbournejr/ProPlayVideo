package com.videoplayer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.datastore by preferencesDataStore(name = "videoplayer_prefs")

private val githubRepoKey = stringPreferencesKey("github_repo")
private val lastPlayedVideoIdKey = stringPreferencesKey("last_played_video_id")

suspend fun Context.saveLastPosition(videoId: String, positionMs: Long) {
    if (videoId.isBlank() || positionMs < 0L) return
    val key = longPreferencesKey("last_position_$videoId")
    datastore.edit { prefs ->
        prefs[key] = positionMs
        prefs[lastPlayedVideoIdKey] = videoId
    }
}

suspend fun Context.readLastPosition(videoId: String): Long {
    val key = longPreferencesKey("last_position_$videoId")
    return datastore.data.first()[key] ?: 0L
}

fun Context.observeAllPositions(): Flow> {
    return datastore.data.map { prefs ->
        val result = mutableMapOf<String, Long>()
        prefs.asMap().forEach { (k, v) ->
            if (k.name.startsWith("last_position_") && v is Long) {
                result[k.name.removePrefix("last_position_")] = v
            }
        }
        result
    }
}

fun Context.observeLastPlayedVideoId(): Flow<String?> {
    return datastore.data.map { prefs -> prefs[lastPlayedVideoIdKey] }
}

suspend fun Context.saveGithubRepo(ownerAndRepo: String) {
    datastore.edit { it[githubRepoKey] = ownerAndRepo }
}

fun Context.readGithubRepo(defaultRepo: String): Flow<String> {
    return datastore.data.map { it[githubRepoKey] ?: defaultRepo }
}