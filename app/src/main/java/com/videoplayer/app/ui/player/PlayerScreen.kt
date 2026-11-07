package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Rational
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.ExternalSubtitleItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.media.VideoNerdStats
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.VideoSnapshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode {
    NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
    if (result == null) {
        result = uri.path?.substringAfterLast('/') ?: "subtitle.srt"
    }
    return result!!
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun buildVideoColorPaint(saturation: Float, contrast: Float, brightnessOffset: Float): Paint? {
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
    return Paint().apply {
        colorFilter = ColorMatrixColorFilter(satMatrix)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    initialVideo: VideoItem?,
    playlist: List<VideoItem>,
    streamUrl: String?,
    onVideoChanged: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { PlayerManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val effectivePlaylist = remember(playlist, initialVideo) {
        if (playlist.isNotEmpty()) playlist else listOfNotNull(initialVideo)
    }

    var currentVideo by remember { mutableStateOf(initialVideo) }
    val currentIndex = remember(currentVideo, effectivePlaylist) {
        if (currentVideo == null) -1 else effectivePlaylist.indexOfFirst { it.id == currentVideo!!.id }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var loadedSubtitles by remember { mutableStateOf<List<ExternalSubtitleItem>>(emptyList()) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var isInPipMode by remember { mutableStateOf(false) }

    var audioBoostPercent by remember { mutableIntStateOf(0) }
    var backgroundAudioEnabled by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepRemainingSec by remember { mutableIntStateOf(0) }

    // Menus & Panels
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var showEqualizerPanel by remember { mutableStateOf(false) }
    var showProStudioPanel by remember { mutableStateOf(false) }

    // Equalizer States
    var eqBands by remember { mutableStateOf(playerManager.getEqualizerBands()) }
    var bassBoostPercent by remember { mutableIntStateOf(0) }
    var activeEqPresetName by remember { mutableStateOf("Mặc định") }

    // ================= PRO STUDIO STATES (A-B Repeat, Mirror, Color Filter, Sub Style, Nerd Stats) =================
    var loopPointA by remember { mutableStateOf<Long?>(null) }
    var loopPointB by remember { mutableStateOf<Long?>(null) }
    var isMirrorFlipped by remember { mutableStateOf(false) }
    var videoSaturation by remember { mutableFloatStateOf(1.0f) } // 0f..2f
    var videoContrast by remember { mutableFloatStateOf(1.0f) }   // 0.5f..1.8f
    var videoBrightnessFilter by remember { mutableFloatStateOf(0f) } // -50f..50f
    var subtitleSizeSp by remember { mutableFloatStateOf(20f) }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var showNerdStats by remember { mutableStateOf(false) }
    var nerdStats by remember { mutableStateOf<VideoNerdStats?>(null) }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }

    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val vid = currentVideo
        if (uri != null && vid != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val fileName = context.resolveUriFileName(uri)
            loadedSubtitles = playerManager.attachExternalSubtitleToCurrent(vid, uri, fileName)
            subtitlesEnabled = true
            gestureHint = "💬 Đã nạp phụ đề: $fileName"
            scope.launch { delay(1800); gestureHint = null }
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

    fun persistPositionFor(vid: VideoItem?, pos: Long, dur: Long) {
        if (vid == null) return
        val posToSave = if (dur > 5000L && pos >= dur - 2000L) 0L else pos
        CoroutineScope(Dispatchers.IO).launch {
            context.saveLastPosition(vid.id, posToSave)
        }
    }

    fun persistCurrentPosition() {
        persistPositionFor(currentVideo, playerManager.getCurrentPosition(), playerManager.getDuration())
    }

    fun triggerNextVideo() {
        if (effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1) {
            persistCurrentPosition()
            resetVideoTransformAndLoop()
            val nextVid = effectivePlaylist[currentIndex + 1]
            scope.launch {
                val savedPos = context.readLastPosition(nextVid.id)
                playerManager.playNextInPlaylist(savedPos)
                currentVideo = nextVid
                loadedSubtitles = playerManager.getSubtitlesForVideo(nextVid)
                onVideoChanged(nextVid)
                gestureHint = "⏭️ ${nextVid.displayName}"
                delay(1500)
                if (gestureMode == GestureMode.NONE) gestureHint = null
            }
        }
    }

    fun triggerPreviousVideo() {
        if (effectivePlaylist.isNotEmpty() && currentIndex > 0) {
            persistCurrentPosition()
            resetVideoTransformAndLoop()
            val prevVid = effectivePlaylist[currentIndex - 1]
            scope.launch {
                val savedPos = context.readLastPosition(prevVid.id)
                playerManager.playPreviousInPlaylist(savedPos)
                currentVideo = prevVid
                loadedSubtitles = playerManager.getSubtitlesForVideo(prevVid)
                onVideoChanged(prevVid)
                gestureHint = "⏮️ ${prevVid.displayName}"
                delay(1500)
                if (gestureMode == GestureMode.NONE) gestureHint = null
            }
        }
    }

    fun takeVideoSnapshot() {
        val vid = currentVideo
        val pos = playerManager.getCurrentPosition()
        gestureHint = "📸 Đang trích xuất khung hình gốc..."
        scope.launch {
            val res = VideoSnapshotHelper.captureCurrentFrame(
                context = context,
                videoPath = vid?.path,
                positionMs = pos,
                videoTitle = vid?.title ?: "Video"
            )
            res.onSuccess { savedPath ->
                gestureHint = "✅ Đã lưu ảnh vào: $savedPath"
            }.onFailure { err ->
                gestureHint = "⚠️ Lỗi chụp ảnh: ${err.message}"
            }
            delay(2200)
            if (gestureMode == GestureMode.NONE) gestureHint = null
        }
    }

    fun enterPip() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                persistCurrentPosition()
                resetZoom()
                showControls = false
                showEqualizerPanel = false
                showProStudioPanel = false
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                act.enterPictureInPictureMode(params)
                isInPipMode = true
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialVideo != null && effectivePlaylist.isNotEmpty()) {
            val startIdx = effectivePlaylist.indexOfFirst { it.id == initialVideo.id }.coerceAtLeast(0)
            val startVid = effectivePlaylist[startIdx]
            currentVideo = startVid
            onVideoChanged(startVid)
            val savedPos = context.readLastPosition(startVid.id)
            val subs = playerManager.playPlaylist(effectivePlaylist, startIdx, savedPos)
            loadedSubtitles = subs
            currentPos = savedPos
            delay(300)
            eqBands = playerManager.getEqualizerBands()
            if (subs.isNotEmpty()) {
                gestureHint = "💬 Tự động nạp phụ đề: ${subs.first().name}"
                scope.launch { delay(1800); if (gestureMode == GestureMode.NONE) gestureHint = null }
            }
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
            loadedSubtitles = emptyList()
        }
    }

    DisposableEffect(playerManager, effectivePlaylist) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    if (videoSize.width > videoSize.height) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentVideo?.let { finishedVid ->
                        CoroutineScope(Dispatchers.IO).launch {
                            context.saveLastPosition(finishedVid.id, 0L)
                        }
                    }
                    val nextIdx = playerManager.currentIndex + 1
                    if (nextIdx in effectivePlaylist.indices) {
                        val nextVid = effectivePlaylist[nextIdx]
                        resetVideoTransformAndLoop()
                        scope.launch {
                            val savedPos = context.readLastPosition(nextVid.id)
                            playerManager.playVideoAtIndex(nextIdx, savedPos)
                            currentVideo = nextVid
                            loadedSubtitles = playerManager.getSubtitlesForVideo(nextVid)
                            onVideoChanged(nextVid)
                            gestureHint = "⏭️ Tự phát tiếp: ${nextVid.displayName}"
                            delay(1800)
                            if (gestureMode == GestureMode.NONE) gestureHint = null
                        }
                    }
                }
            }
        }
        playerManager.player.addListener(listener)
        onDispose {
            playerManager.player.removeListener(listener)
        }
    }

    BackHandler {
        when {
            showProStudioPanel -> showProStudioPanel = false
            showEqualizerPanel -> showEqualizerPanel = false
            else -> {
                persistCurrentPosition()
                onBack()
            }
        }
    }

    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            sleepRemainingSec = sleepTimerMinutes * 60
            while (sleepRemainingSec > 0) {
                delay(1000)
                sleepRemainingSec--
            }
            playerManager.pause()
            persistCurrentPosition()
            sleepTimerMinutes = 0
            gestureHint = "💤 Đã tạm dừng theo Hẹn giờ ngủ"
        } else {
            sleepRemainingSec = 0
        }
    }

    // Vòng lặp chính: Cập nhật vị trí, kiểm tra Lặp đoạn A-B (A-B Repeat) & Cập nhật Nerd Stats
    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                isInPipMode = activity.isInPictureInPictureMode
            }
            val pos = playerManager.getCurrentPosition()
            if (gestureMode != GestureMode.SEEK) {
                currentPos = pos
            }
            duration = playerManager.getDuration().coerceAtLeast(1L)

            // Xử lý Lặp đoạn A-B (A-B Repeat)
            val ptA = loopPointA
            val ptB = loopPointB
            if (ptA != null && ptB != null && ptB > ptA + 500L && pos >= ptB) {
                playerManager.seekTo(ptA)
                currentPos = ptA
            }

            if (showNerdStats) {
                nerdStats = playerManager.getNerdStats()
            }

            val activeVid = currentVideo
            if (activeVid != null && isPlaying) {
                context.saveLastPosition(activeVid.id, currentPos)
            }
            delay(350)
        }
    }

    DisposableEffect(lifecycleOwner, backgroundAudioEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false
            isInPipMode = inPip

            when (event) {
                Lifecycle.Event.ON_PAUSE -> persistCurrentPosition()
                Lifecycle.Event.ON_STOP -> {
                    persistCurrentPosition()
                    if (!inPip && !backgroundAudioEnabled) {
                        playerManager.pause()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            persistCurrentPosition()
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(
                    com.videoplayer.app.R.layout.exo_texture_player_view,
                    null
                ) as PlayerView).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    player = playerManager.player
                    useController = false
                    setResizeMode(resizeMode)
                }
            },
            update = { view ->
                view.keepScreenOn = isPlaying
                view.resizeMode = resizeMode

                // Áp dụng bộ lọc màu Video (Saturation, Contrast, Brightness)
                val colorPaint = buildVideoColorPaint(videoSaturation, videoContrast, videoBrightnessFilter)
                if (colorPaint != null) {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, colorPaint)
                } else {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }

                // Áp dụng Cỡ chữ & Màu chữ Phụ đề
                view.subtitleView?.apply {
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSizeSp)
                    setStyle(
                        CaptionStyleCompat(
                            subtitleTextColor.toArgb(),
                            android.graphics.Color.argb(140, 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = if (isMirrorFlipped) -zoomScale else zoomScale,
                    scaleY = zoomScale,
                    translationX = zoomOffsetX,
                    translationY = zoomOffsetY
                )
                .pointerInput(isLocked, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            when {
                                showProStudioPanel -> showProStudioPanel = false
                                showEqualizerPanel -> showEqualizerPanel = false
                                else -> showControls = !showControls
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isLocked) {
                                val width = size.width
                                val cur = playerManager.getCurrentPosition()
                                val dur = playerManager.getDuration().coerceAtLeast(1L)
                                if (offset.x < width / 2f) {
                                    val next = (cur - 10_000L).coerceAtLeast(0L)
                                    playerManager.seekTo(next)
                                    currentPos = next
                                    gestureHint = "⏪ -10 giây (${formatTimeMs(next)})"
                                } else {
                                    val next = (cur + 10_000L).coerceAtMost(dur)
                                    playerManager.seekTo(next)
                                    currentPos = next
                                    gestureHint = "⏩ +10 giây (${formatTimeMs(next)})"
                                }
                                persistCurrentPosition()
                                scope.launch { delay(800); if (gestureMode == GestureMode.NONE) gestureHint = null }
                            }
                        },
                        onLongPress = {
                            if (!isLocked) {
                                playerManager.setPlaybackSpeed(2.0f)
                                gestureHint = "⚡ Tốc độ 2.0x"
                                scope.launch { delay(1200); if (gestureMode == GestureMode.NONE) gestureHint = null }
                            }
                        }
                    )
                }
                .pointerInput(isLocked, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        if (isLocked) return@awaitEachGesture

                        gestureMode = GestureMode.NONE
                        val startTouchX = firstDown.position.x
                        var totalDragX = 0f
                        var totalDragY = 0f

                        val initialSeekPos = playerManager.getCurrentPosition()
                        var targetSeekPos = initialSeekPos

                        val winBright = activity?.window?.attributes?.screenBrightness ?: -1f
                        val initialBrightness = if (winBright in 0.01f..1.0f) winBright else 0.5f

                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val basePercent = (curVol.toFloat() / maxVol) * 100f
                        val initialCombinedVol = if (basePercent >= 99f && audioBoostPercent > 0) {
                            100f + audioBoostPercent
                        } else {
                            basePercent
                        }

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }

                            if (pressedCount >= 2) {
                                gestureMode = GestureMode.ZOOM
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (zoomChange != 1f || panChange.x != 0f || panChange.y != 0f) {
                                    val newScale = (zoomScale * zoomChange).coerceIn(1.0f, 4.0f)
                                    zoomScale = newScale
                                    if (newScale > 1.01f) {
                                        val maxDx = (size.width * (newScale - 1f)) / 2f
                                        val maxDy = (size.height * (newScale - 1f)) / 2f
                                        zoomOffsetX = (zoomOffsetX + panChange.x * newScale).coerceIn(-maxDx, maxDx)
                                        zoomOffsetY = (zoomOffsetY + panChange.y * newScale).coerceIn(-maxDy, maxDy)
                                    } else {
                                        zoomOffsetX = 0f
                                        zoomOffsetY = 0f
                                    }
                                    gestureHint = "🔍 Thu phóng: ${(zoomScale * 100).roundToInt()}%"
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pressedCount == 1 && gestureMode != GestureMode.ZOOM) {
                                val change = event.changes.firstOrNull { it.pressed } ?: continue
                                val dragX = change.position.x - change.previousPosition.x
                                val dragY = change.position.y - change.previousPosition.y

                                if (dragX != 0f || dragY != 0f) {
                                    val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                                    val screenHeight = size.height.toFloat().coerceAtLeast(1f)

                                    totalDragX += dragX
                                    totalDragY += dragY

                                    if (gestureMode == GestureMode.NONE) {
                                        val threshold = 18f
                                        if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                                            gestureMode = if (abs(totalDragX) > abs(totalDragY) * 1.2f) {
                                                GestureMode.SEEK
                                            } else {
                                                if (startTouchX < screenWidth / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                                            }
                                            totalDragX = 0f
                                            totalDragY = 0f
                                        }
                                    }

                                    when (gestureMode) {
                                        GestureMode.SEEK -> {
                                            change.consume()
                                            val dur = playerManager.getDuration().coerceAtLeast(1L)
                                            val maxSeekRange = minOf(dur, 120_000L).coerceAtLeast(30_000L)
                                            val seekDelta = ((totalDragX / screenWidth) * maxSeekRange).toLong()
                                            targetSeekPos = (initialSeekPos + seekDelta).coerceIn(0L, dur)
                                            currentPos = targetSeekPos
                                            val diffSec = (targetSeekPos - initialSeekPos) / 1000
                                            val sign = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                            gestureHint = "⏱️ ${formatTimeMs(targetSeekPos)} / ${formatTimeMs(dur)} ($sign)"
                                        }
                                        GestureMode.BRIGHTNESS -> {
                                            change.consume()
                                            val act = activity
                                            if (act != null) {
                                                val window = act.window
                                                val attrs = window.attributes
                                                val deltaBright = -totalDragY / (screenHeight * 0.75f)
                                                val newBright = (initialBrightness + deltaBright).coerceIn(0.02f, 1.0f)
                                                attrs.screenBrightness = newBright
                                                window.attributes = attrs
                                                gestureHint = "☀️ Độ sáng: ${(newBright * 100).roundToInt()}%"
                                            }
                                        }
                                        GestureMode.VOLUME -> {
                                            change.consume()
                                            val deltaPercent = (-totalDragY / (screenHeight * 0.75f)) * 150f
                                            val newCombined = (initialCombinedVol + deltaPercent).coerceIn(0f, 200f)

                                            if (newCombined <= 100f) {
                                                audioBoostPercent = 0
                                                playerManager.setAudioBoostPercent(0)
                                                val targetSysVol = ((newCombined / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSysVol, 0)
                                                gestureHint = "🔊 Âm lượng: ${newCombined.roundToInt()}%"
                                            } else {
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                                                val boost = (newCombined - 100f).roundToInt().coerceIn(1, 100)
                                                audioBoostPercent = boost
                                                playerManager.setAudioBoostPercent(boost)
                                                gestureHint = "🚀 Khuếch đại: ${100 + boost}% (+${(boost * 0.15f).toInt()}dB)"
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (gestureMode == GestureMode.SEEK) {
                            playerManager.seekTo(targetSeekPos)
                            currentPos = targetSeekPos
                            persistCurrentPosition()
                        }
                        gestureMode = GestureMode.NONE
                        gestureHint = null
                    }
                }
        )

        // ================= NERD STATS HUD (GÓC TRÊN TRÁI) =================
        if (showNerdStats && !isInPipMode) {
            nerdStats?.let { st ->
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .widthIn(max = 260.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📊 NERD STATS", color = palette.primaryGlow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("✕", color = Color.White, modifier = Modifier.padding(2.dp).pointerInput(Unit) {
                                detectTapGestures { showNerdStats = false }
                            })
                        }
                        Text("• Độ phân giải: ${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp)
                        Text("• Giải mã Video: ${st.videoCodec} • ${st.videoBitrate}", color = Color.White, fontSize = 11.sp)
                        Text("• Kênh Audio: ${st.audioCodec}", color = Color.White, fontSize = 11.sp)
                        Text("• Dropped Frames: ${st.droppedFrames}", color = if (st.droppedFrames > 5) Color(0xFFFF5C8A) else Color(0xFF80FFDB), fontSize = 11.sp)
                    }
                }
            }
        }

        // Hiển thị thông báo Gesture dạng Viên nang Apple Glass
        if (!isInPipMode) {
            gestureHint?.let { hint ->
                LiquidGlassPill(
                    active = true,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = hint,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ================= BẢNG KÍNH LỎNG "✨ PRO STUDIO" (A-B REPEAT, FRAME STEP, MIRROR, COLOR FILTER, SUB STYLE, NERD STATS) =================
        if (showProStudioPanel && !isInPipMode) {
            LiquidGlassCard(
                cornerRadius = 28.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 18.dp)
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.primaryGlow)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Pro Studio • Hình Ảnh & Lặp Đoạn",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LiquidGlassPill(onClick = { showProStudioPanel = false }) {
                            Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // 1. Lặp đoạn A-B & Nhích từng khung hình (±1 Frame) & Lật gương
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassPill(
                            active = loopPointA != null,
                            onClick = {
                                loopPointA = playerManager.getCurrentPosition()
                                if (loopPointB != null && loopPointB!! <= loopPointA!!) loopPointB = null
                                gestureHint = "🅰️ Đã đặt điểm A: ${formatTimeMs(loopPointA!!)}"
                                scope.launch { delay(1200); gestureHint = null }
                            }
                        ) {
                            Text(
                                if (loopPointA != null) "A: ${formatTimeMs(loopPointA!!)}" else "Đặt điểm A",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        LiquidGlassPill(
                            active = loopPointB != null,
                            onClick = {
                                val now = playerManager.getCurrentPosition()
                                if (loopPointA == null) loopPointA = 0L
                                if (now > loopPointA!! + 500L) {
                                    loopPointB = now
                                    gestureHint = "🔁 Đang lặp đoạn A-B (${formatTimeMs(loopPointA!!)} -> ${formatTimeMs(now)})"
                                } else {
                                    gestureHint = "⚠️ Điểm B phải sau điểm A!"
                                }
                                scope.launch { delay(1400); gestureHint = null }
                            }
                        ) {
                            Text(
                                if (loopPointB != null) "B: ${formatTimeMs(loopPointB!!)}" else "Đặt điểm B",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (loopPointA != null || loopPointB != null) {
                            LiquidGlassPill(onClick = {
                                loopPointA = null
                                loopPointB = null
                            }) {
                                Text("Xóa lặp A-B", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Nhích lùi/tiến 1 khung hình (Frame Step)
                        LiquidGlassPill(onClick = {
                            playerManager.stepFrame(forward = false)
                            isPlaying = false
                            currentPos = playerManager.getCurrentPosition()
                        }) {
                            Text("⏮ -1 Frame", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        LiquidGlassPill(onClick = {
                            playerManager.stepFrame(forward = true)
                            isPlaying = false
                            currentPos = playerManager.getCurrentPosition()
                        }) {
                            Text("+1 Frame ⏭", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // 2. Lật gương Video (Mirror) & Bật thông số Nerd Stats
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassPill(
                            active = isMirrorFlipped,
                            onClick = { isMirrorFlipped = !isMirrorFlipped }
                        ) {
                            Icon(Icons.Default.Flip, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isMirrorFlipped) "Đang Lật Gương ✓" else "Lật Gương Trái-Phải", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        LiquidGlassPill(
                            active = showNerdStats,
                            onClick = {
                                showNerdStats = !showNerdStats
                                if (showNerdStats) nerdStats = playerManager.getNerdStats()
                            }
                        ) {
                            Icon(Icons.Default.Analytics, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Thông số Nerd Stats", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }

                        LiquidGlassPill(onClick = {
                            videoSaturation = 1.0f
                            videoContrast = 1.0f
                            videoBrightnessFilter = 0f
                        }) {
                            Text("Đặt lại Màu gốc", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // 3. Thanh trượt Tinh chỉnh Màu sắc Video (Saturation & Contrast)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Độ tươi màu", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                        Slider(
                            value = videoSaturation,
                            onValueChange = { videoSaturation = it },
                            valueRange = 0f..2.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                        )
                        Text("${(videoSaturation * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Độ tương phản", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                        Slider(
                            value = videoContrast,
                            onValueChange = { videoContrast = it },
                            valueRange = 0.6f..1.8f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                        )
                        Text("${(videoContrast * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                    }

                    // 4. Tùy chỉnh Cỡ chữ & Màu chữ Phụ đề
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Phụ đề:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        listOf(16f to "Nhỏ", 20f to "Vừa", 26f to "Lớn", 32f to "Siêu lớn").forEach { (sz, lbl) ->
                            LiquidGlassPill(
                                active = subtitleSizeSp == sz,
                                onClick = { subtitleSizeSp = sz }
                            ) {
                                Text(lbl, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        listOf(
                            Color.White to "Trắng",
                            Color(0xFFFFEA00) to "Vàng",
                            Color(0xFF00F0FF) to "Cyan"
                        ).forEach { (col, name) ->
                            LiquidGlassPill(
                                active = subtitleTextColor == col,
                                onClick = { subtitleTextColor = col }
                            ) {
                                Text(name, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ================= BẢNG KÍNH LỎNG 5-BAND EQUALIZER & BASS BOOST =================
        if (showEqualizerPanel && !isInPipMode) {
            LiquidGlassCard(
                cornerRadius = 28.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = palette.primaryGlow)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Bộ Chỉnh Âm 5 Băng Tần & Siêu Trầm",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LiquidGlassPill(onClick = { showEqualizerPanel = false }) {
                            Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    val presets = listOf(
                        Triple("Mặc định", listOf(0f, 0f, 0f, 0f, 0f), 0),
                        Triple("Siêu Trầm", listOf(8f, 5f, 0f, 2f, 4f), 75),
                        Triple("Rạp Phim", listOf(6f, 3f, -1f, 4f, 6f), 50),
                        Triple("Giọng Nói", listOf(-3f, 2f, 7f, 5f, 1f), 0),
                        Triple("Sôi Động", listOf(5f, 3f, -2f, 3f, 6f), 40)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { (name, levels, bass) ->
                            LiquidGlassPill(
                                active = activeEqPresetName == name,
                                onClick = {
                                    activeEqPresetName = name
                                    playerManager.applyEqPreset(levels, bass)
                                    bassBoostPercent = bass
                                    eqBands = playerManager.getEqualizerBands()
                                }
                            ) {
                                Text(name, color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    eqBands.forEach { band ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = band.label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(56.dp)
                            )
                            Slider(
                                value = band.levelDb,
                                onValueChange = { newDb ->
                                    activeEqPresetName = "Tùy chỉnh"
                                    playerManager.setEqBandLevel(band.index, newDb)
                                    eqBands = playerManager.getEqualizerBands()
                                },
                                valueRange = -15f..15f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = palette.primaryGlow,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            Text(
                                text = "%+.0fdB".format(band.levelDb),
                                color = palette.primaryGlow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Siêu trầm",
                            color = Color(0xFFFFD54F),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = bassBoostPercent.toFloat(),
                            onValueChange = {
                                bassBoostPercent = it.roundToInt()
                                playerManager.setBassBoostPercent(bassBoostPercent)
                            },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFD54F),
                                activeTrackColor = Color(0xFFFFD54F),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Text(
                            text = "$bassBoostPercent%",
                            color = Color(0xFFFFD54F),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }
        }

        if (isLocked && showControls && !isInPipMode) {
            LiquidGlassPill(
                active = true,
                onClick = { isLocked = false },
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Mở khóa", tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Chạm để Mở khóa", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ================= APPLE VISION-OS FLOATING GLASS PLAYER HUD =================
        if (showControls && !isLocked && !isInPipMode) {
            LiquidGlassCard(
                cornerRadius = 32.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentVideo?.displayName ?: (streamUrl ?: "Đang phát"),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (loopPointA != null || loopPointB != null) {
                            LiquidGlassPill(
                                active = true,
                                onClick = { loopPointA = null; loopPointB = null },
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    "🔁 A-B ✕",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (effectivePlaylist.size > 1 && currentIndex >= 0) {
                            LiquidGlassPill(
                                active = false,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    "Tập ${currentIndex + 1}/${effectivePlaylist.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (zoomScale > 1.01f) {
                            LiquidGlassPill(
                                active = true,
                                onClick = { resetZoom() },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text("🔍 ${(zoomScale * 100).roundToInt()}% ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Slider(
                        value = currentPos.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            currentPos = it.toLong()
                            playerManager.seekTo(currentPos)
                        },
                        onValueChangeFinished = { persistCurrentPosition() },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = palette.primaryGlow,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTimeMs(currentPos), color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        if (sleepRemainingSec > 0) {
                            Text("💤 Tắt sau ${formatTimeMs(sleepRemainingSec * 1000L)}", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatTimeMs(duration), color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { persistCurrentPosition(); onBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                        }
                        IconButton(
                            onClick = { triggerPreviousVideo() },
                            enabled = currentIndex > 0
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Video trước",
                                tint = if (currentIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .drawWithCache {
                                    val r = size.width / 2f
                                    val fill = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            palette.primaryGlow.copy(alpha = 0.75f),
                                            palette.secondaryGlow.copy(alpha = 0.75f)
                                        )
                                    )
                                    val rim = Brush.linearGradient(
                                        colors = listOf(Color.White, Color.White.copy(alpha = 0.3f))
                                    )
                                    onDrawBehind {
                                        drawCircle(brush = fill, radius = r)
                                        drawCircle(brush = rim, radius = r - 1.dp.toPx(), style = Stroke(width = 1.4.dp.toPx()))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = {
                                if (isPlaying) {
                                    playerManager.pause()
                                    persistCurrentPosition()
                                } else {
                                    playerManager.resume()
                                }
                                isPlaying = !isPlaying
                            }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Phát/Tạm dừng",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { triggerNextVideo() },
                            enabled = effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Video tiếp theo",
                                tint = if (effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        // NÚT MỞ BẢNG "✨ PRO STUDIO" (Gom gọn A-B Repeat, Frame Step, Mirror, Color Filter, Sub Style, Nerd Stats)
                        LiquidGlassPill(
                            active = showProStudioPanel || loopPointA != null || isMirrorFlipped || abs(videoSaturation - 1f) > 0.05f,
                            onClick = {
                                showEqualizerPanel = false
                                showProStudioPanel = !showProStudioPanel
                            }
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Studio", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // 1. MENU CHỌN TỐC ĐỘ PHÁT
                        Box {
                            LiquidGlassPill(
                                active = playbackSpeed != 1.0f,
                                onClick = { showSpeedMenu = true }
                            ) {
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
                                speedOptions.forEach { spd ->
                                    DropdownMenuItem(
                                        text = {
                                            val label = if (spd == 1.0f) "1.0x (Chuẩn)" else "${spd}x"
                                            Text(
                                                text = if (playbackSpeed == spd) "✓ $label" else "    $label",
                                                fontWeight = if (playbackSpeed == spd) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            playbackSpeed = spd
                                            playerManager.setPlaybackSpeed(spd)
                                            showSpeedMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // 2. NÚT BỘ CHỈNH ÂM 5 BĂNG TẦN (EQUALIZER)
                        IconButton(onClick = {
                            showProStudioPanel = false
                            eqBands = playerManager.getEqualizerBands()
                            showEqualizerPanel = !showEqualizerPanel
                        }) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = if (showEqualizerPanel || bassBoostPercent > 0 || activeEqPresetName != "Mặc định") palette.primaryGlow else Color.White
                            )
                        }

                        // 3. NÚT CHỤP ẢNH NHANH KHUNG HÌNH VIDEO (SNAPSHOT)
                        IconButton(onClick = { takeVideoSnapshot() }) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Chụp ảnh khung hình",
                                tint = Color.White
                            )
                        }

                        // 4. MENU CHỌN MỨC ZOOM VIDEO
                        Box {
                            IconButton(onClick = { showZoomMenu = true }) {
                                Icon(
                                    Icons.Default.ZoomIn,
                                    contentDescription = "Thu phóng video",
                                    tint = if (zoomScale > 1.01f) palette.primaryGlow else Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showZoomMenu,
                                onDismissRequest = { showZoomMenu = false }
                            ) {
                                val zoomPresets = listOf(1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
                                zoomPresets.forEach { z ->
                                    DropdownMenuItem(
                                        text = {
                                            val pct = (z * 100).roundToInt()
                                            val label = if (z == 1.0f) "100% (Mặc định)" else "$pct%"
                                            Text(
                                                text = if (abs(zoomScale - z) < 0.05f) "✓ $label" else "    $label",
                                                fontWeight = if (abs(zoomScale - z) < 0.05f) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            zoomScale = z
                                            if (z == 1.0f) {
                                                zoomOffsetX = 0f
                                                zoomOffsetY = 0f
                                            }
                                            showZoomMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Nút Audio Boost 200%
                        LiquidGlassPill(
                            active = audioBoostPercent > 0,
                            onClick = {
                                audioBoostPercent = if (audioBoostPercent == 0) 50 else if (audioBoostPercent == 50) 100 else 0
                                playerManager.setAudioBoostPercent(audioBoostPercent)
                                gestureHint = if (audioBoostPercent > 0) "🚀 Audio Boost: ${100 + audioBoostPercent}%" else "🔊 Âm lượng chuẩn 100%"
                                scope.launch { delay(1000); gestureHint = null }
                            }
                        ) {
                            Text(
                                text = if (audioBoostPercent > 0) "Boost ${100 + audioBoostPercent}%" else "Boost",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Nút Phát nền
                        IconButton(onClick = {
                            backgroundAudioEnabled = !backgroundAudioEnabled
                            gestureHint = if (backgroundAudioEnabled) "🎧 Đã bật Phát tiếng khi tắt màn hình" else "🎧 Đã tắt Phát nền"
                            scope.launch { delay(1200); gestureHint = null }
                        }) {
                            Icon(
                                Icons.Default.Headphones,
                                contentDescription = "Nghe nền",
                                tint = if (backgroundAudioEnabled) palette.primaryGlow else Color.White
                            )
                        }

                        // 5. MENU CHỌN HẸN GIỜ TẮT
                        Box {
                            IconButton(onClick = { showSleepMenu = true }) {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = "Hẹn giờ tắt",
                                    tint = if (sleepTimerMinutes > 0) Color(0xFFFFD54F) else Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showSleepMenu,
                                onDismissRequest = { showSleepMenu = false }
                            ) {
                                val timerOptions = listOf(0, 10, 15, 30, 45, 60, 90)
                                timerOptions.forEach { mins ->
                                    DropdownMenuItem(
                                        text = {
                                            val label = if (mins == 0) "Tắt hẹn giờ" else "$mins phút"
                                            Text(
                                                text = if (sleepTimerMinutes == mins) "✓ $label" else "    $label",
                                                fontWeight = if (sleepTimerMinutes == mins) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            sleepTimerMinutes = mins
                                            showSleepMenu = false
                                            gestureHint = if (mins > 0) "💤 Hẹn giờ tắt: $mins phút" else "💤 Đã tắt hẹn giờ ngủ"
                                            scope.launch { delay(1200); gestureHint = null }
                                        }
                                    )
                                }
                            }
                        }

                        // 6. MENU PHỤ ĐỀ
                        Box {
                            IconButton(onClick = { showSubtitleMenu = true }) {
                                Icon(
                                    Icons.Default.Subtitles,
                                    contentDescription = "Phụ đề",
                                    tint = if (subtitlesEnabled) palette.primaryGlow else Color.White.copy(alpha = 0.35f)
                                )
                            }
                            DropdownMenu(
                                expanded = showSubtitleMenu,
                                onDismissRequest = { showSubtitleMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (subtitlesEnabled) "✓ Đang bật hiển thị phụ đề" else "    Bật hiển thị phụ đề",
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    onClick = {
                                        subtitlesEnabled = !subtitlesEnabled
                                        playerManager.toggleSubtitles(subtitlesEnabled)
                                        showSubtitleMenu = false
                                        gestureHint = if (subtitlesEnabled) "💬 Đã bật Phụ đề" else "💬 Đã tắt Phụ đề"
                                        scope.launch { delay(1000); gestureHint = null }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📂 Chọn file phụ đề (.srt, .ass, .vtt) từ máy...") },
                                    onClick = {
                                        showSubtitleMenu = false
                                        subtitlePickerLauncher.launch(arrayOf("*/*"))
                                    }
                                )
                                if (loadedSubtitles.isNotEmpty()) {
                                    Divider()
                                    loadedSubtitles.forEach { sub ->
                                        DropdownMenuItem(
                                            text = { Text("📄 ${sub.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            onClick = {
                                                subtitlesEnabled = true
                                                playerManager.toggleSubtitles(true)
                                                showSubtitleMenu = false
                                                gestureHint = "💬 Đang dùng: ${sub.name}"
                                                scope.launch { delay(1000); gestureHint = null }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(onClick = {
                            val curOrient = activity?.requestedOrientation
                            if (curOrient == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = "Xoay màn hình", tint = Color.White)
                        }

                        IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(Icons.Default.AspectRatio, contentDescription = "Tỷ lệ khung hình", tint = Color.White)
                        }

                        IconButton(onClick = { enterPip() }) {
                            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                        }

                        IconButton(onClick = { isLocked = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Khóa màn hình", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}