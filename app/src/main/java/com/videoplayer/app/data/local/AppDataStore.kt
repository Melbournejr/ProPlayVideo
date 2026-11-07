package com.videoplayer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.datastore by preferencesDataStore(name = "videoplayer_prefs")

private val themeModeKey = stringPreferencesKey("theme_mode")
private val githubRepoKey = stringPreferencesKey("github_repo")

suspend fun Context.saveLastPosition(videoId: String, positionMs: Long) {
    val key = longPreferencesKey("last_position_$videoId")
    datastore.edit { it[key] = positionMs }
}

suspend fun Context.readLastPosition(videoId: String): Long {
    val key = longPreferencesKey("last_position_$videoId")
    return datastore.data.first()[key] ?: 0L
}

suspend fun Context.saveGithubRepo(ownerAndRepo: String) {
    datastore.edit { it[githubRepoKey] = ownerAndRepo }
}

fun Context.readGithubRepo(defaultRepo: String): Flow<String> {
    return datastore.data.map { it[githubRepoKey] ?: defaultRepo }
}