package com.videoplayer.app.data.local

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

// Các key cũ (Dùng để kiểm tra và Migration tự động cho người dùng cũ)
private val legacyVaultPinKey = stringPreferencesKey("private_vault_pin")
private val legacyVaultVideoIdsKey = stringSetPreferencesKey("private_vault_video_ids")

// Các key bảo mật mới (PBKDF2 Hash + Salt + Brute-force Lockout)
private val vaultPinHashKey = stringPreferencesKey("vault_pin_pbkdf2_hash")
private val vaultPinSaltKey = stringPreferencesKey("vault_pin_pbkdf2_salt")
private val vaultFailedAttemptsKey = intPreferencesKey("vault_failed_attempts")
private val vaultLockoutUntilMsKey = longPreferencesKey("vault_lockout_until_ms")
private val httpCleartextWarnAckKey = booleanPreferencesKey("http_cleartext_warning_ack")
private val vaultBiometricEnabledKey = booleanPreferencesKey("vault_biometric_enabled")

private val savedBrightnessKey = floatPreferencesKey("saved_player_brightness")
private val subtitleSizeSpKey = floatPreferencesKey("saved_subtitle_size_sp")
private val subtitleColorArgbKey = intPreferencesKey("saved_subtitle_color_argb")

typealias PositionMap = Map<String, Long>
typealias VaultIdSet = Set<String>

data class VaultSecurityState(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val legacyPlainPin: String? = null,
    val legacyVaultIds: Set<String> = emptySet(),
    val biometricEnabled: Boolean = true
) {
    val isConfigured: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
    val needsMigration: Boolean get() = !legacyPlainPin.isNullOrBlank() || legacyVaultIds.isNotEmpty()
}

fun Context.observeVaultSecurityState(): Flow<VaultSecurityState> {
    return this.datastore.data.map { prefs ->
        VaultSecurityState(
            pinHash = prefs[vaultPinHashKey],
            pinSalt = prefs[vaultPinSaltKey],
            failedAttempts = prefs[vaultFailedAttemptsKey] ?: 0,
            lockoutUntilMs = prefs[vaultLockoutUntilMsKey] ?: 0L,
            legacyPlainPin = prefs[legacyVaultPinKey],
            legacyVaultIds = prefs[legacyVaultVideoIdsKey] ?: emptySet(),
            biometricEnabled = prefs[vaultBiometricEnabledKey] ?: true
        )
    }
}

suspend fun Context.saveHashedVaultPin(hashBase64: String, saltBase64: String) {
    this.datastore.edit { prefs ->
        prefs[vaultPinHashKey] = hashBase64
        prefs[vaultPinSaltKey] = saltBase64
        prefs[vaultFailedAttemptsKey] = 0
        prefs[vaultLockoutUntilMsKey] = 0L
        prefs.remove(legacyVaultPinKey)
    }
}

suspend fun Context.clearLegacyVaultDataAfterMigration() {
    this.datastore.edit { prefs ->
        prefs.remove(legacyVaultPinKey)
        prefs.remove(legacyVaultVideoIdsKey)
    }
}

suspend fun Context.recordVaultPinAttempt(success: Boolean, lockoutDurationMs: Long = 0L) {
    this.datastore.edit { prefs ->
        if (success) {
            prefs[vaultFailedAttemptsKey] = 0
            prefs[vaultLockoutUntilMsKey] = 0L
        } else {
            val nextCount = (prefs[vaultFailedAttemptsKey] ?: 0) + 1
            prefs[vaultFailedAttemptsKey] = nextCount
            if (lockoutDurationMs > 0L) {
                prefs[vaultLockoutUntilMsKey] = System.currentTimeMillis() + lockoutDurationMs
            }
        }
    }
}

suspend fun Context.saveVaultBiometricEnabled(enabled: Boolean) {
    this.datastore.edit { prefs -> prefs[vaultBiometricEnabledKey] = enabled }
}

fun Context.observeHttpWarnAcknowledged(): Flow<Boolean> {
    return this.datastore.data.map { prefs -> prefs[httpCleartextWarnAckKey] ?: false }
}

suspend fun Context.saveHttpWarnAcknowledged(ack: Boolean) {
    this.datastore.edit { prefs -> prefs[httpCleartextWarnAckKey] = ack }
}

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
    this.datastore.edit { prefs -> prefs[githubRepoKey] = ownerAndRepo.trim() }
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