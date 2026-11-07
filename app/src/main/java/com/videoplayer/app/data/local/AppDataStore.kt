package com.videoplayer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.FirstFlow
import kotlinx.coroutines.flow.map

private val Context.datastore by preferencesDataStore(name = "videoplayer_prefs")

val Context.appStore: androidx.datastore.preferences.core.DataStore<androidx.datastore.preferences.core.Preferences>
    get() = datastore

private val themeModeKey = stringPreferencesKey("theme_mode")
private val playbackSpeedKey = stringPreferencesKey("playback_speed")
private val lockUiKey = booleanPreferencesKey("lock_ui")

suspend fun Context.saveLastPosition(videoId: String, positionMs: Long) {
    val key = longPreferencesKey("last_position_$videoId")
    datastore.edit { it[key] = positionMs }
}

suspend fun Context.readLastPosition(videoId: String): Long {
    val key = longPreferencesKey("last_position_$videoId")
    return datastore.data.first().getLong(key) ?: 0L
}

suspend fun Context.saveThemeMode(mode: String) {
    datastore.edit { it[themeModeKey] = mode }
}

fun Context.readThemeMode(): FirstFlow<String> {
    return datastore.data.map { it[themeModeKey] ?: "light" }
}

suspend fun Context.savePlaybackSpeed(speed: String) {
    datastore.edit { it[playbackSpeedKey] = speed }
}

fun Context.readPlaybackSpeed(): FirstFlow<String> {
    return datastore.data.map { it[playbackSpeedKey] ?: "1.0" }
}

suspend fun Context.saveLockUi(locked: Boolean) {
    datastore.edit { it[lockUiKey] = locked }
}

fun Context.readLockUi(): FirstFlow<Boolean> {
    return datastore.data.map { it[lockUiKey] ?: false }
}
