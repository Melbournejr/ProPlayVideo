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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoplayer.app.ui.components.*
import kotlin.math.roundToInt

/**
 * Player Functional Overlay (Tuan thu Apple Liquid Glass HIG 2025-2026):
 * 1. Gesture HUD (Do sang / Am luong / Tua): Dung LiquidGlassVariant.CLEAR mong nhe, trong suot cao.
 * 2. Bottom Control Bar: Mot khoi LiquidGlassVariant.REGULAR duy nhat, bo goc Concentric 28.dp,
 *    xuat hien muot voi hieu ung Morph (fadeIn + scaleIn + slideInVertically).
 * 3. Khong ve Glass-on-Glass ben trong Control Bar.
 */
@Composable
fun BoxScope.PlayerOverlaysAndControls(
    state: PlayerStateHolder,
    activity: Activity?,
    onBack: () -> Unit
) {
    val palette = LocalGlassPalette.current

    // 1. Nerd Stats HUD (Bien the Clear Glass o goc tren trai)
    AnimatedVisibility(
        visible = state.showNerdStats && !state.isInPipMode,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
    ) {
        state.nerdStats?.let { st ->
            LiquidGlassBar(
                variant = LiquidGlassVariant.CLEAR,
                cornerRadius = 18.dp,
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

    // 2. Gesture HUD Overlay (Bien the CLEAR Glass mong nhe, khong dac, lo ro video phia sau)
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
                highlighted = true
            ) {
                Text(
                    text = hint,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }

    // 3. Nut Mo khoa cam ung (Liquid Glass Clear Pill)
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

    // 4. Khoi Thanh Dieu Khien Chinh Duoi Day (Mot khoi LiquidGlassBar REGULAR duy nhat + Hieu ung Morph)
    AnimatedVisibility(
        visible = state.showControls && !state.isLocked && !state.isInPipMode && state.activeModal == ActiveModal.NONE,
        enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) +
            slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)),
        exit = fadeOut() + scaleOut(targetScale = 0.95f) + slideOutVertically(targetOffsetY = { it / 3 }),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
    ) {
        LiquidGlassBar(
            variant = LiquidGlassVariant.REGULAR,
            cornerRadius = 28.dp,
            highlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // ================= TANG 1: BACK + TIEU DE + TRANG THAI + BOOKMARK + XOAY =================
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
                            .background(Color.White.copy(alpha = 0.08f))
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
                                .background(Color.White.copy(alpha = 0.08f))
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

                    IconButton(
                        onClick = {
                            state.refreshBookmarksForCurrentVideo()
                            state.activeModal = ActiveModal.BOOKMARKS
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.bookmarks.isNotEmpty()) palette.primaryGlow.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                    ) {
                        Icon(
                            AppIcons.Bookmark,
                            contentDescription = "\u0110\u00E1nh d\u1EA5u",
                            tint = if (state.bookmarks.isNotEmpty()) palette.primaryGlow else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { state.toggleScreenOrientation(activity) },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
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

                // ================= TANG 2: THANH SEEK BAR 120HZ =================
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

                // ================= TANG 3: 3 CUM DIEU KHIEN CAN DOI CHUẨN NHIP APPLE HIG =================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cum Trai: Toc do phat
                    Box {
                        LiquidGlassPill(
                            active = state.playbackSpeed != 1.0f,
                            onClick = { state.showSpeedMenu = true },
                            modifier = Modifier.height(38.dp)
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

                    // Cum Giua: Prev - Play/Pause (50dp) - Next voi khoang cach 16.dp thoang dang
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { state.triggerPreviousVideo() },
                            enabled = state.hasPrevious,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (state.hasPrevious) 0.08f else 0.02f))
                        ) {
                            Icon(
                                AppIcons.SkipPrevious,
                                contentDescription = "Video tr\u01B0\u1EDBc",
                                tint = if (state.hasPrevious) Color.White else Color.White.copy(alpha = 0.28f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Nut Play/Pause trung tam: Dung Specular Tint sang nhe, khong chong lop kinh thu 2
                        IconButton(
                            onClick = { state.togglePlayPause() },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                if (state.isPlaying) AppIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Ph\u00E1t / T\u1EA1m d\u1EEBng",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(
                            onClick = { state.triggerNextVideo() },
                            enabled = state.hasNext,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (state.hasNext) 0.08f else 0.02f))
                        ) {
                            Icon(
                                AppIcons.SkipNext,
                                contentDescription = "Video ti\u1EBFp",
                                tint = if (state.hasNext) Color.White else Color.White.copy(alpha = 0.28f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Cum Phai: Studio & Menu Tien ich
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
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Studio",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
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
                                        palette.primaryGlow.copy(alpha = 0.25f)
                                    else Color.White.copy(alpha = 0.09f)
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