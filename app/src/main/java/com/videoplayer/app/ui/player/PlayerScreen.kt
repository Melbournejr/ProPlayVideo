package com.videoplayer.app.ui.player

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.saveLastPosition
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    video: VideoItem,
    onBack: () -> Unit,
    playerManager: PlayerManager = PlayerManager(LocalContext.current)
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableStateOf(PlayerView.ASPECT_RATIO_FIT) }

    LaunchedEffect(Unit) {
        playerManager.playLocal(video)
        playerManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
                isPlaying = playbackState == Player.STATE_READY && playerManager.isPlaying()
            }
        })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setPlayer(playerManager.player)
                    setResizeMode(aspectRatio)
                    setControllerAutoShow(false)
                    setControllerShowTimeoutMs(0)
                    useController = false
                }
            },
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            showControls = !showControls
                        },
                        onDoubleTap = { offset ->
                            val width = context.resources.displayMetrics.widthPixels
                            val current = playerManager.getCurrentPosition()
                            if (offset.x < width / 2f) {
                                playerManager.seekTo((current - 10000).coerceAtLeast(0))
                            } else {
                                playerManager.seekTo(current + 10000)
                            }
                        }
                    )
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = { },
                        onDrag = { change, dragAmount ->
                            val width = context.resources.displayMetrics.widthPixels
                            val height = context.resources.displayMetrics.heightPixels
                            val dx = dragAmount.x
                            val dy = dragAmount.y
                            val touchX = change.position.x
                            val touchY = change.position.y

                            if (touchX < width / 3f) {
                                // Trái: điều chỉnh độ sáng (giả lập)
                                val brightnessChange = (dy * 0.01f).toFloat()
                                val window = (context as android.app.Activity).window
                                val attrs = window.attributes
                                attrs.screenBrightness = (attrs.screenBrightness + brightnessChange).coerceIn(0f, 1f)
                                window.attributes = attrs
                            } else if (touchX > width * 2 / 3f) {
                                // Phải: điều chỉnh âm lượng (giả lập)
                                // Trong thực tế cần AudioManager, ở đây chỉ hiển thị thông báo
                            } else {
                                // Giữa: seek video
                                val seekDelta = (dx * 500).toInt()
                                playerManager.seekTo((playerManager.getCurrentPosition() + seekDelta).coerceAtLeast(0))
                            }
                        }
                    )
                }
        )

        if (showControls && !isLocked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            ) {
                Text(
                    text = video.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${playerManager.getCurrentPosition() / 1000}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${playerManager.getDuration() / 1000}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Slider(
                    value = playerManager.getCurrentPosition().toFloat(),
                    onValueChange = { playerManager.seekTo(it.toLong()) },
                    valueRange = 0f..playerManager.getDuration().toFloat().coerceAtLeast(1f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    Button(
                        onClick = {
                            if (isPlaying) playerManager.pause() else playerManager.resume()
                            isPlaying = !isPlaying
                        }
                    ) {
                        Text(if (isPlaying) "Tạm dừng" else "Phát")
                    }
                    Button(
                        onClick = {
                            playbackSpeed = if (playbackSpeed == 1f) 1.5f else 1f
                            playerManager.setPlaybackSpeed(playbackSpeed)
                        }
                    ) {
                        Text("x$playbackSpeed")
                    }
                    Button(
                        onClick = { isLocked = !isLocked }
                    ) {
                        Text(if (isLocked) "Mở khóa" else "Khóa màn hình")
                    }
                    Button(onClick = onBack) {
                        Text(text = "Quay lại")
                    }
                }
            }
        }
    }
}
