package com.videoplayer.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.VideoBookmark

/**
 * Thanh tua Liquid Glass 120Hz:
 * - Khi chạm kéo, rãnh kính tự động phồng nhẹ (Morph) từ 5.dp lên 8.dp kèm hào quang khúc xạ.
 * - Viền sáng mỏng (Specular Rim) bao quanh rãnh trượt.
 */
@Composable
fun DirectCanvasSeekBar(
    currentPosMs: Long,
    durationMs: Long,
    sleepRemainingSec: Int,
    bookmarks: List<VideoBookmark> = emptyList(),
    activeColor: Color,
    onDragStart: () -> Unit,
    onDragEnd: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val animatedTrackScale by animateFloatAsState(
        targetValue = if (isDragging) 1.55f else 1f,
        animationSpec = spring(),
        label = "seek_morph"
    )

    val fraction = if (isDragging) {
        dragFraction
    } else {
        (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayedMs = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        currentPosMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(safeDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        onDragStart()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (down.position.x / w).coerceIn(0f, 1f)

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                            }
                        } while (event.changes.any { it.pressed })

                        val targetMs = (dragFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                        isDragging = false
                        onDragEnd(targetMs)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                val trackHeight = 5.dp.toPx() * animatedTrackScale
                val centerY = size.height / 2f
                val thumbX = (fraction * size.width).coerceIn(0f, size.width)
                val cr = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                // Rãnh nền kính mờ + viền sáng mỏng
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = cr
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.25f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = cr,
                    style = Stroke(width = 0.6.dp.toPx())
                )

                // Dải tiến trình phát sáng (Liquid Glow Fill)
                if (thumbX > 1f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(activeColor.copy(alpha = 0.85f), Color.White)
                        ),
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(thumbX, trackHeight),
                        cornerRadius = cr
                    )
                }

                // Các vạch đánh dấu Bookmark
                bookmarks.forEach { bm ->
                    val bmFrac = (bm.timestampMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val bmX = bmFrac * size.width
                    drawCircle(
                        color = Color(0xFFFFEA00),
                        radius = 3.8.dp.toPx(),
                        center = Offset(bmX, centerY)
                    )
                }

                // Quầng khúc xạ quanh nút kéo khi chạm
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.32f),
                        radius = 14.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                }
                // Hạt thủy tinh (Glass Bead Thumb)
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 8.5.dp.toPx() else 6.5.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDragging) "\u23F1\uFE0F ${formatTimeMs(displayedMs)}" else formatTimeMs(displayedMs),
                color = if (isDragging) activeColor else Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (sleepRemainingSec > 0) {
                Text(
                    text = "\uD83D\uDCA4 ${formatTimeMs(sleepRemainingSec * 1000L)}",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}