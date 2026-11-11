package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Rational
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.VideoSnapshotHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun Context.resolveUriFileName(uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) result = cursor.getString(idx)
            }
        }
    }
    return result ?: (uri.path?.substringAfterLast('/') ?: "subtitle.srt")
}

@Composable
fun PlayerModalsLayer(
    state: PlayerStateHolder,
    activity: Activity?
) {
    val palette = LocalGlassPalette.current
    var studioTabIndex by remember { mutableIntStateOf(0) }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val vid = state.currentVideo
        if (uri != null && vid != null) {
            try {
                state.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val fileName = state.context.resolveUriFileName(uri)
            state.scope.launch {
                state.loadedSubtitles = state.playerManager.attachExternalSubtitleToCurrent(vid, uri, fileName)
                state.subtitlesEnabled = true
                state.gestureHint = "💬 Đã nạp phụ đề: $fileName"
                delay(1600)
                state.gestureHint = null
            }
        }
    }

    if (state.activeModal == ActiveModal.STUDIO) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            highlighted = true,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 520.dp)
                .heightIn(max = 350.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.AutoAwesome, contentDescription = null, tint = palette.primaryGlow)
                    Spacer(Modifier.width(8.dp))
                    Text("Pro Studio", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    LiquidGlassPill(onClick = { state.activeModal = ActiveModal.NONE }) {
                        Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassPill(active = studioTabIndex == 0, onClick = { studioTabIndex = 0 }, modifier = Modifier.weight(1f)) {
                        Text("🎬 Hình Ảnh & A-B", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    LiquidGlassPill(
                        active = studioTabIndex == 1,
                        onClick = {
                            state.eqBands = state.playerManager.getEqualizerBands()
                            studioTabIndex = 1
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🎵 Âm Thanh & EQ", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (studioTabIndex == 0) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassPill(
                            active = state.loopPointA != null,
                            onClick = {
                                state.loopPointA = state.playerManager.getCurrentPosition()
                                if (state.loopPointB != null && state.loopPointB!! <= state.loopPointA!!) state.loopPointB = null
                            }
                        ) { Text(if (state.loopPointA != null) "A: ${formatTimeMs(state.loopPointA!!)}" else "Đặt điểm A", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                        LiquidGlassPill(
                            active = state.loopPointB != null,
                            onClick = {
                                val now = state.playerManager.getCurrentPosition()
                                if (state.loopPointA == null) state.loopPointA = 0L
                                if (now > state.loopPointA!! + 500L) state.loopPointB = now
                            }
                        ) { Text(if (state.loopPointB != null) "B: ${formatTimeMs(state.loopPointB!!)}" else "Đặt điểm B", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                        if (state.loopPointA != null || state.loopPointB != null) {
                            LiquidGlassPill(onClick = { state.loopPointA = null; state.loopPointB = null }) {
                                Text("Xóa A-B", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        LiquidGlassPill(onClick = {
                            state.playerManager.stepFrame(forward = false)
                            state.isPlaying = false
                            state.currentPos = state.playerManager.getCurrentPosition()
                        }) { Text("⏮ -1 Frame", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                        LiquidGlassPill(onClick = {
                            state.playerManager.stepFrame(forward = true)
                            state.isPlaying = false
                            state.currentPos = state.playerManager.getCurrentPosition()
                        }) { Text("+1 Frame ⏭", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Độ tươi màu", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
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
                        Text("Tương phản", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Siêu trầm", color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                        Slider(
                            value = state.bassBoostPercent.toFloat(),
                            onValueChange = {
                                state.bassBoostPercent = it.roundToInt()
                                state.playerManager.setBassBoostPercent(state.bassBoostPercent)
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F))
                        )
                        Text("${state.bassBoostPercent}%", color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(38.dp))
                    }

                    state.eqBands.forEach { band ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(band.label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                            Slider(
                                value = band.levelDb,
                                onValueChange = { newDb ->
                                    state.activeEqPresetName = "Tùy chỉnh"
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

    if (state.activeModal == ActiveModal.TOOLS) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            highlighted = true,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 500.dp)
                .heightIn(max = 350.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Tune, contentDescription = null, tint = palette.primaryGlow)
                    Spacer(Modifier.width(8.dp))
                    Text("Tiện Ích & Phụ Đề", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    LiquidGlassPill(onClick = { state.activeModal = ActiveModal.NONE }) {
                        Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassPill(
                        onClick = {
                            val vid = state.currentVideo
                            val pos = state.playerManager.getCurrentPosition()
                            state.activeModal = ActiveModal.NONE
                            state.gestureHint = "📸 Đang chụp khung hình gốc..."
                            state.scope.launch {
                                val res = VideoSnapshotHelper.captureCurrentFrame(state.context, vid?.path, pos, vid?.title ?: "Video")
                                state.gestureHint = res.fold(
                                    onSuccess = { "✅ Đã lưu: $it" },
                                    onFailure = { "⚠️ Lỗi: ${it.message}" }
                                )
                                delay(2000)
                                state.gestureHint = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chụp ảnh", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    LiquidGlassPill(
                        onClick = {
                            state.activeModal = ActiveModal.NONE
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                state.persistCurrentPosition()
                                state.showControls = false
                                activity.enterPictureInPictureMode(
                                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cửa sổ PiP", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    state.currentVideo?.let { curVid ->
                        LiquidGlassPill(
                            onClick = {
                                state.activeModal = ActiveModal.NONE
                                state.playerManager.pause()
                                state.isPlaying = false
                                FileOperationHelper.shareVideo(state.context, curVid)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chia sẻ", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Cắt tràn"
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Kéo giãn"
                            else -> "Vừa khung"
                        }
                        Text("Khung hình: $modeName", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    LiquidGlassPill(
                        active = state.backgroundAudioEnabled,
                        onClick = { state.backgroundAudioEnabled = !state.backgroundAudioEnabled },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(AppIcons.Headphones, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.backgroundAudioEnabled) "Nghe nền: BẬT" else "Nghe nền: TẮT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💤 Hẹn giờ:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    listOf(0, 15, 30, 45, 60).forEach { mins ->
                        LiquidGlassPill(active = state.sleepTimerMinutes == mins, onClick = { state.startSleepTimer(mins) }) {
                            Text(if (mins == 0) "Tắt" else "${mins}p", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                val audioTracks = remember(state.currentVideo, state.isPlaying, state.activeModal) {
                    state.playerManager.getAvailableTracks(C.TRACK_TYPE_AUDIO)
                }
                if (audioTracks.isNotEmpty()) {
                    Text("🔊 Kênh Âm Thanh (Audio Track):", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        audioTracks.forEach { trackOpt ->
                            LiquidGlassPill(
                                active = trackOpt.isSelected,
                                onClick = {
                                    state.playerManager.selectTrackOverride(trackOpt)
                                    state.gestureHint = "🔊 Đã chọn: ${trackOpt.label}"
                                    state.scope.launch { delay(1200); state.gestureHint = null }
                                }
                            ) { Text(trackOpt.label, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Cỡ chữ:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    listOf(16f to "Nhỏ", 20f to "Vừa", 26f to "Lớn", 32f to "To").forEach { (sz, lbl) ->
                        LiquidGlassPill(
                            active = state.subtitleSizeSp == sz,
                            onClick = {
                                state.subtitleSizeSp = sz
                                state.scope.launch { state.context.saveSubtitleAppearance(sz, state.subtitleTextColor.toArgb()) }
                            }
                        ) { Text(lbl, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                    LiquidGlassPill(
                        onClick = {
                            state.activeModal = ActiveModal.NONE
                            subtitlePickerLauncher.launch(arrayOf("*/*"))
                        }
                    ) { Text("📂 Mở .SRT/.ASS", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}