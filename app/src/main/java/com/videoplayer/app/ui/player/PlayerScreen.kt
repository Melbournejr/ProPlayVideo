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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.ExternalSubtitleItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.media.VideoNerdStats
import com.videoplayer.app.ui.components.AppIcons
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

private enum class ActiveModal {
    NONE, STUDIO, TOOLS
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
    return result ?: (uri.path?.substringAfterLast('/') ?: "subtitle.srt")
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
    return Paint().apply { colorFilter = ColorMatrixColorFilter(satMatrix) }
}

/**
 * Thanh tua thời gian vẽ trực tiếp trên Canvas (Direct-Touch 120Hz SeekBar)
 * Không qua trung gian Material3 Slider -> Kéo chấm tròn bám sát ngón tay với độ trễ 0ms!
 */
@Composable
private fun DirectCanvasSeekBar(
    currentPosMs: Long,
    durationMs: Long,
    sleepRemainingSec: Int,
    activeColor: Color,
    onDragStart: () -> Unit,
    onDragEnd: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (isDragging) {
        dragFraction
    } else {
        (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayedMs = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        currentPosMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(safeDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        onDragStart()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (down.position.x / w).coerceIn(0f, 1f)

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                            }
                        } while (event.changes.any { it.pressed })

                        val targetMs = (dragFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                        isDragging = false
                        onDragEnd(targetMs)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val trackHeight = if (isDragging) 7.dp.toPx() else 5.dp.toPx()
                val centerY = size.height / 2f
                val thumbX = (fraction * size.width).coerceIn(0f, size.width)

                // Đường rãnh nền
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
                // Đường tiến trình đã chạy
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
                // Quầng sáng quanh chấm tròn khi đang kéo
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.35f),
                        radius = 13.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                }
                // Chấm tròn trắng (Thumb)
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 9.dp.toPx() else 7.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDragging) "⏱️ ${formatTimeMs(displayedMs)}" else formatTimeMs(displayedMs),
                color = if (isDragging) activeColor else Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (sleepRemainingSec > 0) {
                Text(
                    text = "💤 ${formatTimeMs(sleepRemainingSec * 1000L)}",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
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

    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSeekCompletedTimeMs by remember { mutableLongStateOf(0L) }

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

    var activeModal by remember { mutableStateOf(ActiveModal.NONE) }
    var studioTabIndex by remember { mutableIntStateOf(0) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var eqBands by remember { mutableStateOf(playerManager.getEqualizerBands()) }
    var bassBoostPercent by remember { mutableIntStateOf(0) }
    var activeEqPresetName by remember { mutableStateOf("Mặc định") }

    var loopPointA by remember { mutableStateOf<Long?>(null) }
    var loopPointB by remember { mutableStateOf<Long?>(null) }
    var isMirrorFlipped by remember { mutableStateOf(false) }
    var videoSaturation by remember { mutableFloatStateOf(1.0f) }
    var videoContrast by remember { mutableFloatStateOf(1.0f) }
    var videoBrightnessFilter by remember { mutableFloatStateOf(0f) }
    var currentScreenBrightness by remember { mutableFloatStateOf(context.getSystemScreenBrightness()) }
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
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val fileName = context.resolveUriFileName(uri)
            scope.launch {
                loadedSubtitles = playerManager.attachExternalSubtitleToCurrent(vid, uri, fileName)
                subtitlesEnabled = true
                gestureHint = "💬 Đã nạp phụ đề: $fileName"
                delay(1600)
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

    fun toggleScreenOrientation() {
        val act = activity ?: return
        val isLandscape = act.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        act.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun triggerNextVideo() {
        if (effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1) {
            persistCurrentPosition()
            resetVideoTransformAndLoop()
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
            resetVideoTransformAndLoop()
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

    fun takeVideoSnapshot() {
        val vid = currentVideo
        val pos = playerManager.getCurrentPosition()
        activeModal = ActiveModal.NONE
        gestureHint = "📸 Đang chụp khung hình gốc..."
        scope.launch {
            val res = VideoSnapshotHelper.captureCurrentFrame(context, vid?.path, pos, vid?.title ?: "Video")
            gestureHint = res.fold(
                onSuccess = { "✅ Đã lưu: $it" },
                onFailure = { "⚠️ Lỗi: ${it.message}" }
            )
            delay(2000)
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
                activeModal = ActiveModal.NONE
                act.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
                isInPipMode = true
            } catch (_: Exception) {}
        }
    }

    // Giữ sáng màn hình & Ẩn thanh thông báo (Không tự ép xoay màn hình khi mới mở để chống khựng!)
    DisposableEffect(activity, currentVideo) {
        val window = activity?.window
        val isVaultVideo = currentVideo?.path?.startsWith("vault://") == true
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (isVaultVideo) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
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
        // Đồng bộ độ sáng thực của máy (hoặc mức sáng đã lưu) và Cỡ/Màu phụ đề
        val initBright = context.readPreferredBrightness()
        currentScreenBrightness = initBright
        activity?.window?.let { win ->
            val attrs = win.attributes
            attrs.screenBrightness = initBright
            win.attributes = attrs
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
            val subs = playerManager.playPlaylist(effectivePlaylist, startIdx, savedPos)
            loadedSubtitles = subs
            currentPos = savedPos
            delay(250)
            eqBands = playerManager.getEqualizerBands()
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
            loadedSubtitles = emptyList()
        }
    }

    // Chỉ lắng nghe STATE_ENDED để tự chuyển tập tiếp theo (Đã gỡ bỏ hoàn toàn tự động xoay màn hình!)
    DisposableEffect(playerManager, effectivePlaylist) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    currentVideo?.let { finishedVid ->
                        CoroutineScope(Dispatchers.IO).launch { context.saveLastPosition(finishedVid.id, 0L) }
                    }
                    val nextIdx = playerManager.currentIndex + 1
                    if (nextIdx in effectivePlaylist.indices) {
                        val nextVid = effectivePlaylist[nextIdx]
                        resetVideoTransformAndLoop()
                        scope.launch {
                            val savedPos = context.readLastPosition(nextVid.id)
                            val (_, subs) = playerManager.playVideoAtIndex(nextIdx, savedPos)
                            currentVideo = nextVid
                            loadedSubtitles = subs
                            onVideoChanged(nextVid)
                            gestureHint = "⏭️ Tự phát tiếp: ${nextVid.displayName}"
                            delay(1600)
                            if (gestureMode == GestureMode.NONE) gestureHint = null
                        }
                    }
                }
            }
        }
        playerManager.player.addListener(listener)
        onDispose { playerManager.player.removeListener(listener) }
    }

    BackHandler {
        if (activeModal != ActiveModal.NONE) {
            activeModal = ActiveModal.NONE
        } else {
            persistCurrentPosition()
            onBack()
        }
    }

    // Tự ẩn thanh công cụ sau 3.5 giây
    LaunchedEffect(showControls, isPlaying, isDraggingSlider, activeModal, showSpeedMenu, gestureMode) {
        if (showControls && isPlaying && !isDraggingSlider && activeModal == ActiveModal.NONE && !showSpeedMenu && gestureMode == GestureMode.NONE) {
            delay(3500)
            showControls = false
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

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                isInPipMode = activity.isInPictureInPictureMode
            }
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
                    if (!inPip && !backgroundAudioEnabled) playerManager.pause()
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

                val colorPaint = buildVideoColorPaint(videoSaturation, videoContrast, videoBrightnessFilter)
                if (colorPaint != null) view.setLayerType(View.LAYER_TYPE_HARDWARE, colorPaint)
                else view.setLayerType(View.LAYER_TYPE_NONE, null)

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
                            if (activeModal != ActiveModal.NONE) activeModal = ActiveModal.NONE
                            else showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            if (!isLocked) {
                                val width = size.width
                                val cur = playerManager.getCurrentPosition()
                                val dur = playerManager.getDuration().coerceAtLeast(1L)
                                val next = if (offset.x < width / 2f) {
                                    (cur - 10_000L).coerceAtLeast(0L)
                                } else {
                                    (cur + 10_000L).coerceAtMost(dur)
                                }
                                playerManager.seekToFast(next)
                                currentPos = next
                                lastSeekCompletedTimeMs = System.currentTimeMillis()
                                gestureHint = if (offset.x < width / 2f) "⏪ -10s (${formatTimeMs(next)})" else "⏩ +10s (${formatTimeMs(next)})"
                                persistCurrentPosition()
                                scope.launch { delay(750); if (gestureMode == GestureMode.NONE) gestureHint = null }
                            }
                        }
                    )
                }
                .pointerInput(isLocked, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = true)
                        if (isLocked) return@awaitEachGesture
                        if (showControls && firstDown.position.y > size.height * 0.70f) return@awaitEachGesture

                        gestureMode = GestureMode.NONE
                        val startTouchX = firstDown.position.x
                        var totalDragX = 0f
                        var totalDragY = 0f

                        val initialSeekPos = playerManager.getCurrentPosition()
                        var targetSeekPos = initialSeekPos

                        val winBright = activity?.window?.attributes?.screenBrightness ?: -1f
                        val initialBrightness = if (winBright in 0.01f..1.0f) winBright else currentScreenBrightness

                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val basePercent = (curVol.toFloat() / maxVol) * 100f
                        val initialCombinedVol = if (basePercent >= 99f && audioBoostPercent > 0) 100f + audioBoostPercent else basePercent

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
                                    gestureHint = "🔍 ${(zoomScale * 100).roundToInt()}%"
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
                                        val threshold = 10f
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
                                            gestureHint = "⏱️ ${formatTimeMs(targetSeekPos)} ($sign)"
                                        }
                                        GestureMode.BRIGHTNESS -> {
                                            change.consume()
                                            activity?.let { act ->
                                                // Phản ứng nhanh gấp đôi (vuốt 42% màn hình là đạt 0% -> 100%)
                                                val delta = -totalDragY / (screenHeight * 0.42f)
                                                val newBright = (initialBrightness + delta).coerceIn(0.01f, 1.0f)
                                                currentScreenBrightness = newBright
                                                val attrs = act.window.attributes
                                                attrs.screenBrightness = newBright
                                                act.window.attributes = attrs
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
                                                gestureHint = "🔊 ${newCombined.roundToInt()}%"
                                            } else {
                                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                                                val boost = (newCombined - 100f).roundToInt().coerceIn(1, 100)
                                                audioBoostPercent = boost
                                                playerManager.setAudioBoostPercent(boost)
                                                gestureHint = "🚀 Boost ${100 + boost}%"
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (gestureMode == GestureMode.SEEK) {
                            playerManager.seekToFast(targetSeekPos)
                            currentPos = targetSeekPos
                            lastSeekCompletedTimeMs = System.currentTimeMillis()
                            persistCurrentPosition()
                        } else if (gestureMode == GestureMode.BRIGHTNESS) {
                            CoroutineScope(Dispatchers.IO).launch {
                                context.savePreferredBrightness(currentScreenBrightness)
                            }
                        }
                        gestureMode = GestureMode.NONE
                        gestureHint = null
                    }
                }
        )

        // Nerd Stats HUD
        if (showNerdStats && !isInPipMode) {
            nerdStats?.let { st ->
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp).widthIn(max = 260.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📊 NERD STATS", color = palette.primaryGlow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("✕", color = Color.White, modifier = Modifier.padding(2.dp).clickable { showNerdStats = false })
                        }
                        Text("• ${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp)
                        Text("• Video: ${st.videoCodec} • ${st.videoBitrate}", color = Color.White, fontSize = 11.sp)
                        Text("• Audio: ${st.audioCodec} • Drop: ${st.droppedFrames}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Gesture Hint
        if (!isInPipMode) {
            gestureHint?.let { hint ->
                LiquidGlassPill(active = true, modifier = Modifier.align(Alignment.Center)) {
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

        // ================= BẢNG MỞ RỘNG 1: PRO STUDIO =================
        if (activeModal == ActiveModal.STUDIO && !isInPipMode) {
            LiquidGlassCard(
                cornerRadius = 26.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 350.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.AutoAwesome, contentDescription = null, tint = palette.primaryGlow)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pro Studio",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlassPill(onClick = { activeModal = ActiveModal.NONE }) {
                            Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassPill(
                            active = studioTabIndex == 0,
                            onClick = { studioTabIndex = 0 },
                            modifier = Modifier.weight(1f)
                        ) { Text("🎬 Hình Ảnh & A-B", color = Color.White, style = MaterialTheme.typography.labelMedium) }

                        LiquidGlassPill(
                            active = studioTabIndex == 1,
                            onClick = {
                                eqBands = playerManager.getEqualizerBands()
                                studioTabIndex = 1
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("🎵 Âm Thanh & EQ", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                    }

                    if (studioTabIndex == 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassPill(
                                active = loopPointA != null,
                                onClick = {
                                    loopPointA = playerManager.getCurrentPosition()
                                    if (loopPointB != null && loopPointB!! <= loopPointA!!) loopPointB = null
                                }
                            ) { Text(if (loopPointA != null) "A: ${formatTimeMs(loopPointA!!)}" else "Đặt điểm A", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                            LiquidGlassPill(
                                active = loopPointB != null,
                                onClick = {
                                    val now = playerManager.getCurrentPosition()
                                    if (loopPointA == null) loopPointA = 0L
                                    if (now > loopPointA!! + 500L) loopPointB = now
                                }
                            ) { Text(if (loopPointB != null) "B: ${formatTimeMs(loopPointB!!)}" else "Đặt điểm B", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                            if (loopPointA != null || loopPointB != null) {
                                LiquidGlassPill(onClick = { loopPointA = null; loopPointB = null }) {
                                    Text("Xóa A-B", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            LiquidGlassPill(onClick = {
                                playerManager.stepFrame(forward = false)
                                isPlaying = false
                                currentPos = playerManager.getCurrentPosition()
                            }) { Text("⏮ -1 Frame", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                            LiquidGlassPill(onClick = {
                                playerManager.stepFrame(forward = true)
                                isPlaying = false
                                currentPos = playerManager.getCurrentPosition()
                            }) { Text("+1 Frame ⏭", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassPill(active = isMirrorFlipped, onClick = { isMirrorFlipped = !isMirrorFlipped }) {
                                Text(if (isMirrorFlipped) "Lật Gương: BẬT" else "Lật Gương", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            LiquidGlassPill(
                                active = showNerdStats,
                                onClick = {
                                    showNerdStats = !showNerdStats
                                    if (showNerdStats) nerdStats = playerManager.getNerdStats()
                                }
                            ) { Text("📊 Nerd Stats", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                            LiquidGlassPill(onClick = {
                                videoSaturation = 1.0f
                                videoContrast = 1.0f
                                videoBrightnessFilter = 0f
                            }) { Text("Màu gốc", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Độ tươi màu", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
                            Slider(
                                value = videoSaturation,
                                onValueChange = { videoSaturation = it },
                                valueRange = 0f..2.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                            )
                            Text("${(videoSaturation * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Tương phản", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(78.dp))
                            Slider(
                                value = videoContrast,
                                onValueChange = { videoContrast = it },
                                valueRange = 0.6f..1.8f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.primaryGlow)
                            )
                            Text("${(videoContrast * 100).roundToInt()}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LiquidGlassPill(
                                active = audioBoostPercent > 0,
                                onClick = {
                                    audioBoostPercent = if (audioBoostPercent == 0) 50 else if (audioBoostPercent == 50) 100 else 0
                                    playerManager.setAudioBoostPercent(audioBoostPercent)
                                }
                            ) { Text("🚀 Boost: ${100 + audioBoostPercent}%", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                            val presets = listOf(
                                Triple("Mặc định", listOf(0f, 0f, 0f, 0f, 0f), 0),
                                Triple("Siêu Trầm", listOf(8f, 5f, 0f, 2f, 4f), 75),
                                Triple("Rạp Phim", listOf(6f, 3f, -1f, 4f, 6f), 50),
                                Triple("Giọng Nói", listOf(-3f, 2f, 7f, 5f, 1f), 0)
                            )
                            presets.forEach { (name, levels, bass) ->
                                LiquidGlassPill(
                                    active = activeEqPresetName == name,
                                    onClick = {
                                        activeEqPresetName = name
                                        playerManager.applyEqPreset(levels, bass)
                                        bassBoostPercent = bass
                                        eqBands = playerManager.getEqualizerBands()
                                    }
                                ) { Text(name, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Siêu trầm", color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(64.dp))
                            Slider(
                                value = bassBoostPercent.toFloat(),
                                onValueChange = {
                                    bassBoostPercent = it.roundToInt()
                                    playerManager.setBassBoostPercent(bassBoostPercent)
                                },
                                valueRange = 0f..100f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F))
                            )
                            Text("$bassBoostPercent%", color = Color(0xFFFFD54F), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(38.dp))
                        }

                        eqBands.forEach { band ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(band.label, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                                Slider(
                                    value = band.levelDb,
                                    onValueChange = { newDb ->
                                        activeEqPresetName = "Tùy chỉnh"
                                        playerManager.setEqBandLevel(band.index, newDb)
                                        eqBands = playerManager.getEqualizerBands()
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

        // ================= BẢNG MỞ RỘNG 2: TIỆN ÍCH & PHỤ ĐỀ (NÚT "☰ THÊM") =================
        if (activeModal == ActiveModal.TOOLS && !isInPipMode) {
            LiquidGlassCard(
                cornerRadius = 26.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .widthIn(max = 500.dp)
                    .heightIn(max = 350.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Tune, contentDescription = null, tint = palette.primaryGlow)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tiện Ích & Phụ Đề",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f)
                        )
                        LiquidGlassPill(onClick = { activeModal = ActiveModal.NONE }) {
                            Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassPill(onClick = { takeVideoSnapshot() }, modifier = Modifier.weight(1f)) {
                            Icon(AppIcons.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chụp ảnh", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        LiquidGlassPill(onClick = { enterPip() }, modifier = Modifier.weight(1f)) {
                            Icon(AppIcons.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cửa sổ PiP", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassPill(
                            active = backgroundAudioEnabled,
                            onClick = { backgroundAudioEnabled = !backgroundAudioEnabled },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(AppIcons.Headphones, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (backgroundAudioEnabled) "Nghe nền: BẬT" else "Nghe nền: TẮT", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        LiquidGlassPill(
                            onClick = {
                                activeModal = ActiveModal.NONE
                                isLocked = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Khóa cảm ứng", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidGlassPill(
                            active = resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            val modeName = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Cắt tràn"
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Kéo giãn"
                                else -> "Vừa khung"
                            }
                            Text("Khung hình: $modeName", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💤 Hẹn giờ:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        listOf(0, 15, 30, 45, 60).forEach { mins ->
                            LiquidGlassPill(active = sleepTimerMinutes == mins, onClick = { sleepTimerMinutes = mins }) {
                                Text(if (mins == 0) "Tắt" else "${mins}p", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassPill(
                            active = subtitlesEnabled,
                            onClick = {
                                subtitlesEnabled = !subtitlesEnabled
                                playerManager.toggleSubtitles(subtitlesEnabled)
                            }
                        ) { Text(if (subtitlesEnabled) "💬 Phụ đề: BẬT" else "💬 Phụ đề: TẮT", color = Color.White, style = MaterialTheme.typography.labelSmall) }

                        LiquidGlassPill(
                            onClick = {
                                activeModal = ActiveModal.NONE
                                subtitlePickerLauncher.launch(arrayOf("*/*"))
                            }
                        ) { Text("📂 Mở file .SRT/.ASS", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }

                    // Bước 5: UI Chọn Track Âm thanh (Audio Tracks) bằng TrackSelectionOverride
                    val audioTracks = remember(currentVideo, isPlaying, activeModal) {
                        playerManager.getAvailableTracks(C.TRACK_TYPE_AUDIO)
                    }
                    if (audioTracks.isNotEmpty()) {
                        Text("🔊 Chọn Kênh Âm Thanh (Audio Track):", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            audioTracks.forEach { trackOpt ->
                                LiquidGlassPill(
                                    active = trackOpt.isSelected,
                                    onClick = {
                                        playerManager.selectTrackOverride(trackOpt)
                                        gestureHint = "🔊 Đã chọn âm thanh: ${trackOpt.label}"
                                        scope.launch { delay(1200); gestureHint = null }
                                    }
                                ) {
                                    Text(trackOpt.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Bước 5: UI Chọn Track Phụ đề (Subtitle Tracks) bằng TrackSelectionOverride
                    val textTracks = remember(currentVideo, loadedSubtitles, isPlaying, activeModal) {
                        playerManager.getAvailableTracks(C.TRACK_TYPE_TEXT)
                    }
                    if (textTracks.isNotEmpty()) {
                        Text("💬 Chọn Kênh Phụ Đề (Subtitle Track):", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            textTracks.forEach { trackOpt ->
                                LiquidGlassPill(
                                    active = subtitlesEnabled && trackOpt.isSelected,
                                    onClick = {
                                        subtitlesEnabled = true
                                        playerManager.selectTrackOverride(trackOpt)
                                        gestureHint = "💬 Đã chọn phụ đề: ${trackOpt.label}"
                                        scope.launch { delay(1200); gestureHint = null }
                                    }
                                ) {
                                    Text(trackOpt.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cỡ & Màu:", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        listOf(16f to "Nhỏ", 20f to "Vừa", 26f to "Lớn", 32f to "To").forEach { (sz, lbl) ->
                            LiquidGlassPill(
                                active = subtitleSizeSp == sz,
                                onClick = {
                                    subtitleSizeSp = sz
                                    scope.launch { context.saveSubtitleAppearance(sz, subtitleTextColor.toArgb()) }
                                }
                            ) {
                                Text(lbl, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        listOf(Color.White to "Trắng", Color(0xFFFFEA00) to "Vàng", Color(0xFF00F0FF) to "Cyan").forEach { (col, name) ->
                            LiquidGlassPill(
                                active = subtitleTextColor == col,
                                onClick = {
                                    subtitleTextColor = col
                                    scope.launch { context.saveSubtitleAppearance(subtitleSizeSp, col.toArgb()) }
                                }
                            ) {
                                Text(name, color = col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
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
                Icon(AppIcons.LockOpen, contentDescription = "Mở khóa", tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Chạm để Mở khóa", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // ================= THANH ĐIỀU KHIỂN CHÍNH (CÓ NÚT XOAY RA NGOÀI & THANH TUA CANVAS 120HZ) =================
        if (showControls && !isLocked && !isInPipMode && activeModal == ActiveModal.NONE) {
            LiquidGlassCard(
                cornerRadius = 28.dp,
                highlighted = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // HÀNG 1: NÚT QUAY LẠI (←) + TIÊU ĐỀ + SỐ TẬP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { persistCurrentPosition(); onBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = currentVideo?.displayName ?: (streamUrl ?: "Đang phát"),
                            style = MaterialTheme.typography.titleSmall,
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
                                modifier = Modifier.padding(start = 4.dp)
                            ) { Text("🔁 A-B ✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (zoomScale > 1.01f) {
                            LiquidGlassPill(
                                active = true,
                                onClick = { resetZoom() },
                                modifier = Modifier.padding(start = 4.dp)
                            ) { Text("🔍 ${(zoomScale * 100).roundToInt()}% ✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (effectivePlaylist.size > 1 && currentIndex >= 0) {
                            LiquidGlassPill(active = false, modifier = Modifier.padding(start = 6.dp)) {
                                Text("${currentIndex + 1}/${effectivePlaylist.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // HÀNG 2: THANH TUA CANVAS 120HZ (BÁM DÍNH NGÓN TAY 0MS DELAY)
                    DirectCanvasSeekBar(
                        currentPosMs = currentPos,
                        durationMs = duration,
                        sleepRemainingSec = sleepRemainingSec,
                        activeColor = palette.primaryGlow,
                        onDragStart = { isDraggingSlider = true },
                        onDragEnd = { targetMs ->
                            currentPos = targetMs
                            lastSeekCompletedTimeMs = System.currentTimeMillis()
                            isDraggingSlider = false
                            playerManager.seekToFast(targetMs)
                            persistCurrentPosition()
                        }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // HÀNG 3: CÁC NÚT CHÍNH (CÓ NÚT XOAY MÀN HÌNH RA NGOÀI, VỪA KHÍT MÀN HÌNH DỌC)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Tốc độ phát (1.0x)
                        Box {
                            LiquidGlassPill(
                                active = playbackSpeed != 1.0f,
                                onClick = { showSpeedMenu = true }
                            ) {
                                Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                    DropdownMenuItem(
                                        text = {
                                            val label = if (spd == 1.0f) "1.0x (Chuẩn)" else "${spd}x"
                                            Text(if (playbackSpeed == spd) "✓ $label" else "    $label")
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

                        // 2. Cụm điều hướng phát: Bài trước - Play/Pause - Bài tiếp
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { triggerPreviousVideo() },
                                enabled = currentIndex > 0,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    AppIcons.SkipPrevious,
                                    contentDescription = "Trước",
                                    tint = if (currentIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .drawWithCache {
                                        val r = size.width / 2f
                                        val fill = Brush.linearGradient(
                                            listOf(Color.White.copy(alpha = 0.45f), palette.primaryGlow.copy(alpha = 0.75f), palette.secondaryGlow.copy(alpha = 0.75f))
                                        )
                                        val rim = Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f)))
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
                                    Icon(if (isPlaying) AppIcons.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                }
                            }

                            IconButton(
                                onClick = { triggerNextVideo() },
                                enabled = effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    AppIcons.SkipNext,
                                    contentDescription = "Tiếp",
                                    tint = if (effectivePlaylist.isNotEmpty() && currentIndex in 0 until effectivePlaylist.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // 3. Nút Xoay Màn Hình đặt ngay bên ngoài + Nút Studio + Nút Thêm
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Nút Xoay ngang/dọc trực tiếp ngoài thanh công cụ
                            IconButton(
                                onClick = { toggleScreenOrientation() },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    AppIcons.ScreenRotation,
                                    contentDescription = "Xoay màn hình",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            LiquidGlassPill(
                                active = loopPointA != null || isMirrorFlipped || audioBoostPercent > 0 || bassBoostPercent > 0 || abs(videoSaturation - 1f) > 0.05f,
                                onClick = { activeModal = ActiveModal.STUDIO }
                            ) {
                                Icon(AppIcons.AutoAwesome, contentDescription = "Studio", tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Studio", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }

                            LiquidGlassPill(
                                active = backgroundAudioEnabled || sleepTimerMinutes > 0,
                                onClick = { activeModal = ActiveModal.TOOLS }
                            ) {
                                Icon(AppIcons.Tune, contentDescription = "Thêm", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}