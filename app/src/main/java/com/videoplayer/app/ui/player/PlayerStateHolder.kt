package com.videoplayer.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.AudioManager
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.media3.ui.AspectRatioFrameLayout
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.readPreferredBrightness
import com.videoplayer.app.data.local.readSubtitleAppearance
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.local.savePreferredBrightness
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.EqBandInfo
import com.videoplayer.app.media.ExternalSubtitleItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.media.VideoNerdStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class ActiveModal { NONE, STUDIO, TOOLS }
enum class GestureMode { NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM }

class PlayerStateHolder(
    val context: Context,
    val scope: CoroutineScope,
    val playerManager: PlayerManager,
    val initialVideo: VideoItem?,
    val playlist: List<VideoItem>,
    val streamUrl: String?,
    val onVideoChanged: (VideoItem) -> Unit,
    val onBack: () -> Unit
) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val effectivePlaylist = if (playlist.isNotEmpty()) playlist else listOfNotNull(initialVideo)

    var currentVideo by mutableStateOf(initialVideo)
    val currentIndex by derivedStateOf {
        if (currentVideo == null) -1 else effectivePlaylist.indexOfFirst { it.id == currentVideo!!.id }
    }

    var isPlaying by mutableStateOf(true)
    var currentPos by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(1L)
    var isDraggingSlider by mutableStateOf(false)
    var lastSeekCompletedTimeMs by mutableLongStateOf(0L)

    var playbackSpeed by mutableFloatStateOf(1.0f)
    var isLocked by mutableStateOf(false)
    var showControls by mutableStateOf(true)
    var activeModal by mutableStateOf(ActiveModal.NONE)
    var showSpeedMenu by mutableStateOf(false)
    var gestureHint by mutableStateOf<String?>(null)
    var gestureMode by mutableStateOf(GestureMode.NONE)

    var currentScreenBrightness by mutableFloatStateOf(0.5f)
    var audioBoostPercent by mutableIntStateOf(0)
    var backgroundAudioEnabled by mutableStateOf(false)
    var sleepTimerMinutes by mutableIntStateOf(0)
    var sleepRemainingSec by mutableIntStateOf(0)

    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    var zoomScale by mutableFloatStateOf(1.0f)
    var zoomOffsetX by mutableFloatStateOf(0f)
    var zoomOffsetY by mutableFloatStateOf(0f)
    var isMirrorFlipped by mutableStateOf(false)

    var videoSaturation by mutableFloatStateOf(1.0f)
    var videoContrast by mutableFloatStateOf(1.0f)
    var videoBrightnessFilter by mutableFloatStateOf(0f)

    var subtitlesEnabled by mutableStateOf(true)
    var loadedSubtitles by mutableStateOf<List<ExternalSubtitleItem>>(emptyList())
    var subtitleSizeSp by mutableFloatStateOf(20f)
    var subtitleTextColor by mutableStateOf(Color.White)

    var loopPointA by mutableStateOf<Long?>(null)
    var loopPointB by mutableStateOf<Long?>(null)

    var showNerdStats by mutableStateOf(false)
    var nerdStats by mutableStateOf<VideoNerdStats?>(null)
    var eqBands by mutableStateOf(emptyList<EqBandInfo>())
    var bassBoostPercent by mutableIntStateOf(0)
    var activeEqPresetName by mutableStateOf("Mặc định")

    private var syncJob: Job? = null
    private var sleepJob: Job? = null

    fun initialize(activity: Activity?) {
        scope.launch {
            currentScreenBrightness = context.readPreferredBrightness()
            activity?.window?.attributes?.let {
                it.screenBrightness = currentScreenBrightness
                activity.window.attributes = it
            }
            val (savedSubSize, savedSubCol) = context.readSubtitleAppearance()
            subtitleSizeSp = savedSubSize
            subtitleTextColor = Color(savedSubCol)

            if (initialVideo != null && effectivePlaylist.isNotEmpty()) {
                val startIdx = effectivePlaylist.indexOfFirst { it.id == initialVideo.id }.coerceAtLeast(0)
                val startVid = effectivePlaylist[startIdx]
                currentVideo = startVid
                onVideoChanged(startVid)
                val savedPos = context.readLastPosition(startVid.id)
                loadedSubtitles = playerManager.playPlaylist(effectivePlaylist, startIdx, savedPos)
                currentPos = savedPos
                eqBands = playerManager.getEqualizerBands()
            } else if (!streamUrl.isNullOrEmpty()) {
                playerManager.playStream(streamUrl)
            }
        }
        startSyncLoop()
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch {
            var tick = 0
            while (isActive) {
                isPlaying = playerManager.isPlaying()
                val pos = playerManager.getCurrentPosition()
                val now = System.currentTimeMillis()
                if (!isDraggingSlider && gestureMode != GestureMode.SEEK && (now - lastSeekCompletedTimeMs > 500L)) {
                    currentPos = pos
                }
                duration = playerManager.getDuration().coerceAtLeast(1L)

                val ptA = loopPointA
                val ptB = loopPointB
                if (ptA != null && ptB != null && ptB > ptA + 500L && pos >= ptB) {
                    playerManager.seekToFast(ptA)
                    if (!isDraggingSlider) currentPos = ptA
                }

                if (showNerdStats) {
                    nerdStats = playerManager.getNerdStats()
                }

                tick++
                val activeVid = currentVideo
                if (activeVid != null && isPlaying && !isDraggingSlider && tick % 4 == 0) {
                    context.saveLastPosition(activeVid.id, pos)
                }
                delay(300)
            }
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerMinutes = minutes
        sleepJob?.cancel()
        if (minutes > 0) {
            sleepRemainingSec = minutes * 60
            sleepJob = scope.launch {
                while (sleepRemainingSec > 0) {
                    delay(1000)
                    sleepRemainingSec--
                }
                playerManager.pause()
                persistCurrentPosition()
                sleepTimerMinutes = 0
                gestureHint = "💤 Đã tạm dừng theo Hẹn giờ ngủ"
            }
        } else {
            sleepRemainingSec = 0
        }
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

    fun triggerNextVideo() {
        if (effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1) {
            persistCurrentPosition()
            resetTransformAndLoop()
            val nextIdx = currentIndex + 1
            val nextVid = effectivePlaylist[nextIdx]
            scope.launch {
                val savedPos = context.readLastPosition(nextVid.id)
                val (_, subs) = playerManager.playVideoAtIndex(nextIdx, savedPos)
                currentVideo = nextVid
                loadedSubtitles = subs
                onVideoChanged(nextVid)
                gestureHint = "⏭️ ${nextVid.displayName}"
                delay(1400)
                if (gestureMode == GestureMode.NONE) gestureHint = null
            }
        }
    }

    fun triggerPreviousVideo() {
        if (effectivePlaylist.isNotEmpty() && currentIndex > 0) {
            persistCurrentPosition()
            resetTransformAndLoop()
            val prevIdx = currentIndex - 1
            val prevVid = effectivePlaylist[prevIdx]
            scope.launch {
                val savedPos = context.readLastPosition(prevVid.id)
                val (_, subs) = playerManager.playVideoAtIndex(prevIdx, savedPos)
                currentVideo = prevVid
                loadedSubtitles = subs
                onVideoChanged(prevVid)
                gestureHint = "⏮️ ${prevVid.displayName}"
                delay(1400)
                if (gestureMode == GestureMode.NONE) gestureHint = null
            }
        }
    }

    fun resetTransformAndLoop() {
        zoomScale = 1.0f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
        loopPointA = null
        loopPointB = null
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

    fun buildVideoColorPaint(): Paint? {
        if (abs(videoSaturation - 1f) < 0.02f && abs(videoContrast - 1f) < 0.02f && abs(videoBrightnessFilter) < 1f) {
            return null
        }
        val satMatrix = ColorMatrix().apply { setSaturation(videoSaturation) }
        val t = (1f - videoContrast) * 128f + videoBrightnessFilter
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                videoContrast, 0f, 0f, 0f, t,
                0f, videoContrast, 0f, 0f, t,
                0f, 0f, videoContrast, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        )
        satMatrix.postConcat(contrastMatrix)
        return Paint().apply { colorFilter = ColorMatrixColorFilter(satMatrix) }
    }

    fun release() {
        syncJob?.cancel()
        sleepJob?.cancel()
        persistCurrentPosition()
        playerManager.release()
    }
}