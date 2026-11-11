package com.videoplayer.app.ui.player

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.videoplayer.app.data.local.savePreferredBrightness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Modifier gắn toàn bộ hệ thống cử chỉ thông minh lên bề mặt Video PlayerView:
 * - Chạm đơn: Ẩn/Hiện thanh điều khiển hoặc đóng Modal
 * - Chạm đúp nửa trái/phải: Tua nhanh -10s / +10s
 * - Vuốt ngang: Tua thời gian mượt mà
 * - Vuốt dọc nửa trái: Chỉnh độ sáng khớp với độ sáng hệ thống máy
 * - Vuốt dọc nửa phải: Chỉnh âm lượng (0% -> 100%) + Khuếch đại LoudnessEnhancer (100% -> 200%)
 * - Chụm 2 ngón: Thu phóng & Kéo khung hình (100% -> 400%)
 */
fun Modifier.playerTouchGestures(
    state: PlayerStateHolder,
    activity: Activity?,
    audioManager: AudioManager
): Modifier = this
    .pointerInput(state.isLocked, state.isInPipMode) {
        if (state.isInPipMode) return@pointerInput
        detectTapGestures(
            onTap = {
                if (state.activeModal != ActiveModal.NONE) {
                    state.activeModal = ActiveModal.NONE
                } else {
                    state.showControls = !state.showControls
                }
            },
            onDoubleTap = { offset ->
                if (!state.isLocked) {
                    val width = size.width
                    val cur = state.playerManager.getCurrentPosition()
                    val dur = state.playerManager.getDuration().coerceAtLeast(1L)
                    val next = if (offset.x < width / 2f) {
                        (cur - 10_000L).coerceAtLeast(0L)
                    } else {
                        (cur + 10_000L).coerceAtMost(dur)
                    }
                    state.playerManager.seekToFast(next)
                    state.currentPos = next
                    state.lastSeekCompletedTimeMs = System.currentTimeMillis()
                    val hint = if (offset.x < width / 2f) "⏪ -10s (${formatTimeMs(next)})" else "⏩ +10s (${formatTimeMs(next)})"
                    state.showTemporaryHint(hint, 750L)
                    state.persistCurrentPosition()
                }
            }
        )
    }
    .pointerInput(state.isLocked, state.isInPipMode) {
        if (state.isInPipMode) return@pointerInput
        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = true)
            if (state.isLocked) return@awaitEachGesture
            if (state.showControls && firstDown.position.y > size.height * 0.70f) return@awaitEachGesture

            state.gestureMode = GestureMode.NONE
            val startTouchX = firstDown.position.x
            var totalDragX = 0f
            var totalDragY = 0f

            val initialSeekPos = state.playerManager.getCurrentPosition()
            var targetSeekPos = initialSeekPos

            val winBright = activity?.window?.attributes?.screenBrightness ?: -1f
            val initialBrightness = if (winBright in 0.01f..1.0f) winBright else state.currentScreenBrightness

            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val basePercent = (curVol.toFloat() / maxVol) * 100f
            val initialCombinedVol = if (basePercent >= 99f && state.audioBoostPercent > 0) {
                100f + state.audioBoostPercent
            } else {
                basePercent
            }

            do {
                val event = awaitPointerEvent()
                val pressedCount = event.changes.count { it.pressed }

                if (pressedCount >= 2) {
                    state.gestureMode = GestureMode.ZOOM
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()

                    if (zoomChange != 1f || panChange.x != 0f || panChange.y != 0f) {
                        val newScale = (state.zoomScale * zoomChange).coerceIn(1.0f, 4.0f)
                        state.zoomScale = newScale
                        if (newScale > 1.01f) {
                            val maxDx = (size.width * (newScale - 1f)) / 2f
                            val maxDy = (size.height * (newScale - 1f)) / 2f
                            state.zoomOffsetX = (state.zoomOffsetX + panChange.x * newScale).coerceIn(-maxDx, maxDx)
                            state.zoomOffsetY = (state.zoomOffsetY + panChange.y * newScale).coerceIn(-maxDy, maxDy)
                        } else {
                            state.zoomOffsetX = 0f
                            state.zoomOffsetY = 0f
                        }
                        state.gestureHint = "🔍 ${(state.zoomScale * 100).roundToInt()}%"
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressedCount == 1 && state.gestureMode != GestureMode.ZOOM) {
                    val change = event.changes.firstOrNull { it.pressed } ?: continue
                    val dragX = change.position.x - change.previousPosition.x
                    val dragY = change.position.y - change.previousPosition.y

                    if (dragX != 0f || dragY != 0f) {
                        val screenWidth = size.width.toFloat().coerceAtLeast(1f)
                        val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                        totalDragX += dragX
                        totalDragY += dragY

                        if (state.gestureMode == GestureMode.NONE) {
                            val threshold = 10f
                            if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                                state.gestureMode = if (abs(totalDragX) > abs(totalDragY) * 1.2f) {
                                    GestureMode.SEEK
                                } else {
                                    if (startTouchX < screenWidth / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                                }
                                totalDragX = 0f
                                totalDragY = 0f
                            }
                        }

                        when (state.gestureMode) {
                            GestureMode.SEEK -> {
                                change.consume()
                                val dur = state.playerManager.getDuration().coerceAtLeast(1L)
                                val maxSeekRange = minOf(dur, 120_000L).coerceAtLeast(30_000L)
                                val seekDelta = ((totalDragX / screenWidth) * maxSeekRange).toLong()
                                targetSeekPos = (initialSeekPos + seekDelta).coerceIn(0L, dur)
                                state.currentPos = targetSeekPos
                                val diffSec = (targetSeekPos - initialSeekPos) / 1000
                                val sign = if (diffSec >= 0) "+${diffSec}s" else "${diffSec}s"
                                state.gestureHint = "⏱️ ${formatTimeMs(targetSeekPos)} ($sign)"
                            }
                            GestureMode.BRIGHTNESS -> {
                                change.consume()
                                activity?.let { act ->
                                    val delta = -totalDragY / (screenHeight * 0.42f)
                                    val newBright = (initialBrightness + delta).coerceIn(0.01f, 1.0f)
                                    state.currentScreenBrightness = newBright
                                    val attrs = act.window.attributes
                                    attrs.screenBrightness = newBright
                                    act.window.attributes = attrs
                                    state.gestureHint = "☀️ Độ sáng: ${(newBright * 100).roundToInt()}%"
                                }
                            }
                            GestureMode.VOLUME -> {
                                change.consume()
                                val deltaPercent = (-totalDragY / (screenHeight * 0.75f)) * 150f
                                val newCombined = (initialCombinedVol + deltaPercent).coerceIn(0f, 200f)

                                if (newCombined <= 100f) {
                                    state.audioBoostPercent = 0
                                    state.playerManager.setAudioBoostPercent(0)
                                    val targetSysVol = ((newCombined / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSysVol, 0)
                                    state.gestureHint = "🔊 ${newCombined.roundToInt()}%"
                                } else {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
                                    val boost = (newCombined - 100f).roundToInt().coerceIn(1, 100)
                                    state.audioBoostPercent = boost
                                    state.playerManager.setAudioBoostPercent(boost)
                                    state.gestureHint = "🚀 Boost ${100 + boost}%"
                                }
                            }
                            else -> {}
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (state.gestureMode == GestureMode.SEEK) {
                state.playerManager.seekToFast(targetSeekPos)
                state.currentPos = targetSeekPos
                state.lastSeekCompletedTimeMs = System.currentTimeMillis()
                state.persistCurrentPosition()
            } else if (state.gestureMode == GestureMode.BRIGHTNESS) {
                CoroutineScope(Dispatchers.IO).launch {
                    state.context.savePreferredBrightness(state.currentScreenBrightness)
                }
            }
            state.gestureMode = GestureMode.NONE
            state.gestureHint = null
        }
    }