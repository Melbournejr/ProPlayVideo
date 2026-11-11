package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import kotlin.math.roundToInt

@Composable
fun BoxScope.PlayerOverlaysAndControls(
    state: PlayerStateHolder,
    activity: Activity?,
    onBack: () -> Unit
) {
    val palette = LocalGlassPalette.current

    // 1. Nerd Stats HUD (Góc trên bên trái)
    if (state.showNerdStats && !state.isInPipMode) {
        state.nerdStats?.let { st ->
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
                        Text("✕", color = Color.White, modifier = Modifier.padding(2.dp).clickable { state.showNerdStats = false })
                    }
                    Text("• ${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp)
                    Text("• Video: ${st.videoCodec} • ${st.videoBitrate}", color = Color.White, fontSize = 11.sp)
                    Text("• Audio: ${st.audioCodec} • Drop: ${st.droppedFrames}", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }

    // 2. Bong bóng hiển thị thông báo Gesture ở chính giữa
    if (!state.isInPipMode) {
        state.gestureHint?.let { hint ->
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

    // 3. Nút Mở khóa cảm ứng khi đang ở chế độ Khóa trẻ em / Khóa chạm
    if (state.isLocked && state.showControls && !state.isInPipMode) {
        LiquidGlassPill(
            active = true,
            onClick = { state.isLocked = false },
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
        ) {
            Icon(AppIcons.LockOpen, contentDescription = "Mở khóa", tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Chạm để Mở khóa", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    // 4. Thanh điều khiển chính dưới đáy (Liquid Glass Bottom HUD)
    if (state.showControls && !state.isLocked && !state.isInPipMode && state.activeModal == ActiveModal.NONE) {
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
                // Hàng 1: Nút Quay lại + Tiêu đề + Chip trạng thái A-B / Zoom / Tập
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { state.persistCurrentPosition(); onBack() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = state.currentVideo?.displayName ?: (state.streamUrl ?: "Đang phát"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.loopPointA != null || state.loopPointB != null) {
                        LiquidGlassPill(
                            active = true,
                            onClick = { state.loopPointA = null; state.loopPointB = null },
                            modifier = Modifier.padding(start = 4.dp)
                        ) { Text("🔁 A-B ✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (state.zoomScale > 1.01f) {
                        LiquidGlassPill(
                            active = true,
                            onClick = { state.resetZoom() },
                            modifier = Modifier.padding(start = 4.dp)
                        ) { Text("🔍 ${(state.zoomScale * 100).roundToInt()}% ✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (state.effectivePlaylist.size > 1 && state.currentIndex >= 0) {
                        LiquidGlassPill(active = false, modifier = Modifier.padding(start = 6.dp)) {
                            Text("${state.currentIndex + 1}/${state.effectivePlaylist.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Hàng 2: Thanh tua Canvas 120Hz
                DirectCanvasSeekBar(
                    currentPosMs = state.currentPos,
                    durationMs = state.duration,
                    sleepRemainingSec = state.sleepRemainingSec,
                    bookmarks = state.bookmarks,
                    activeColor = palette.primaryGlow,
                    onDragStart = { state.isDraggingSlider = true },
                    onDragEnd = { targetMs ->
                        state.currentPos = targetMs
                        state.lastSeekCompletedTimeMs = System.currentTimeMillis()
                        state.isDraggingSlider = false
                        state.playerManager.seekToFast(targetMs)
                        state.persistCurrentPosition()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Hàng 3: Cụm nút chức năng (Tốc độ | Trước - Phát/Dừng - Tiếp | Xoay - Studio - Thêm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        LiquidGlassPill(
                            active = state.playbackSpeed != 1.0f,
                            onClick = { state.showSpeedMenu = true }
                        ) {
                            Text("${state.playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(
                            expanded = state.showSpeedMenu,
                            onDismissRequest = { state.showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = {
                                        val label = if (spd == 1.0f) "1.0x (Chuẩn)" else "${spd}x"
                                        Text(if (state.playbackSpeed == spd) "✓ $label" else "    $label")
                                    },
                                    onClick = {
                                        state.playbackSpeed = spd
                                        state.playerManager.setPlaybackSpeed(spd)
                                        state.showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { state.triggerPreviousVideo() },
                            enabled = state.hasPrevious,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                AppIcons.SkipPrevious,
                                contentDescription = "Trước",
                                tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.3f)
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
                            IconButton(onClick = { state.togglePlayPause() }) {
                                Icon(
                                    if (state.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Phát/Tạm dừng",
                                    tint = Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { state.triggerNextVideo() },
                            enabled = state.hasNext,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                AppIcons.SkipNext,
                                contentDescription = "Tiếp",
                                tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = {
                                state.refreshBookmarksForCurrentVideo()
                                state.activeModal = ActiveModal.BOOKMARKS
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.bookmarks.isNotEmpty()) palette.primaryGlow.copy(alpha = 0.28f)
                                    else Color.White.copy(alpha = 0.14f)
                                )
                        ) {
                            Icon(
                                AppIcons.Bookmark,
                                contentDescription = "Đánh dấu Bookmark",
                                tint = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { state.toggleScreenOrientation(activity) },
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
                            active = state.isStudioEffectActive,
                            onClick = { state.activeModal = ActiveModal.STUDIO }
                        ) {
                            Icon(AppIcons.AutoAwesome, contentDescription = "Studio", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Studio", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }

                        LiquidGlassPill(
                            active = state.backgroundAudioEnabled || state.sleepTimerMinutes > 0,
                            onClick = { state.activeModal = ActiveModal.TOOLS }
                        ) {
                            Icon(AppIcons.Tune, contentDescription = "Thêm", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}