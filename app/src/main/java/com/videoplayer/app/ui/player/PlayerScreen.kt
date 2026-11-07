package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    playlist: List<VideoItem>,
    streamUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { PlayerManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    // Quản lý Playlist để Next/Prev & Tự chuyển tập
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
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var isInPipMode by remember { mutableStateOf(false) }

    // Combo 4 States: Audio Boost 200%, Background Audio, Sleep Timer, Auto-Rotate
    var audioBoostPercent by remember { mutableIntStateOf(0) } // 0..100 (tương ứng 100%..200%)
    var backgroundAudioEnabled by remember { mutableStateOf(false) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) } // 0 = Tắt, 15, 30, 60
    var sleepRemainingSec by remember { mutableIntStateOf(0) }

    // Biến trạng thái Khóa trục Gesture
    var gestureMode by remember { mutableStateOf(GestureMode.NONE) }
    var startTouchX by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var initialSeekPos by remember { mutableLongStateOf(0L) }
    var targetSeekPos by remember { mutableLongStateOf(0L) }
    var initialBrightness by remember { mutableFloatStateOf(0.5f) }
    var initialCombinedVolumePercent by remember { mutableFloatStateOf(100f) } // 0..200%

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
            currentVideo = playlist[currentIndex + 1]
            gestureHint = "⏭️ Đang phát: ${currentVideo?.displayName}"
            scope.launch { delay(1500); gestureHint = null }
        }
    }

    fun playPreviousVideo() {
        if (playlist.isNotEmpty() && currentIndex > 0) {
            persistCurrentPosition()
            currentVideo = playlist[currentIndex - 1]
            gestureHint = "⏮️ Đang phát: ${currentVideo?.displayName}"
            scope.launch { delay(1500); gestureHint = null }
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

    // 1. Giữ màn hình sáng + Immersive Fullscreen + Tự trả lại màn hình dọc khi thoát Player
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

    // 2. Listener cho Smart Auto-Rotate (Tự xoay ngang nếu video ngang) & Tự chuyển tập tiếp theo (STATE_ENDED)
    DisposableEffect(playerManager, currentIndex, playlist) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    if (videoSize.width > videoSize.height) {
                        // Video ngang -> Tự động xoay ngang cảm biến 2 chiều
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        // Video dọc (TikTok/Shorts) -> Giữ màn hình dọc
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

    // Phát video khi currentVideo hoặc streamUrl thay đổi
    LaunchedEffect(currentVideo, streamUrl) {
        if (currentVideo != null) {
            val savedPos = context.readLastPosition(currentVideo!!.id)
            playerManager.playLocal(currentVideo!!, savedPos)
            currentPos = savedPos
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
        }
    }

    // Đồng hồ đếm ngược Hẹn giờ tắt (Sleep Timer)
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

    // Vòng lặp cập nhật thanh tiến trình
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

    // Xử lý Lifecycle: Cho phép chạy nền khi bật Background Audio hoặc PiP
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

                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val basePercent = (curVol.toFloat() / maxVol) * 100f
                            initialCombinedVolumePercent = if (basePercent >= 99f && audioBoostPercent > 0) {
                                100f + audioBoostPercent
                            } else {
                                basePercent
                            }
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
                                    // Hỗ trợ vuốt từ 0% -> 200% (100%-200% kích hoạt LoudnessEnhancer Audio Boost)
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val deltaPercent = (-totalDragY / (screenHeight * 0.75f)) * 150f
                                    val newCombined = (initialCombinedVolumePercent + deltaPercent).coerceIn(0f, 200f)

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
                                        gestureHint = "🚀 Khuếch đại âm thanh: ${100 + boost}% (Boost +${(boost * 0.15f).toInt()}dB)"
                                    }
                                }
                                GestureMode.NONE -> {}
                            }
                        }
                    )
                }
        )

        // Hiển thị thông báo Gesture ở giữa màn hình
        if (!isInPipMode) {
            gestureHint?.let { hint ->
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = hint,
                        color = if (audioBoostPercent > 0 && gestureMode == GestureMode.VOLUME) Color(0xFF00E5FF) else Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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

        // Bảng điều khiển đầy đủ
        if (showControls && !isLocked && !isInPipMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.78f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (audioBoostPercent > 0) {
                        Text(
                            text = "🔊 ${100 + audioBoostPercent}%",
                            color = Color(0xFF00E5FF),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Slider(
                    value = currentPos.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        currentPos = it.toLong()
                        playerManager.seekTo(currentPos)
                    },
                    onValueChangeFinished = { persistCurrentPosition() },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTimeMs(currentPos), color = Color.White, style = MaterialTheme.typography.bodySmall)
                    if (sleepRemainingSec > 0) {
                        Text("💤 Tắt sau ${formatTimeMs(sleepRemainingSec * 1000L)}", color = Color(0xFFFFD54F), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatTimeMs(duration), color = Color.White, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Hàng nút điều khiển có thanh cuộn ngang tiện lợi
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { persistCurrentPosition(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                    // Nút Bài trước (Previous)
                    IconButton(
                        onClick = { playPreviousVideo() },
                        enabled = currentIndex > 0
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "Video trước",
                            tint = if (currentIndex > 0) Color.White else Color.Gray
                        )
                    }
                    // Nút Play / Pause
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
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // Nút Bài tiếp theo (Next)
                    IconButton(
                        onClick = { playNextVideo() },
                        enabled = playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "Video tiếp theo",
                            tint = if (playlist.isNotEmpty() && currentIndex in 0 until playlist.size - 1) Color.White else Color.Gray
                        )
                    }
                    // Nút Tốc độ phát
                    TextButton(onClick = {
                        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
                        val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        playbackSpeed = speeds[nextIdx]
                        playerManager.setPlaybackSpeed(playbackSpeed)
                    }) {
                        Text("${playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    // Nút Bật/Tắt Audio Boost 200% nhanh
                    TextButton(onClick = {
                        audioBoostPercent = if (audioBoostPercent == 0) 50 else if (audioBoostPercent == 50) 100 else 0
                        playerManager.setAudioBoostPercent(audioBoostPercent)
                        gestureHint = if (audioBoostPercent > 0) "🚀 Audio Boost: ${100 + audioBoostPercent}%" else "🔊 Âm lượng chuẩn 100%"
                        scope.launch { delay(1000); gestureHint = null }
                    }) {
                        Text(
                            text = if (audioBoostPercent > 0) "Boost ${100 + audioBoostPercent}%" else "Boost",
                            color = if (audioBoostPercent > 0) Color(0xFF00E5FF) else Color.White
                        )
                    }
                    // Nút Phát nền khi tắt màn hình (Background Audio)
                    IconButton(onClick = {
                        backgroundAudioEnabled = !backgroundAudioEnabled
                        gestureHint = if (backgroundAudioEnabled) "🎧 Đã bật Phát tiếng khi tắt màn hình" else "🎧 Đã tắt Phát nền"
                        scope.launch { delay(1200); gestureHint = null }
                    }) {
                        Icon(
                            Icons.Default.Headphones,
                            contentDescription = "Nghe nền",
                            tint = if (backgroundAudioEnabled) Color(0xFF00E5FF) else Color.White
                        )
                    }
                    // Nút Hẹn giờ ngủ (Sleep Timer)
                    IconButton(onClick = {
                        val options = listOf(0, 15, 30, 60)
                        val next = options[(options.indexOf(sleepTimerMinutes) + 1) % options.size]
                        sleepTimerMinutes = next
                        gestureHint = if (next > 0) "💤 Hẹn giờ tắt: $next phút" else "💤 Đã hủy hẹn giờ tắt"
                        scope.launch { delay(1200); gestureHint = null }
                    }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Hẹn giờ tắt",
                            tint = if (sleepTimerMinutes > 0) Color(0xFFFFD54F) else Color.White
                        )
                    }
                    // Nút Đảo chiều xoay ngang/dọc thủ công
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
                    // Nút Tỷ lệ khung hình
                    IconButton(onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Tỷ lệ khung hình", tint = Color.White)
                    }
                    // Nút Phụ đề
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
                    // Nút PiP
                    IconButton(onClick = { enterPip() }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                    }
                    // Nút Khóa trẻ em
                    IconButton(onClick = { isLocked = true }) {
                        Icon(Icons.Default.Lock, contentDescription = "Khóa màn hình", tint = Color.White)
                    }
                }
            }
        }
    }
}