package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerControlsOverlay(
    state: PlayerStateHolder,
    activity: Activity?,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current

    if (state.showControls && !state.isLocked && state.activeModal == ActiveModal.NONE) {
        LiquidGlassCard(
            cornerRadius = 28.dp,
            highlighted = true,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // HÀNG 1: QUAY LẠI (←) + TIÊU ĐỀ + CHỈ SỐ A-B / ZOOM / SỐ TẬP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { state.persistCurrentPosition(); state.onBack() },
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
                            onClick = { state.zoomScale = 1.0f; state.zoomOffsetX = 0f; state.zoomOffsetY = 0f },
                            modifier = Modifier.padding(start = 4.dp)
                        ) { Text("🔍 ${(state.zoomScale * 100).roundToInt()}% ✕", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                    }
                    if (state.effectivePlaylist.size > 1 && state.currentIndex >= 0) {
                        LiquidGlassPill(active = false, modifier = Modifier.padding(start = 6.dp)) {
                            Text("${state.currentIndex + 1}/${state.effectivePlaylist.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // HÀNG 2: THANH TUA CANVAS 120HZ
                PlayerSeekBar(
                    currentPosMs = state.currentPos,
                    durationMs = state.duration,
                    sleepRemainingSec = state.sleepRemainingSec,
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

                // HÀNG 3: CÁC NÚT ĐIỀU HƯỚNG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Tốc độ phát (0.5x .. 2.0x)
                    Box {
                        LiquidGlassPill(
                            active = state.playbackSpeed != 1.0f,
                            onClick = { state.showSpeedMenu = true }
                        ) {
                            Text("${state.playbackSpeed}x", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(expanded = state.showSpeedMenu, onDismissRequest = { state.showSpeedMenu = false }) {
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

                    // Cụm Trước - Play/Pause - Sau
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { state.triggerPreviousVideo() },
                            enabled = state.currentIndex > 0,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                AppIcons.SkipPrevious,
                                contentDescription = "Trước",
                                tint = if (state.currentIndex > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (state.isPlaying) {
                                    state.playerManager.pause()
                                    state.persistCurrentPosition()
                                } else {
                                    state.playerManager.resume()
                                }
                                state.isPlaying = !state.isPlaying
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(palette.primaryGlow.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                if (state.isPlaying) AppIcons.Pause else AppIcons.PlayCircleFilled,
                                contentDescription = null,
                                tint = Color(0xFF001F29)
                            )
                        }

                        IconButton(
                            onClick = { state.triggerNextVideo() },
                            enabled = state.effectivePlaylist.isNotEmpty() && state.currentIndex in 0 until state.effectivePlaylist.size - 1,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                AppIcons.SkipNext,
                                contentDescription = "Tiếp",
                                tint = if (state.effectivePlaylist.isNotEmpty() && state.currentIndex in 0 until state.effectivePlaylist.size - 1) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Cụm Xoay + Studio + Thêm
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        IconButton(
                            onClick = { state.toggleScreenOrientation(activity) },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                        ) {
                            Icon(AppIcons.ScreenRotation, contentDescription = "Xoay màn hình", tint = Color.White, modifier = Modifier.size(17.dp))
                        }

                        LiquidGlassPill(
                            active = state.loopPointA != null || state.isMirrorFlipped || state.audioBoostPercent > 0 || state.bassBoostPercent > 0 || abs(state.videoSaturation - 1f) > 0.05f,
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