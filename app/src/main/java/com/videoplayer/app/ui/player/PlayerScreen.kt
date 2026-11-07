package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureMode {
    NONE, SEEK, BRIGHTNESS, VOLUME
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
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
    streamUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { PlayerManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var isInPipMode by remember { mutableStateOf(false) }

    // Biến trạng thái phục vụ Khóa trục Gesture
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var startTouchX by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var initialSeekPos by remember { mutableLongStateOf(0L) }
    var targetSeekPos by remember { mutableLongStateOf(0L) }
    var initialBrightness by remember { mutableFloatStateOf(0.5f) }
    var initialVolume by remember { mutableFloatStateOf(0f) }

    fun persistCurrentPosition() {
        if (video != null) {
            val pos = playerManager.getCurrentPosition()
            val dur = playerManager.getDuration()
            val posToSave = if (dur > 5000L && pos >= dur - 2000L) 0L else pos
            CoroutineScope(Dispatchers.IO).launch {
                context.saveLastPosition(video.id, posToSave)
            }
        }
    }

    fun enterPip() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                persistCurrentPosition()
                showControls = false
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                act.enterPictureInPictureMode(params)
                isInPipMode = true
            } catch (_: Exception) {}
        }
    }

    // 1. Giữ màn hình luôn sáng (FLAG_KEEP_SCREEN_ON) & Ẩn thanh thông báo Fullscreen (Immersive Mode)
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
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        persistCurrentPosition()
        onBack()
    }

    LaunchedEffect(video, streamUrl) {
        if (video != null) {
            val savedPos = context.readLastPosition(video.id)
            playerManager.playLocal(video, savedPos)
            currentPos = savedPos
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
        }
    }

    // Cập nhật tiến trình & kiểm tra trạng thái PiP mỗi 500ms
    LaunchedEffect(video) {
        while (true) {
            isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                isInPipMode = activity.isInPictureInPictureMode
            }
            if (gestureMode != GestureMode.SEEK) {
                currentPos = playerManager.getCurrentPosition()
            }
            duration = playerManager.getDuration().coerceAtLeast(1L)
            if (video != null && isPlaying) {
                context.saveLastPosition(video.id, currentPos)
            }
            delay(500)
        }
    }

    // 2. Xử lý Lifecycle chuẩn cho PiP: Chỉ dừng video ở ON_STOP khi KHÔNG ở chế độ PiP
    DisposableEffect(lifecycleOwner, video) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false
            isInPipMode = inPip

            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    persistCurrentPosition()
                    // Lưu ý: KHÔNG pause video ở ON_PAUSE vì khi vừa vào PiP Android sẽ gọi ON_PAUSE
                }
                Lifecycle.Event.ON_STOP -> {
                    persistCurrentPosition()
                    if (!inPip) {
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
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (isLocked) return@detectDragGestures
                            gestureMode = GestureMode.NONE
                            startTouchX = startOffset.x
                            totalDragX = 0f
                            totalDragY = 0f
                            initialSeekPos = playerManager.getCurrentPosition()
                            targetSeekPos = initialSeekPos

                            val winBright = activity?.window?.attributes?.screenBrightness ?: -1f
                            initialBrightness = if (winBright in 0.01f..1.0f) winBright else 0.5f

                            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        },
                        onDragEnd = {
                            if (gestureMode == GestureMode.SEEK) {
                                playerManager.seekTo(targetSeekPos)
                                currentPos = targetSeekPos
                                persistCurrentPosition()
                            }
                            gestureMode = GestureMode.NONE
                            gestureHint = null
                        },
                        onDragCancel = {
                            gestureMode = GestureMode.NONE
                            gestureHint = null
                        },
                        onDrag = { change, dragAmount ->
                            if (isLocked) return@detectDragGestures
                            change.consume()

                            val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                            val screenHeight = size.height.toFloat().coerceAtLeast(1f)

                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

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
                                    val act = activity ?: return@detectDragGestures
                                    val window = act.window
                                    val attrs = window.attributes
                                    val deltaBright = -totalDragY / (screenHeight * 0.75f)
                                    val newBright = (initialBrightness + deltaBright).coerceIn(0.02f, 1.0f)
                                    attrs.screenBrightness = newBright
                                    window.attributes = attrs
                                    gestureHint = "☀️ Độ sáng: ${(newBright * 100).roundToInt()}%"
                                }
                                GestureMode.VOLUME -> {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val deltaVol = (-totalDragY / (screenHeight * 0.75f)) * maxVol
                                    val newVolFloat = (initialVolume + deltaVol).coerceIn(0f, maxVol.toFloat())
                                    val newVolInt = newVolFloat.roundToInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)
                                    val percent = ((newVolFloat / maxVol) * 100).roundToInt().coerceIn(0, 100)
                                    gestureHint = "🔊 Âm lượng: $percent%"
                                }
                                GestureMode.NONE -> {}
                            }
                        }
                    )
                }
        )

        // Hiển thị thông báo Gesture ở giữa màn hình (ẩn khi ở chế độ PiP)
        if (!isInPipMode) {
            gestureHint?.let { hint ->
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = hint,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Nút mở khóa khi đang bật Child Lock
        if (isLocked && showControls && !isInPipMode) {
            FloatingActionButton(
                onClick = { isLocked = false },
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Mở khóa")
            }
        }

        // Bảng điều khiển đầy đủ (Tự động ẩn khi vào chế độ PiP)
        if (showControls && !isLocked && !isInPipMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp)
            ) {
                Text(
                    text = video?.displayName ?: (streamUrl ?: "Đang phát"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = currentPos.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        currentPos = it.toLong()
                        playerManager.seekTo(currentPos)
                    },
                    onValueChangeFinished = {
                        persistCurrentPosition()
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTimeMs(currentPos), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text(formatTimeMs(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        persistCurrentPosition()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
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
                            if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                            contentDescription = "Phát/Tạm dừng",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    TextButton(onClick = {
                        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        playbackSpeed = speeds[nextIdx]
                        playerManager.setPlaybackSpeed(playbackSpeed)
                    }) {
                        Text("${playbackSpeed}x", color = Color.White)
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
                    IconButton(onClick = {
                        subtitlesEnabled = !subtitlesEnabled
                        playerManager.toggleSubtitles(subtitlesEnabled)
                    }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = "Phụ đề",
                            tint = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
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