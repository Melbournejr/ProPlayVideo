package com.videoplayer.app.ui.player

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoplayer.app.ui.components.*
import kotlin.math.roundToInt

/**
 * Player Control Bar & Functional HUD (Apple Liquid Glass 5-Layer Optical):
 * - Cân chỉnh lại khoảng cách và kích thước nút bấm:
 *   + Hàng trên: Back (38dp) | Tiêu đề | Bookmark (38dp) | Xoay màn hình (38dp)
 *   + Hàng giữa: Thanh tua DirectCanvasSeekBar 120Hz
 *   + Hàng dưới: Cụm Trái [Tốc độ 1.0x] | Cụm Giữa [Prev (42dp) - Play (54dp) - Next (42dp) cách nhau 18dp] | Cụm Phải [Studio - Menu (40dp)]
 */
@Composable
fun BoxScope.PlayerOverlaysAndControls(
    state: PlayerStateHolder,
    activity: Activity?,
    onBack: () -> Unit
) {
    val palette = LocalGlassPalette.current

    // 1. Nerd Stats HUD (Clear Glass)
    AnimatedVisibility(
        visible = state.showNerdStats && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
    ) {
        state.nerdStats?.let { st ->
            LiquidGlassBar(
                variant = LiquidGlassVariant.CLEAR,
                cornerRadius = 20.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.widthIn(max = 260.dp)
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

    // 2. Gesture HUD Overlay (Clear Glass mỏng nhẹ, khúc xạ cao)
    AnimatedVisibility(
        visible = state.gestureHint != null && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.88f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 0.90f),
        modifier = Modifier.align(Alignment.Center)
    ) {
        state.gestureHint?.let { hint ->
            LiquidGlassBar(
                variant = LiquidGlassVariant.CLEAR,
                cornerRadius = 50.dp,
                shadowElevation = 16.dp,
                highlighted = true
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp)
                )
            }
        }
    }

    // 3. Nút Mở khóa cảm ứng
    AnimatedVisibility(
        visible = state.isLocked && state.showControls && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
    ) {
        LiquidGlassPill(
            active = true,
            variant = LiquidGlassVariant.REGULAR,
            onClick = { state.isLocked = false }
        ) {
            Icon(AppIcons.LockOpen, contentDescription = "M\u1EDF kh\u00F3a", tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text("Ch\u1EA1m \u0111\u1EC3 M\u1EDF kh\u00F3a", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }

    // 4. Thanh Điều Khiển Chính Dưới Đáy (5-Layer Optical LiquidGlassBar + Hiệu ứng Morph)
    AnimatedVisibility(
        visible = state.showControls && !state.isLocked && !state.isInPipMode && state.activeModal == ActiveModal.NONE,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.93f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 0.94f) + slideOutVertically(targetOffsetY = { it / 3 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
    ) {
        LiquidGlassBar(
            variant = LiquidGlassVariant.REGULAR,
            cornerRadius = 30.dp,
            shadowElevation = 22.dp,
            highlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // ================= TẦNG 1: BACK + TIÊU ĐỀ + BOOKMARK + XOAY MÀN HÌNH =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiquidGlassIconButton(
                        onClick = { state.persistCurrentPosition(); onBack() },
                        size = 36.dp
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
                        fontWeight = FontWeight.SemiBold,
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

                    LiquidGlassIconButton(
                        onClick = {
                            state.refreshBookmarksForCurrentVideo()
                            state.activeModal = ActiveModal.BOOKMARKS
                        },
                        size = 36.dp,
                        active = state.bookmarks.isNotEmpty()
                    ) {
                        Icon(
                            AppIcons.Bookmark,
                            contentDescription = "\u0110\u00E1nh d\u1EA5u",
                            tint = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    LiquidGlassIconButton(
                        onClick = { state.toggleScreenOrientation(activity) },
                        size = 36.dp
                    ) {
                        Icon(
                            AppIcons.ScreenRotation,
                            contentDescription = "Xoay m\u00E0n h\u00ECnh",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ================= TẦNG 2: THANH SEEK BAR 120HZ =================
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

                // ================= TẦNG 3: 3 CỤM NÚT CÂN ĐỐI, KHOẢNG CÁCH RỘNG DỄ BẤM =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cụm Trái: Tốc độ phát
                    Box {
                        LiquidGlassPill(
                            active = state.playbackSpeed != 1.0f,
                            onClick = { state.showSpeedMenu = true },
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = "${state.playbackSpeed}x",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
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

                    // Cụm Giữa: Prev (42dp) - Play/Pause (54dp) - Next (42dp) cách nhau 18.dp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        LiquidGlassIconButton(
                            onClick = { state.triggerPreviousVideo() },
                            size = 42.dp,
                            enabled = state.hasPrevious
                        ) {
                            Icon(
                                AppIcons.SkipPrevious,
                                contentDescription = "Video tr\u01B0\u1EDBc",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Nút Play/Pause trung tâm (Kính cầu phản quang Specular Sphere 54dp)
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .shadow(10.dp, CircleShape, spotColor = palette.primaryGlow)
                                .clip(CircleShape)
                                .clickable { state.togglePlayPause() }
                                .drawWithCache {
                                    val r = size.width / 2f
                                    val sphereBrush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.38f),
                                            palette.primaryGlow.copy(alpha = 0.55f),
                                            palette.secondaryGlow.copy(alpha = 0.45f)
                                        )
                                    )
                                    val rimBrush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.95f),
                                            Color.White.copy(alpha = 0.25f)
                                        )
                                    )
                                    onDrawBehind {
                                        drawCircle(brush = sphereBrush, radius = r)
                                        drawCircle(
                                            brush = rimBrush,
                                            radius = r - 0.8.dp.toPx(),
                                            style = Stroke(width = 1.2.dp.toPx())
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Ph\u00E1t / T\u1EA1m d\u1EEBng",
                                tint = Color.White,
                                modifier = Modifier.size(27.dp)
                            )
                        }

                        LiquidGlassIconButton(
                            onClick = { state.triggerNextVideo() },
                            size = 42.dp,
                            enabled = state.hasNext
                        ) {
                            Icon(
                                AppIcons.SkipNext,
                                contentDescription = "Video ti\u1EBFp",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Cụm Phải: Studio & Menu Tiện ích
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LiquidGlassPill(
                            active = state.isStudioEffectActive,
                            onClick = { state.activeModal = ActiveModal.STUDIO },
                            modifier = Modifier.height(40.dp)
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
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        LiquidGlassIconButton(
                            onClick = { state.activeModal = ActiveModal.TOOLS },
                            size = 40.dp,
                            active = state.backgroundAudioEnabled || state.sleepTimerMinutes > 0
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