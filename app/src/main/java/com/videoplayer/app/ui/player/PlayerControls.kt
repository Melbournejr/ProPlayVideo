package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * Giao dien Thanh dieu khien Player da duoc can chinh lai khoang cach (Ergonomic 2-Row Layout):
 * - Hang Header: Nut Back + Tieu de video + Chip trang thai + Nut Bookmark + Nut Xoay man hinh
 * - Hang SeekBar: Thanh keo 120Hz DirectCanvasSeekBar
 * - Hang Action: Cum trai (Toc do 1.0x) | Cum giua (Prev - Play 50dp - Next) | Cum phai (Studio & Menu)
 */
@Composable
fun BoxScope.PlayerOverlaysAndControls(
    state: PlayerStateHolder,
    activity: Activity?,
    onBack: () -> Unit
) {
    val palette = LocalGlassPalette.current

    // 1. Nerd Stats HUD
    if (state.showNerdStats && !state.isInPipMode) {
        state.nerdStats?.let { st ->
            LiquidGlassCard(
                cornerRadius = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .widthIn(max = 260.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("\uD83D\uDCCA NERD STATS", color = palette.primaryGlow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        Text("\u2715", color = Color.White, modifier = Modifier.padding(2.dp).clickable { state.showNerdStats = false })
                    }
                    Text("\u2022 ${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp)
                    Text("\u2022 Video: ${st.videoCodec} \u2022 ${st.videoBitrate}", color = Color.White, fontSize = 11.sp)
                    Text("\u2022 Audio: ${st.audioCodec} \u2022 Drop: ${st.droppedFrames}", color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }

    // 2. Bong bong thong bao Gesture o giua man hinh
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

    // 3. Nut Mo khoa cam ung
    if (state.isLocked && state.showControls && !state.isInPipMode) {
        LiquidGlassPill(
            active = true,
            onClick = { state.isLocked = false },
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
        ) {
            Icon(AppIcons.LockOpen, contentDescription = "M\u1EDF kh\u00F3a", tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Ch\u1EA1m \u0111\u1EC3 M\u1EDF kh\u00F3a", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    // 4. Thanh dieu khien chinh duoi day
    if (state.showControls && !state.isLocked && !state.isInPipMode && state.activeModal == ActiveModal.NONE) {
        LiquidGlassCard(
            cornerRadius = 28.dp,
            highlighted = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // ================= HANG 1: BACK + TIEU DE + BOOKMARK + XOAY MAN HINH =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { state.persistCurrentPosition(); onBack() },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Quay l\u1EA1i",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = state.currentVideo?.displayName ?: (state.streamUrl ?: "\u0110ang ph\u00E1t"),
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
                            onClick = { state.loopPointA = null; state.loopPointB = null }
                        ) {
                            Text("\uD83D\uDD01 A-B \u2715", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (state.zoomScale > 1.01f) {
                        LiquidGlassPill(
                            active = true,
                            onClick = { state.resetZoom() }
                        ) {
                            Text("\uD83D\uDD0D ${(state.zoomScale * 100).roundToInt()}% \u2715", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (state.effectivePlaylist.size > 1 && state.currentIndex >= 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "${state.currentIndex + 1}/${state.effectivePlaylist.size}",
                                color = palette.primaryGlow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Nut Bookmark (Danh dau moc thoi gian)
                    IconButton(
                        onClick = {
                            state.refreshBookmarksForCurrentVideo()
                            state.activeModal = ActiveModal.BOOKMARKS
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.bookmarks.isNotEmpty()) palette.primaryGlow.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.12f)
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            AppIcons.Bookmark,
                            contentDescription = "\u0110\u00E1nh d\u1EA5u",
                            tint = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Nut Xoay man hinh
                    IconButton(
                        onClick = { state.toggleScreenOrientation(activity) },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            AppIcons.ScreenRotation,
                            contentDescription = "Xoay m\u00E0n h\u00ECnh",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ================= HANG 2: THANH TUA CANVAS 120HZ =================
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

                Spacer(modifier = Modifier.height(10.dp))

                // ================= HANG 3: 3 CUM DIEU KHIEN DOI XUNG, THOANG DANG =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cum 1 (Trai): Toc do phat (0.5x - 2.0x)
                    Box {
                        LiquidGlassPill(
                            active = state.playbackSpeed != 1.0f,
                            onClick = { state.showSpeedMenu = true },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "${state.playbackSpeed}x",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(
                            expanded = state.showSpeedMenu,
                            onDismissRequest = { state.showSpeedMenu = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                DropdownMenuItem(
                                    text = {
                                        val label = if (spd == 1.0f) "1.0x (Chu\u1EA9n)" else "${spd}x"
                                        Text(if (state.playbackSpeed == spd) "\u2713 $label" else "    $label")
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

                    // Cum 2 (Giua): Prev - Play/Pause (50dp) - Next voi khoang cach 12.dp de bam
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = { state.triggerPreviousVideo() },
                            enabled = state.hasPrevious,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (state.hasPrevious) 0.12f else 0.04f))
                        ) {
                            Icon(
                                AppIcons.SkipPrevious,
                                contentDescription = "Video tr\u01B0\u1EDBc",
                                tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .drawWithCache {
                                    val r = size.width / 2f
                                    val fill = Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.45f),
                                            palette.primaryGlow.copy(alpha = 0.80f),
                                            palette.secondaryGlow.copy(alpha = 0.80f)
                                        )
                                    )
                                    val rim = Brush.linearGradient(
                                        listOf(Color.White, Color.White.copy(alpha = 0.35f))
                                    )
                                    onDrawBehind {
                                        drawCircle(brush = fill, radius = r)
                                        drawCircle(brush = rim, radius = r - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { state.togglePlayPause() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    if (state.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Ph\u00E1t / T\u1EA1m d\u1EEBng",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { state.triggerNextVideo() },
                            enabled = state.hasNext,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (state.hasNext) 0.12f else 0.04f))
                        ) {
                            Icon(
                                AppIcons.SkipNext,
                                contentDescription = "Video ti\u1EBFp",
                                tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Cum 3 (Phai): Studio & Menu Tien ich (Cach nhau 8.dp gon gang)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassPill(
                            active = state.isStudioEffectActive,
                            onClick = { state.activeModal = ActiveModal.STUDIO },
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                AppIcons.AutoAwesome,
                                contentDescription = "Studio",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Studio",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        IconButton(
                            onClick = { state.activeModal = ActiveModal.TOOLS },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (state.backgroundAudioEnabled || state.sleepTimerMinutes > 0)
                                        palette.primaryGlow.copy(alpha = 0.30f)
                                    else Color.White.copy(alpha = 0.14f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (state.backgroundAudioEnabled || state.sleepTimerMinutes > 0)
                                        palette.primaryGlow
                                    else Color.White.copy(alpha = 0.28f),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                AppIcons.Tune,
                                contentDescription = "T\u00F9y ch\u1ECDn & Ph\u1EE5 \u0111\u1EC1",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}