package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoplayer.app.data.local.StreamHistoryManager
import com.videoplayer.app.ui.components.*
import kotlin.math.roundToInt

@Composable
fun BoxScope.PlayerOverlaysAndControls(
    state: PlayerStateHolder,
    activity: Activity?,
    onBack: () -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette

    // 1. Nerd Stats HUD
    AnimatedVisibility(
        visible = state.showNerdStats && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
    ) {
        state.nerdStats?.let { st ->
            LiquidGlassBar(
                variant = LiquidGlassVariant.CLEAR,
                cornerRadius = 16.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.widthIn(max = 260.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("NERD STATS", color = palette.primaryGlow, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text("\u2715", color = Color.White, modifier = Modifier.padding(2.dp).clickable { state.showNerdStats = false })
                    }
                    Text("${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Video: ${st.videoCodec} \u2022 ${st.videoBitrate}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("Audio: ${st.audioCodec} \u2022 Drop: ${st.droppedFrames}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    // 2. Gesture HUD Overlay
    AnimatedVisibility(
        visible = state.gestureHint != null && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 0.90f),
        modifier = Modifier.align(Alignment.Center)
    ) {
        state.gestureHint?.let { hint ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 50.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(0.9.dp, palette.primaryGlow.copy(alpha = 0.65f), RoundedCornerShape(if (style.isProMonitor) 6.dp else 50.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // 3. Nut Mo khoa cam ung
    AnimatedVisibility(
        visible = state.isLocked && state.showControls && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
    ) {
        LiquidGlassPill(active = true, variant = LiquidGlassVariant.REGULAR, onClick = { state.isLocked = false }) {
            Icon(AppIcons.LockOpen, contentDescription = "M\u1EDF kh\u00F3a", tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Ch\u1EA1m \u0111\u1EC3 M\u1EDF kh\u00F3a", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    // 4. Thanh Dieu Khien Chinh (Tu bien hoa theo 4 Bo Theme!)
    AnimatedVisibility(
        visible = state.showControls && !state.isLocked && !state.isInPipMode && state.activeModal == ActiveModal.NONE,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        val controlsContent: @Composable ColumnScope.() -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (style.isCinema) 20.dp else 16.dp,
                        vertical = if (style.isCinema) 18.dp else 13.dp
                    )
            ) {
                // Tang 1: Back + Tieu de + Bookmark + Xoay man hinh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { state.persistCurrentPosition(); onBack() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                            .background(if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay l\u1EA1i", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = state.currentVideo?.displayName
                            ?: state.streamTitle?.takeIf { it.isNotBlank() }
                            ?: state.streamUrl?.let { StreamHistoryManager.findTitleForUrl(it) }
                            ?: (state.streamUrl ?: "\u0110ang ph\u00E1t"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (state.loopPointA != null || state.loopPointB != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.primaryGlow.copy(alpha = 0.25f))
                                .clickable { state.loopPointA = null; state.loopPointB = null }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("A-B \u2715", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.zoomScale > 1.01f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.primaryGlow.copy(alpha = 0.25f))
                                .clickable { state.resetZoom() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("${(state.zoomScale * 100).roundToInt()}% \u2715", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    IconButton(
                        onClick = {
                            state.refreshBookmarksForCurrentVideo()
                            state.activeModal = ActiveModal.BOOKMARKS
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                            .background(if (state.bookmarks.isNotEmpty()) palette.primaryGlow.copy(alpha = 0.25f) else if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(AppIcons.Bookmark, contentDescription = "\u0110\u00E1nh d\u1EA5u", tint = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White, modifier = Modifier.size(16.dp))
                    }

                    IconButton(
                        onClick = { state.toggleScreenOrientation(activity) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                            .background(if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.10f))
                    ) {
                        Icon(AppIcons.ScreenRotation, contentDescription = "Xoay m\u00E0n h\u00ECnh", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tang 2: SeekBar (Tu bien hoa theo Theme)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Tang 3: 3 Cum nut can doi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 50.dp))
                                .background(if (state.playbackSpeed != 1.0f) palette.primaryGlow.copy(alpha = 0.30f) else if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.10f))
                                .clickable { state.showSpeedMenu = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${state.playbackSpeed}x",
                                fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        DropdownMenu(expanded = state.showSpeedMenu, onDismissRequest = { state.showSpeedMenu = false }) {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        IconButton(
                            onClick = { state.triggerPreviousVideo() },
                            enabled = state.hasPrevious,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (style.isCinema || style.isPureAmoled) Color.Transparent else Color.White.copy(alpha = if (state.hasPrevious) 0.10f else 0.03f))
                        ) {
                            Icon(AppIcons.SkipPrevious, null, tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.28f), modifier = Modifier.size(22.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(if (style.isProMonitor) RoundedCornerShape(8.dp) else CircleShape)
                                .background(
                                    when {
                                        style.isCinema -> Color.White.copy(alpha = 0.20f)
                                        style.isPureAmoled -> palette.primaryGlow
                                        else -> palette.primaryGlow.copy(alpha = 0.45f)
                                    }
                                )
                                .border(1.dp, Color.White.copy(alpha = if (style.isPureAmoled) 0f else 0.65f), if (style.isProMonitor) RoundedCornerShape(8.dp) else CircleShape)
                                .clickable { state.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Ph\u00E1t / T\u1EA1m d\u1EEBng",
                                tint = if (style.isPureAmoled && palette.primaryGlow == Color.White) Color.Black else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { state.triggerNextVideo() },
                            enabled = state.hasNext,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (style.isCinema || style.isPureAmoled) Color.Transparent else Color.White.copy(alpha = if (state.hasNext) 0.10f else 0.03f))
                        ) {
                            Icon(AppIcons.SkipNext, null, tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.28f), modifier = Modifier.size(22.dp))
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 50.dp))
                                .background(if (state.isStudioEffectActive) palette.primaryGlow.copy(alpha = 0.30f) else if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.10f))
                                .clickable { state.activeModal = ActiveModal.STUDIO }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Studio", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        IconButton(
                            onClick = { state.activeModal = ActiveModal.TOOLS },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(if (style.isProMonitor) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (state.backgroundAudioEnabled || state.sleepTimerMinutes > 0) palette.primaryGlow.copy(alpha = 0.30f) else if (style.isCinema) Color.Transparent else Color.White.copy(alpha = 0.10f))
                        ) {
                            Icon(AppIcons.Tune, contentDescription = "Ti\u1EC7n \u00EDch", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Boc container khac nhau hoan toan theo 4 Bo Theme!
        if (style.isCinema) {
            // CINEMA SHOWCASE: Khong khung hop, chi dung gradient den mo min o day man hinh
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.90f))
                        )
                    ),
                content = controlsContent
            )
        } else {
            // LIQUID GLASS / PRO MONITOR / PURE AMOLED: Khoi noi tu thich ung trong LiquidGlassBar
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                LiquidGlassBar(
                    variant = LiquidGlassVariant.REGULAR,
                    cornerRadius = 28.dp,
                    shadowElevation = 20.dp,
                    highlighted = true,
                    modifier = Modifier.fillMaxWidth(),
                    content = controlsContent
                )
            }
        }
    }
}