package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.provider.OpenableColumns
import android.net.Uri
import android.util.Rational
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.media3.ui.AspectRatioFrameLayout
import com.videoplayer.app.data.local.getSystemScreenBrightness
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.ExternalSubtitleItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.media.VideoNerdStats
import com.videoplayer.app.util.VideoSnapshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class GestureMode {
    NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM
}

enum class ActiveModal {
    NONE, STUDIO, TOOLS
}

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.resolveUriFileName(uri: Uri): String {
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

fun buildVideoColorPaint(saturation: Float, contrast: Float, brightnessOffset: Float): Paint? {
    if (abs(saturation - 1f) < 0.02f && abs(contrast - 1f) < 0.02f && abs(brightnessOffset) < 1f) {
        return null
    }
    val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
    val t = (1f - contrast) * 128f + brightnessOffset
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    satMatrix.postConcat(contrastMatrix)
    return Paint().apply { colorFilter = ColorMatrixColorFilter(satMatrix) }
}

/**
 * StateHolder quản lý tập trung toàn bộ trạng thái và hành vi của PlayerScreen.
 * Giúp các Composable bên dưới hoàn toàn "dumb" và giảm thiểu Recomposition.
 */
@Stable
class PlayerStateHolder(
    val context: Context,
    val scope: CoroutineScope,
    val playerManager: PlayerManager,
    initialVideo: VideoItem?,
    rawPlaylist: List<VideoItem>,
    val streamUrl: String?,
    private val onVideoChangedCallback: (VideoItem) -> Unit
) {
    val effectivePlaylist: List<VideoItem> = if (rawPlaylist.isNotEmpty()) rawPlaylist else listOfNotNull(initialVideo)

    var currentVideo by mutableStateOf(initialVideo)
    val currentIndex by derivedStateOf {
        val vid = currentVideo
        if (vid == null) -1 else effectivePlaylist.indexOfFirst { it.id == vid.id }
    }
    val hasPrevious by derivedStateOf { currentIndex > 0 }
    val hasNext by derivedStateOf { effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1 }
    val isVaultVideo by derivedStateOf {
        currentVideo?.id?.startsWith("vault_") == true || currentVideo?.path?.startsWith("vault://") == true
    }

    var isPlaying by mutableStateOf(true)
    var currentPos by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(1L)

    var isDraggingSlider by mutableStateOf(false)
    var lastSeekCompletedTimeMs by mutableLongStateOf(0L)

    var playbackSpeed by mutableFloatStateOf(1.0f)
    var isLocked by mutableStateOf(false)
    var showControls by mutableStateOf(true)
    var subtitlesEnabled by mutableStateOf(true)
    var loadedSubtitles by mutableStateOf<List<ExternalSubtitleItem>>(emptyList())
    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    var gestureHint by mutableStateOf<String?>(null)
    var isInPipMode by mutableStateOf(false)

    var audioBoostPercent by mutableIntStateOf(0)
    var backgroundAudioEnabled by mutableStateOf(false)
    var sleepTimerMinutes by mutableIntStateOf(0)
    var sleepRemainingSec by mutableIntStateOf(0)

    var activeModal by mutableStateOf(ActiveModal.NONE)
    var studioTabIndex by mutableIntStateOf(0)
    var showSpeedMenu by mutableStateOf(false)

    var eqBands by mutableStateOf(playerManager.getEqualizerBands())
    var bassBoostPercent by mutableIntStateOf(0)
    var activeEqPresetName by mutableStateOf("Mặc định")

    var loopPointA by mutableStateOf<Long?>(null)
    var loopPointB by mutableStateOf<Long?>(null)
    var isMirrorFlipped by mutableStateOf(false)
    var videoSaturation by mutableFloatStateOf(1.0f)
    var videoContrast by mutableFloatStateOf(1.0f)
    var videoBrightnessFilter by mutableFloatStateOf(0f)
    var currentScreenBrightness by mutableFloatStateOf(context.getSystemScreenBrightness())
    var subtitleSizeSp by mutableFloatStateOf(20f)
    var subtitleTextColor by mutableStateOf(Color.White)
    var showNerdStats by mutableStateOf(false)
    var nerdStats by mutableStateOf<VideoNerdStats?>(null)

    var zoomScale by mutableFloatStateOf(1.0f)
    var zoomOffsetX by mutableFloatStateOf(0f)
    var zoomOffsetY by mutableFloatStateOf(0f)

    var gestureMode by mutableStateOf(GestureMode.NONE)

    val isStudioEffectActive by derivedStateOf {
        loopPointA != null || isMirrorFlipped || audioBoostPercent > 0 || bassBoostPercent > 0 || abs(videoSaturation - 1f) > 0.05f
    }

    fun showTemporaryHint(text: String, durationMs: Long = 1400L) {
        gestureHint = text
        scope.launch {
            delay(durationMs)
            if (gestureMode == GestureMode.NONE) {
                gestureHint = null
            }
        }
    }

    fun resetZoom() {
        zoomScale = 1.0f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }

    fun resetVideoTransformAndLoop() {
        resetZoom()
        loopPointA = null
        loopPointB = null
    }

    fun persistCurrentPosition() {
        val vid = currentVideo ?: return
        val pos = playerManager.getCurrentPosition()
        val dur = playerManager.getDuration()
        val posToSave = if (dur > 5000L && pos >= dur - 2000L) 0L else pos
        CoroutineScope(Dispatchers.IO).launch {
            context.saveLastPosition(vid.id, posToSave)
        }
    }

    fun togglePlayPause() {
        if (isPlaying) {
            playerManager.pause()
            persistCurrentPosition()
        } else {
            playerManager.resume()
        }
        isPlaying = !isPlaying
    }

    fun toggleScreenOrientation(activity: Activity?) {
        val act = activity ?: return
        val isLandscape = act.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        act.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun triggerNextVideo() {
        if (hasNext) {
            persistCurrentPosition()
            resetVideoTransformAndLoop()
            val nextIdx = currentIndex + 1
            val nextVid = effectivePlaylist[nextIdx]
            scope.launch {
                val savedPos = context.readLastPosition(nextVid.id)
                val (_, subs) = playerManager.playVideoAtIndex(nextIdx, savedPos)
                currentVideo = nextVid
                loadedSubtitles = subs
                onVideoChangedCallback(nextVid)
                showTemporaryHint("⏭️ ${nextVid.displayName}")
            }
        }
    }

    fun triggerPreviousVideo() {
        if (hasPrevious) {
            persistCurrentPosition()
            resetVideoTransformAndLoop()
            val prevIdx = currentIndex - 1
            val prevVid = effectivePlaylist[prevIdx]
            scope.launch {
                val savedPos = context.readLastPosition(prevVid.id)
                val (_, subs) = playerManager.playVideoAtIndex(prevIdx, savedPos)
                currentVideo = prevVid
                loadedSubtitles = subs
                onVideoChangedCallback(prevVid)
                showTemporaryHint("⏮️ ${prevVid.displayName}")
            }
        }
    }

    fun takeVideoSnapshot() {
        val vid = currentVideo
        val pos = playerManager.getCurrentPosition()
        activeModal = ActiveModal.NONE
        gestureHint = "📸 Đang chụp khung hình gốc..."
        scope.launch {
            val res = VideoSnapshotHelper.captureCurrentFrame(context, vid?.path, pos, vid?.title ?: "Video")
            val msg = res.fold(
                onSuccess = { "✅ Đã lưu: $it" },
                onFailure = { "⚠️ Lỗi: ${it.message}" }
            )
            showTemporaryHint(msg, 2000L)
        }
    }

    fun enterPip(activity: Activity?) {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                persistCurrentPosition()
                resetZoom()
                showControls = false
                activeModal = ActiveModal.NONE
                act.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
                isInPipMode = true
            } catch (_: Exception) {}
        }
    }
}