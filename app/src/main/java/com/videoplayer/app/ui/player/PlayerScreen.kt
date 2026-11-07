package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
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

    // Biến trạng thái phục vụ Khóa trục Gesture (Chống nhảy qua lại giữa Tua và Âm lượng/Độ sáng)
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
            // Nếu xem gần hết (>98%) thì đặt lại về 0 cho lần xem sau, ngược lại lưu đúng vị trí đang chạy
            val posToSave = if (dur > 5000L && pos >= dur - 2000L) 0L else pos
            CoroutineScope(Dispatchers.IO).launch {
                context.saveLastPosition(video.id, posToSave)
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

    // Lưu vị trí định kỳ mỗi 1 giây khi đang phát
    LaunchedEffect(video) {
        while (true) {
            isPlaying = playerManager.isPlaying()
            if (gestureMode != GestureMode.SEEK) {
                currentPos = playerManager.getCurrentPosition()
            }
            duration = playerManager.getDuration().coerceAtLeast(1L)
            if (video != null && isPlaying) {
                context.saveLastPosition(video.id, currentPos)
            }
            delay(1000)
        }
    }

    // Lưu vị trí ngay khi người dùng bấm Home / tắt màn hình / thoát trình phát
    DisposableEffect(lifecycleOwner, video) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                persistCurrentPosition()
                playerManager.pause()
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
                    player = playerManager.player
                    useController = false
                    setResizeMode(resizeMode)
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
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
                .pointerInput(isLocked) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (isLocked) return@detectDragGestures
                            gestureMode = GestureMode.NONE
                            startTouchX = startOffset.x
                            totalDragX = 0f
                            totalDragY = 0f
                            initialSeekPos = playerManager.getCurrentPosition()
                            targetSeekPos = initialSeekPos

                            val activity = context as? Activity
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

                            // KHÓA TRỤC: Chỉ quyết định chế độ 1 lần duy nhất khi vượt ngưỡng 18px
                            if (gestureMode == GestureMode.NONE) {
                                val threshold = 18f
                                if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                                    gestureMode = if (abs(totalDragX) > abs(totalDragY) * 1.2f) {
                                        GestureMode.SEEK
                                    } else {
                                        if (startTouchX < screenWidth / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                                    }
                                    // Đặt lại tổng quãng đường tính từ thời điểm khóa trục để mượt tuyệt đối
                                    totalDragX = 0f
                                    totalDragY = 0f
                                }
                            }

                            when (gestureMode) {
                                GestureMode.SEEK -> {
                                    val dur = playerManager.getDuration().coerceAtLeast(1L)
                                    // Vuốt hết chiều ngang màn hình tương đương tua tối đa 120 giây (hoặc 1/2 thời lượng video ngắn)
                                    val maxSeekRange = minOf(dur, 120_000L).coerceAtLeast(30_000L)
                                    val seekDelta = ((totalDragX / screenWidth) * maxSeekRange).toLong()
                                    targetSeekPos = (initialSeekPos + seekDelta).coerceIn(0L, dur)
                                    currentPos = targetSeekPos
                                    val diffSec = (targetSeekPos - initialSeekPos) / 1000
                                    val sign = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                    gestureHint = "⏱️ ${formatTimeMs(targetSeekPos)} / ${formatTimeMs(dur)} ($sign)"
                                }
                                GestureMode.BRIGHTNESS -> {
                                    val activity = context as? Activity ?: return@detectDragGestures
                                    val window = activity.window
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

        // Hiển thị thông báo Gesture ở giữa màn hình
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

        // Nút mở khóa khi đang bật Child Lock
        if (isLocked && showControls) {
            FloatingActionButton(
                onClick = { isLocked = false },
                modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Mở khóa")
            }
        }

        // Bảng điều khiển đầy đủ
        if (showControls && !isLocked) {
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
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            persistCurrentPosition()
                            (context as? Activity)?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                            )
                        }
                    }) {
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