package com.videoplayer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
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
private val glassThemeKey = stringPreferencesKey("glass_theme_id")

typealias PositionMap = Map<String, Long>

suspend fun Context.saveLastPosition(videoId: String, positionMs: Long) {
    if (videoId.isBlank() || positionMs < 0L) return
    val key = longPreferencesKey("last_position_$videoId")
    this.datastore.edit { prefs ->
        prefs[key] = positionMs
        prefs[lastPlayedVideoIdKey] = videoId
    }
}

suspend fun Context.readLastPosition(videoId: String): Long {
    val key = longPreferencesKey("last_position_$videoId")
    return this.datastore.data.first()[key] ?: 0L
}

fun Context.observeAllPositions(): Flow<PositionMap> {
    return this.datastore.data.map { prefs: Preferences ->
        val result = HashMap<String, Long>()
        for (entry in prefs.asMap().entries) {
            val kName = entry.key.name
            val vVal = entry.value
            if (kName.startsWith("last_position_") && vVal is Long) {
                result[kName.removePrefix("last_position_")] = vVal
            }
        }
        result
    }
}

fun Context.observeLastPlayedVideoId(): Flow<String?> {
    return this.datastore.data.map { prefs: Preferences ->
        prefs[lastPlayedVideoIdKey]
    }
}

suspend fun Context.saveGithubRepo(ownerAndRepo: String) {
    this.datastore.edit { prefs ->
        prefs[githubRepoKey] = ownerAndRepo
    }
}

fun Context.readGithubRepo(defaultRepo: String): Flow<String> {
    return this.datastore.data.map { prefs: Preferences ->
        prefs[githubRepoKey] ?: defaultRepo
    }
}

suspend fun Context.saveGlassTheme(themeId: String) {
    this.datastore.edit { prefs ->
        prefs[glassThemeKey] = themeId
    }
}

fun Context.observeGlassTheme(): Flow<String> {
    return this.datastore.data.map { prefs: Preferences ->
        prefs[glassThemeKey] ?: "cyan_nebula"
    }
}