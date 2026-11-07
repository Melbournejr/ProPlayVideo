package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.ExternalSubtitleItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    video: VideoItem?,
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

    var currentVideo by remember(video) { mutableStateOf(video) }
    val currentIndex = remember(currentVideo, playlist) {
        if (currentVideo == null) -1 else playlist.indexOfFirst { it.id == currentVideo!!.id }
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

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var zoomOffsetX by remember { mutableFloatStateOf(0f) }
    var zoomOffsetY by remember { mutableFloatStateOf(0f) }

    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }

    // Trình chọn file Phụ đề từ bộ nhớ ngoài (.srt, .ass, .ssa, .vtt)
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val fileName = context.resolveUriFileName(uri)
            playerManager.attachExternalSubtitle(uri, fileName)
            loadedSubtitles = playerManager.getLoadedSubtitles()
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

    fun persistCurrentPosition() {
        val vid = currentVideo ?: return
        val pos = playerManager.getCurrentPosition()
        val dur = playerManager.getDuration()
        val posToSave = if (dur > 5000L && pos >= dur - 2000L) 0L else pos
        CoroutineScope(Dispatchers.IO).launch {
            context.saveLastPosition(vid.id, posToSave)
        }
    }

    fun playNextVideo() {
        if (playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1) {
            persistCurrentPosition()
            resetZoom()
            val nextVid = playlist[currentIndex + 1]
            currentVideo = nextVid
            onVideoChanged(nextVid)
            gestureHint = "⏭️ Đang phát: ${nextVid.displayName}"
            scope.launch { delay(1500); gestureHint = null }
        }
    }

    fun playPreviousVideo() {
        if (playlist.isNotEmpty() && currentIndex > 0) {
            persistCurrentPosition()
            resetZoom()
            val prevVid = playlist[currentIndex - 1]
            currentVideo = prevVid
            onVideoChanged(prevVid)
            gestureHint = "⏮️ Đang phát: ${prevVid.displayName}"
            scope.launch { delay(1500); gestureHint = null }
        }
    }

    fun enterPip() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                persistCurrentPosition()
                resetZoom()
                showControls = false
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

    DisposableEffect(playerManager, currentIndex, playlist) {
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
                    if (playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1) {
                        playNextVideo()
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
        persistCurrentPosition()
        onBack()
    }

    LaunchedEffect(currentVideo, streamUrl) {
        if (currentVideo != null) {
            onVideoChanged(currentVideo!!)
            val savedPos = context.readLastPosition(currentVideo!!.id)
            val autoMatchedSubs = playerManager.playLocal(currentVideo!!, savedPos)
            loadedSubtitles = autoMatchedSubs
            currentPos = savedPos
            if (autoMatchedSubs.isNotEmpty()) {
                gestureHint = "💬 Tự động nạp phụ đề: ${autoMatchedSubs.first().name}"
                scope.launch { delay(1800); if (gestureMode == GestureMode.NONE) gestureHint = null }
            }
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
            loadedSubtitles = emptyList()
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

    LaunchedEffect(currentVideo) {
        while (true) {
            isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                isInPipMode = activity.isInPictureInPictureMode
            }
            if (gestureMode != GestureMode.SEEK) {
                currentPos = playerManager.getCurrentPosition()
            }
            duration = playerManager.getDuration().coerceAtLeast(1L)
            if (currentVideo != null && isPlaying) {
                context.saveLastPosition(currentVideo!!.id, currentPos)
            }
            delay(500)
        }
    }

    DisposableEffect(lifecycleOwner, currentVideo, backgroundAudioEnabled) {
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
                PlayerView(ctx).apply {
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
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = zoomOffsetX,
                    translationY = zoomOffsetY
                )
                .pointerInput(isLocked, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    detectTapGestures(
                        onTap = { showControls = !showControls },
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

        // Hiển thị thông báo Gesture dạng Viên nang Kính Lỏng
        if (!isInPipMode) {
            gestureHint?.let { hint ->
                val pillShape = RoundedCornerShape(50)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(pillShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    palette.deepBgMid.copy(alpha = 0.88f),
                                    palette.deepBgTop.copy(alpha = 0.90f)
                                )
                            )
                        )
                        .border(
                            BorderStroke(
                                1.2.dp,
                                Brush.linearGradient(
                                    colors = listOf(Color.White.copy(alpha = 0.6f), palette.primaryGlow.copy(alpha = 0.6f))
                                )
                            ),
                            pillShape
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = hint,
                        color = if (audioBoostPercent > 0 && gestureMode == GestureMode.VOLUME) palette.primaryGlow else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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

        // ================= FLOATING LIQUID GLASS PLAYER HUD =================
        if (showControls && !isLocked && !isInPipMode) {
            val hudShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .clip(hudShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                palette.deepBgMid.copy(alpha = 0.80f),
                                palette.deepBgTop.copy(alpha = 0.88f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(800f, 600f)
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    palette.secondaryGlow.copy(alpha = 0.35f),
                                    palette.primaryGlow.copy(alpha = 0.55f)
                                )
                            )
                        ),
                        hudShape
                    )
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
                    if (zoomScale > 1.01f) {
                        LiquidGlassPill(
                            active = true,
                            onClick = { resetZoom() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("🔍 ${(zoomScale * 100).roundToInt()}% ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (audioBoostPercent > 0) {
                        LiquidGlassPill(
                            active = true,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text("🔊 ${100 + audioBoostPercent}%", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall)
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
                        thumbColor = palette.primaryGlow,
                        activeTrackColor = palette.primaryGlow,
                        inactiveTrackColor = Color.White.copy(alpha = 0.20f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTimeMs(currentPos), color = palette.primaryGlow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    if (sleepRemainingSec > 0) {
                        Text("💤 Tắt sau ${formatTimeMs(sleepRemainingSec * 1000L)}", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatTimeMs(duration), color = Color(0xFFA7B8D8), style = MaterialTheme.typography.bodySmall)
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
                        onClick = { playPreviousVideo() },
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
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(palette.primaryGlow, palette.secondaryGlow)
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
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
                        onClick = { playNextVideo() },
                        enabled = playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Video tiếp theo",
                            tint = if (playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                        )
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

                    // 2. MENU CHỌN MỨC ZOOM VIDEO
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

                    // 3. MENU CHỌN HẸN GIỜ TẮT
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

                    // 4. MENU PHỤ ĐỀ (TỰ ĐỘNG NẠP CÙNG THƯ MỤC + CHỌN FILE TỪ BỘ NHỚ MÁY)
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