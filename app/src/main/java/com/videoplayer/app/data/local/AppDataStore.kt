package com.videoplayer.app.data.local

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.datastore by preferencesDataStore(name = "videoplayer_prefs")

private val githubRepoKey = stringPreferencesKey("github_repo")
private val lastPlayedVideoIdKey = stringPreferencesKey("last_played_video_id")
private val glassThemeKey = stringPreferencesKey("glass_theme_id")
private val glassTintKey = stringPreferencesKey("glass_surface_tint_id")
private val glassFrostAlphaKey = floatPreferencesKey("glass_frost_alpha")
private val vaultPinKey = stringPreferencesKey("private_vault_pin")
private val vaultVideoIdsKey = stringSetPreferencesKey("private_vault_video_ids")
private val savedBrightnessKey = floatPreferencesKey("saved_player_brightness")
private val subtitleSizeSpKey = floatPreferencesKey("saved_subtitle_size_sp")
private val subtitleColorArgbKey = intPreferencesKey("saved_subtitle_color_argb")

typealias PositionMap = Map<String, Long>
typealias VaultIdSet = Set<String>

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
    return this.datastore.data.map { prefs: Preferences -> prefs[lastPlayedVideoIdKey] }
}

suspend fun Context.saveFolderScrollState(folderKey: String, index: Int, offset: Int) {
    if (folderKey.isBlank()) return
    val idxKey = intPreferencesKey("scroll_idx_$folderKey")
    val offKey = intPreferencesKey("scroll_off_$folderKey")
    this.datastore.edit { prefs ->
        prefs[idxKey] = index.coerceAtLeast(0)
        prefs[offKey] = offset.coerceAtLeast(0)
    }
}

suspend fun Context.readFolderScrollState(folderKey: String): Pair<Int, Int> {
    if (folderKey.isBlank()) return 0 to 0
    val prefs = this.datastore.data.first()
    val idx = prefs[intPreferencesKey("scroll_idx_$folderKey")] ?: 0
    val off = prefs[intPreferencesKey("scroll_off_$folderKey")] ?: 0
    return idx to off
}

/**
 * Đọc độ sáng thực tế của máy (0.01f .. 1.0f) hoặc độ sáng người dùng đã lưu lần trước
 */
fun Context.getSystemScreenBrightness(): Float {
    return try {
        val sysVal = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (sysVal.toFloat() / 255f).coerceIn(0.02f, 1.0f)
    } catch (_: Exception) {
        0.5f
    }
}

suspend fun Context.readPreferredBrightness(): Float {
    val saved = this.datastore.data.first()[savedBrightnessKey] ?: -1f
    return if (saved in 0.01f..1.0f) saved else getSystemScreenBrightness()
}

suspend fun Context.savePreferredBrightness(brightness: Float) {
    this.datastore.edit { prefs ->
        prefs[savedBrightnessKey] = brightness.coerceIn(0.01f, 1.0f)
    }
}

/**
 * Lưu & Đọc thông tin Phụ đề đã chọn riêng cho từng videoId
 */
suspend fun Context.saveCustomSubtitleForVideo(videoId: String, localPathOrUri: String, displayName: String) {
    if (videoId.isBlank()) return
    val uriKey = stringPreferencesKey("sub_uri_$videoId")
    val nameKey = stringPreferencesKey("sub_name_$videoId")
    this.datastore.edit { prefs ->
        prefs[uriKey] = localPathOrUri
        prefs[nameKey] = displayName
    }
}

suspend fun Context.readCustomSubtitleForVideo(videoId: String): Pair<String, String>? {
    if (videoId.isBlank()) return null
    val prefs = this.datastore.data.first()
    val uriStr = prefs[stringPreferencesKey("sub_uri_$videoId")] ?: return null
    val nameStr = prefs[stringPreferencesKey("sub_name_$videoId")] ?: "subtitle.srt"
    return uriStr to nameStr
}

suspend fun Context.saveSubtitleAppearance(sizeSp: Float, colorArgb: Int) {
    this.datastore.edit { prefs ->
        prefs[subtitleSizeSpKey] = sizeSp
        prefs[subtitleColorArgbKey] = colorArgb
    }
}

suspend fun Context.readSubtitleAppearance(): Pair<Float, Int> {
    val prefs = this.datastore.data.first()
    val sz = prefs[subtitleSizeSpKey] ?: 20f
    val col = prefs[subtitleColorArgbKey] ?: android.graphics.Color.WHITE
    return sz to col
}

suspend fun Context.saveGithubRepo(ownerAndRepo: String) {
    this.datastore.edit { prefs -> prefs[githubRepoKey] = ownerAndRepo }
}

fun Context.readGithubRepo(defaultRepo: String): Flow<String> {
    return this.datastore.data.map { prefs -> prefs[githubRepoKey] ?: defaultRepo }
}

suspend fun Context.saveGlassTheme(themeId: String) {
    this.datastore.edit { prefs -> prefs[glassThemeKey] = themeId }
}

fun Context.observeGlassTheme(): Flow<String> {
    return this.datastore.data.map { prefs -> prefs[glassThemeKey] ?: "apple_ice" }
}

suspend fun Context.saveGlassTint(tintId: String) {
    this.datastore.edit { prefs -> prefs[glassTintKey] = tintId }
}

fun Context.observeGlassTint(): Flow<String> {
    return this.datastore.data.map { prefs -> prefs[glassTintKey] ?: "crystal_white" }
}

suspend fun Context.saveGlassFrostAlpha(alpha: Float) {
    this.datastore.edit { prefs -> prefs[glassFrostAlphaKey] = alpha.coerceIn(0.04f, 0.45f) }
}

fun Context.observeGlassFrostAlpha(): Flow<Float> {
    return this.datastore.data.map { prefs -> prefs[glassFrostAlphaKey] ?: 0.16f }
}

fun Context.observeVaultPin(): Flow<String?> {
    return this.datastore.data.map { prefs -> prefs[vaultPinKey] }
}

suspend fun Context.saveVaultPin(pin: String) {
    this.datastore.edit { prefs -> prefs[vaultPinKey] = pin.trim() }
}

fun Context.observeVaultVideoIds(): Flow<VaultIdSet> {
    return this.datastore.data.map { prefs -> prefs[vaultVideoIdsKey] ?: emptySet() }
}

suspend fun Context.toggleVideoInVault(videoId: String, addToVault: Boolean) {
    this.datastore.edit { prefs ->
        val current = prefs[vaultVideoIdsKey]?.toMutableSet() ?: mutableSetOf()
        if (addToVault) current.add(videoId) else current.remove(videoId)
        prefs[vaultVideoIdsKey] = current
    }
}

suspend fun Context.toggleMultipleVideosInVault(videoIds: Collection<String>, addToVault: Boolean) {
    this.datastore.edit { prefs ->
        val current = prefs[vaultVideoIdsKey]?.toMutableSet() ?: mutableSetOf()
        if (addToVault) current.addAll(videoIds) else current.removeAll(videoIds.toSet())
        prefs[vaultVideoIdsKey] = current
    }
}