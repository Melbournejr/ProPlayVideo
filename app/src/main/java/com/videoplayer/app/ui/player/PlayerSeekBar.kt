package com.videoplayer.app.ui.player

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun PlayerSeekBar(
    currentPosMs: Long,
    durationMs: Long,
    sleepRemainingSec: Int,
    activeColor: Color,
    onDragStart: () -> Unit,
    onDragEnd: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

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
                .height(36.dp)
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
            Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                val trackHeight = if (isDragging) 7.dp.toPx() else 5.dp.toPx()
                val centerY = size.height / 2f
                val thumbX = (fraction * size.width).coerceIn(0f, size.width)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.24f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(thumbX, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )
                if (isDragging) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.35f),
                        radius = 13.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 9.dp.toPx() else 7.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isDragging) "⏱️ ${formatTimeMs(displayedMs)}" else formatTimeMs(displayedMs),
                color = if (isDragging) activeColor else Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            if (sleepRemainingSec > 0) {
                Text(
                    text = "💤 ${formatTimeMs(sleepRemainingSec * 1000L)}",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}