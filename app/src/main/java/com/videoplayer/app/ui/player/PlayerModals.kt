package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import com.videoplayer.app.data.local.saveSubtitleAppearance
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassIconButton
import com.videoplayer.app.ui.components.LiquidGlassModalSheet
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.FileOperationHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BoxScope.PlayerActiveModals(
    state: PlayerStateHolder,
    activity: Activity?,
    onLaunchSubtitlePicker: () -> Unit
) {
    if (state.isInPipMode) return

    when (state.activeModal) {
        ActiveModal.STUDIO -> ProStudioBottomSheet(state = state)
        ActiveModal.TOOLS -> PlayerToolsBottomSheet(
            state = state,
            activity = activity,
            onLaunchSubtitlePicker = onLaunchSubtitlePicker
        )
        ActiveModal.BOOKMARKS -> PlayerBookmarkModal(state = state)
        ActiveModal.NONE -> {}
    }
}

@Composable
private fun BoxScope.ProStudioBottomSheet(state: PlayerStateHolder) {
    val palette = LocalGlassPalette.current

    LiquidGlassModalSheet(
        cornerRadius = 28.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 560.dp)
            .heightIn(max = 310.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.AutoAwesome, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pro Studio",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(onClick = { state.activeModal = ActiveModal.NONE }, size = 32.dp) {
                    Icon(Icons.Default.Close, contentDescription = "\u0110\u00F3ng", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassPill(
                    active = state.studioTabIndex == 0,
                    onClick = { state.studioTabIndex = 0 },
                    modifier = Modifier.weight(1f)
                ) { Text("H\u00ECnh \u1EA3nh & A-B", color = Color.White, style = MaterialTheme.typography.labelMedium) }

                LiquidGlassPill(
                    active = state.studioTabIndex == 1,
                    onClick = {
                        state.eqBands = state.playerManager.getEqualizerBands()
                        state.studioTabIndex = 1
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("\u00C2m thanh & EQ", color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }

            if (state.studioTabIndex == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidGlassPill(
                        active = state.loopPointA != null,
                        onClick = {
                            state.loopPointA = state.playerManager.getCurrentPosition()
                            if (state.loopPointB != null && state.loopPointB!! <= state.loopPointA!!) state.loopPointB = null
                        }
                    ) { Text(if (state.loopPointA != null) "A: ${formatTimeMs(state.loopPointA!!)}" else "\u0110\u1EB7t \u0111i\u1EC3m A", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                    LiquidGlassPill(
                        active = state.loopPointB != null,
                        onClick = {
                            val now = state.playerManager.getCurrentPosition()
                            if (state.loopPointA == null) state.loopPointA = 0L
                            if (now > state.loopPointA!! + 500L) state.loopPointB = now
                        }
                    ) { Text(if (state.loopPointB != null) "B: ${formatTimeMs(state.loopPointB!!)}" else "\u0110\u1EB7t \u0111i\u1EC3m B", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                    if (state.loopPointA != null || state.loopPointB != null) {
                        LiquidGlassPill(onClick = { state.loopPointA = null; state.loopPointB = null }) {
                            Text("X\u00F3a A-B", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    LiquidGlassPill(onClick = {
                        state.playerManager.stepFrame(forward = false)
                        state.isPlaying = false
                        state.currentPos = state.playerManager.getCurrentPosition()
                    }) { Text("-1 Frame", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                    LiquidGlassPill(onClick = {
                        state.playerManager.stepFrame(forward = true)
                        state.isPlaying = false
                        state.currentPos = state.playerManager.getCurrentPosition()
                    }) { Text("+1 Frame", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidGlassPill(active = state.isMirrorFlipped, onClick = { state.isMirrorFlipped = !state.isMirrorFlipped }) {
                        Text(if (state.isMirrorFlipped) "L\u1EADt g\u01B0\u01A1ng: B\u1EACT" else "L\u1EADt g\u01B0\u01A1ng", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    LiquidGlassPill(
                        active = state.showNerdStats,
                        onClick = {
                            state.showNerdStats = !state.showNerdStats
                            if (state.showNerdStats) state.nerdStats = state.playerManager.getNerdStats()
                        }
                    ) { Text("Th\u00F4ng s\u1ED1 k\u1EF9 thu\u1EADt", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                    LiquidGlassPill(onClick = {
                        state.videoSaturation = 1.0f
                        state.videoContrast = 1.0f
                        state.videoBrightnessFilter = 0f
                    }) { Text("M\u00E0u g\u1ED1c", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("\u0110\u1ED9 t\u01B0\u01A1i m\u00E0u", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
                    Slider(
                        value = state.videoSaturation,
                        onValueChange = { state.videoSaturation = it },
                        valueRange = 0f..2.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                    )
                    Text("${(state.videoSaturation * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("T\u01B0\u01A1ng ph\u1EA3n", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
                    Slider(
                        value = state.videoContrast,
                        onValueChange = { state.videoContrast = it },
                        valueRange = 0.6f..1.8f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                    )
                    Text("${(state.videoContrast * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidGlassPill(
                        active = state.audioBoostPercent > 0,
                        onClick = {
                            state.audioBoostPercent = if (state.audioBoostPercent == 0) 50 else if (state.audioBoostPercent == 50) 100 else 0
                            state.playerManager.setAudioBoostPercent(state.audioBoostPercent)
                        }
                    ) { Text("Boost: ${100 + state.audioBoostPercent}%", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                    val presets = listOf(
                        Triple("M\u1EB7c \u0111\u1ECBnh", listOf(0f, 0f, 0f, 0f, 0f), 0),
                        Triple("Si\u00EAu tr\u1EA7m", listOf(8f, 5f, 0f, 2f, 4f), 75),
                        Triple("R\u1EA1p phim", listOf(6f, 3f, -1f, 4f, 6f), 50),
                        Triple("Gi\u1ECDng n\u00F3i", listOf(-3f, 2f, 7f, 5f, 1f), 0)
                    )
                    presets.forEach { (name, levels, bass) ->
                        LiquidGlassPill(
                            active = state.activeEqPresetName == name,
                            onClick = {
                                state.activeEqPresetName = name
                                state.playerManager.applyEqPreset(levels, bass)
                                state.bassBoostPercent = bass
                                state.eqBands = state.playerManager.getEqualizerBands()
                            }
                        ) { Text(name, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Si\u00EAu tr\u1EA7m", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                    Slider(
                        value = state.bassBoostPercent.toFloat(),
                        onValueChange = {
                            state.bassBoostPercent = it.roundToInt()
                            state.playerManager.setBassBoostPercent(state.bassBoostPercent)
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                    )
                    Text("${state.bassBoostPercent}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(38.dp))
                }

                state.eqBands.forEach { band ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(band.label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                        Slider(
                            value = band.levelDb,
                            onValueChange = { newDb ->
                                state.activeEqPresetName = "T\u00F9y ch\u1EC9nh"
                                state.playerManager.setEqBandLevel(band.index, newDb)
                                state.eqBands = state.playerManager.getEqualizerBands()
                            },
                            valueRange = -15f..15f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                        )
                        Text("%+.0fdB".format(band.levelDb), color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun BoxScope.PlayerToolsBottomSheet(
    state: PlayerStateHolder,
    activity: Activity?,
    onLaunchSubtitlePicker: () -> Unit
) {
    val palette = LocalGlassPalette.current

    LiquidGlassModalSheet(
        cornerRadius = 28.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 560.dp)
            .heightIn(max = 320.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Tune, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Ti\u1EC7n \u00EDch & Ph\u1EE5 \u0111\u1EC1",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(onClick = { state.activeModal = ActiveModal.NONE }, size = 32.dp) {
                    Icon(Icons.Default.Close, contentDescription = "\u0110\u00F3ng", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassPill(onClick = { state.takeVideoSnapshot() }, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ch\u1EE5p \u1EA3nh", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(onClick = { state.enterPip(activity) }, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PiP", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (state.currentVideo != null) {
                    LiquidGlassPill(
                        onClick = {
                            state.activeModal = ActiveModal.NONE
                            state.playerManager.pause()
                            state.isPlaying = false
                            FileOperationHelper.shareVideo(state.context, state.currentVideo!!)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chia s\u1EBB", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassPill(
                    active = state.backgroundAudioEnabled,
                    onClick = { state.backgroundAudioEnabled = !state.backgroundAudioEnabled },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(AppIcons.Headphones, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.backgroundAudioEnabled) "Nghe n\u1EC1n: B\u1EACT" else "Nghe n\u1EC1n", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(
                    onClick = {
                        state.activeModal = ActiveModal.NONE
                        state.isLocked = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kh\u00F3a ch\u1EA1m", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(
                    active = state.resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    onClick = {
                        state.resizeMode = when (state.resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    val modeName = when (state.resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "C\u1EAFt tr\u00E0n"
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "K\u00E9o gi\u00E3n"
                        else -> "V\u1EEBa khung"
                    }
                    Text(modeName, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("H\u1EB9n gi\u1EDD:", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                listOf(0, 15, 30, 45, 60).forEach { mins ->
                    LiquidGlassPill(active = state.sleepTimerMinutes == mins, onClick = { state.sleepTimerMinutes = mins }) {
                        Text(if (mins == 0) "T\u1EAFt" else "${mins}p", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassPill(
                    active = state.subtitlesEnabled,
                    onClick = {
                        state.subtitlesEnabled = !state.subtitlesEnabled
                        state.playerManager.toggleSubtitles(state.subtitlesEnabled)
                    }
                ) { Text(if (state.subtitlesEnabled) "Ph\u1EE5 \u0111\u1EC1: B\u1EACT" else "Ph\u1EE5 \u0111\u1EC1: T\u1EAET", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                LiquidGlassPill(
                    onClick = {
                        state.activeModal = ActiveModal.NONE
                        onLaunchSubtitlePicker()
                    }
                ) { Text("M\u1EDF file .SRT/.ASS", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }

            val audioTracks = remember(state.currentVideo, state.isPlaying, state.activeModal) {
                state.playerManager.getAvailableTracks(C.TRACK_TYPE_AUDIO)
            }
            if (audioTracks.isNotEmpty()) {
                Text("K\u00EAnh \u00E2m thanh", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    audioTracks.forEach { trackOpt ->
                        LiquidGlassPill(
                            active = trackOpt.isSelected,
                            onClick = {
                                state.playerManager.selectTrackOverride(trackOpt)
                                state.showTemporaryHint("\u00C2m thanh: ${trackOpt.label}", 1200L)
                            }
                        ) { Text(trackOpt.label, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            val textTracks = remember(state.currentVideo, state.loadedSubtitles, state.isPlaying, state.activeModal) {
                state.playerManager.getAvailableTracks(C.TRACK_TYPE_TEXT)
            }
            if (textTracks.isNotEmpty()) {
                Text("K\u00EAnh ph\u1EE5 \u0111\u1EC1", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    textTracks.forEach { trackOpt ->
                        LiquidGlassPill(
                            active = state.subtitlesEnabled && trackOpt.isSelected,
                            onClick = {
                                state.subtitlesEnabled = true
                                state.playerManager.selectTrackOverride(trackOpt)
                                state.showTemporaryHint("Ph\u1EE5 \u0111\u1EC1: ${trackOpt.label}", 1200L)
                            }
                        ) { Text(trackOpt.label, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("C\u1EE1 & M\u00E0u:", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                listOf(16f to "Nh\u1ECF", 20f to "V\u1EEBa", 26f to "L\u1EDBn", 32f to "To").forEach { (sz, lbl) ->
                    LiquidGlassPill(
                        active = state.subtitleSizeSp == sz,
                        onClick = {
                            state.subtitleSizeSp = sz
                            state.scope.launch { state.context.saveSubtitleAppearance(sz, state.subtitleTextColor.toArgb()) }
                        }
                    ) { Text(lbl, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
                listOf(Color.White to "Tr\u1EAFng", Color(0xFFFFEA00) to "V\u00E0ng", Color(0xFF00F0FF) to "Cyan").forEach { (col, name) ->
                    LiquidGlassPill(
                        active = state.subtitleTextColor == col,
                        onClick = {
                            state.subtitleTextColor = col
                            state.scope.launch { state.context.saveSubtitleAppearance(state.subtitleSizeSp, col.toArgb()) }
                        }
                    ) { Text(name, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}