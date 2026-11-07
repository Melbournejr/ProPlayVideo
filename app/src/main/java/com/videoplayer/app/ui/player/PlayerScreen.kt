package com.videoplayer.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.readLastPosition
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    video: VideoItem?,
    streamUrl: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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

    LaunchedEffect(video, streamUrl) {
        if (video != null) {
            val savedPos = context.readLastPosition(video.id)
            playerManager.playLocal(video, savedPos)
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = playerManager.isPlaying()
            currentPos = playerManager.getCurrentPosition()
            duration = playerManager.getDuration().coerceAtLeast(1L)
            if (video != null && isPlaying) {
                context.saveLastPosition(video.id, currentPos)
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
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
                                if (offset.x < width / 2f) {
                                    playerManager.seekTo((cur - 10_000L).coerceAtLeast(0L))
                                    gestureHint = "⏪ -10 giây"
                                } else {
                                    playerManager.seekTo((cur + 10_000L).coerceAtMost(duration))
                                    gestureHint = "⏩ +10 giây"
                                }
                                scope.launch { delay(800); gestureHint = null }
                            }
                        },
                        onLongPress = {
                            if (!isLocked) {
                                playerManager.setPlaybackSpeed(2.0f)
                                gestureHint = "⚡ Tốc độ 2.0x"
                                scope.launch { delay(1200); gestureHint = null }
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    detectDragGestures(
                        onDragEnd = { gestureHint = null },
                        onDrag = { change, dragAmount ->
                            if (isLocked) return@detectDragGestures
                            val width = size.width.toFloat()
                            val touchX = change.position.x
                            val dx = dragAmount.x
                            val dy = dragAmount.y

                            if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                if (touchX < width / 2f) {
                                    // Vuốt dọc bên trái: Độ sáng
                                    val activity = context as? Activity ?: return@detectDragGestures
                                    val window = activity.window
                                    val attrs = window.attributes
                                    val curBright = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
                                    val newBright = (curBright - dy / 600f).coerceIn(0.05f, 1.0f)
                                    attrs.screenBrightness = newBright
                                    window.attributes = attrs
                                    gestureHint = "☀️ Độ sáng: ${(newBright * 100).toInt()}%"
                                } else {
                                    // Vuốt dọc bên phải: Âm lượng
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val delta = if (dy < -10) 1 else if (dy > 10) -1 else 0
                                    if (delta != 0) {
                                        val newVol = (curVol + delta).coerceIn(0, maxVol)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        gestureHint = "🔊 Âm lượng: ${(newVol * 100) / maxVol}%"
                                    }
                                }
                            } else {
                                // Vuốt ngang: Tua video
                                val newPos = (playerManager.getCurrentPosition() + (dx * 150).toLong()).coerceIn(0L, duration)
                                playerManager.seekTo(newPos)
                                gestureHint = "⏱️ ${newPos / 1000}s / ${duration / 1000}s"
                            }
                        }
                    )
                }
        )

        // Hiển thị thông báo Gesture ở giữa màn hình
        gestureHint?.let { hint ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
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
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${currentPos / 1000}s", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("${duration / 1000}s", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (isPlaying) playerManager.pause() else playerManager.resume()
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