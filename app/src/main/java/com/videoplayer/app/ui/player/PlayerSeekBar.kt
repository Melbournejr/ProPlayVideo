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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.VideoBookmark
import com.videoplayer.app.ui.components.LocalGlassStyle

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
    val style = LocalGlassStyle.current
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val animatedTrackScale by animateFloatAsState(
        targetValue = if (isDragging) 1.5f else 1f,
        animationSpec = spring(),
        label = "seek_morph"
    )

    val fraction = if (isDragging) dragFraction else (currentPosMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedMs = if (isDragging) (dragFraction * safeDuration).toLong() else currentPosMs

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
                val centerY = size.height / 2f
                val thumbX = (fraction * size.width).coerceIn(0f, size.width)

                when {
                    // PRO MONITOR: Thanh tua chia vach thuoc (Ruler Ticks) + kim chi doc
                    style.isProMonitor -> {
                        val tickCount = 32
                        val stepX = size.width / tickCount
                        for (i in 0..tickCount) {
                            val tx = i * stepX
                            val isMajor = (i % 4 == 0)
                            val tickH = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                            drawLine(
                                color = if (tx <= thumbX) activeColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.25f),
                                start = Offset(tx, centerY - tickH / 2f),
                                end = Offset(tx, centerY + tickH / 2f),
                                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx()
                            )
                        }
                        drawLine(
                            color = activeColor,
                            start = Offset(0f, centerY),
                            end = Offset(thumbX, centerY),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        // Kim chi doc vuong vuc
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(thumbX - 2.dp.toPx(), centerY - 9.dp.toPx()),
                            size = Size(4.dp.toPx(), 18.dp.toPx())
                        )
                    }

                    // CINEMA SHOWCASE: Thanh tua sieu manh kieu Netflix
                    style.isCinema -> {
                        val h = 3.dp.toPx() * animatedTrackScale
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.25f),
                            topLeft = Offset(0f, centerY - h / 2f),
                            size = Size(size.width, h),
                            cornerRadius = CornerRadius(h / 2f, h / 2f)
                        )
                        if (thumbX > 1f) {
                            drawRoundRect(
                                color = activeColor,
                                topLeft = Offset(0f, centerY - h / 2f),
                                size = Size(thumbX, h),
                                cornerRadius = CornerRadius(h / 2f, h / 2f)
                            )
                        }
                        drawCircle(color = activeColor, radius = if (isDragging) 8.dp.toPx() else 5.5.dp.toPx(), center = Offset(thumbX, centerY))
                    }

                    // PURE AMOLED: Duong vector manh sac net + nut vuong toi gian
                    style.isPureAmoled -> {
                        drawLine(color = Color.White.copy(alpha = 0.25f), start = Offset(0f, centerY), end = Offset(size.width, centerY), strokeWidth = 1.5.dp.toPx())
                        drawLine(color = activeColor, start = Offset(0f, centerY), end = Offset(thumbX, centerY), strokeWidth = 2.5.dp.toPx())
                        val boxSz = if (isDragging) 12.dp.toPx() else 8.dp.toPx()
                        drawRect(color = activeColor, topLeft = Offset(thumbX - boxSz / 2f, centerY - boxSz / 2f), size = Size(boxSz, boxSz))
                    }

                    // APPLE LIQUID GLASS: Ranh kinh 120Hz phat sang
                    else -> {
                        val trackHeight = 5.dp.toPx() * animatedTrackScale
                        val cr = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                        drawRoundRect(color = Color.White.copy(alpha = 0.16f), topLeft = Offset(0f, centerY - trackHeight / 2f), size = Size(size.width, trackHeight), cornerRadius = cr)
                        drawRoundRect(color = Color.White.copy(alpha = 0.25f), topLeft = Offset(0f, centerY - trackHeight / 2f), size = Size(size.width, trackHeight), cornerRadius = cr, style = Stroke(width = 0.6.dp.toPx()))
                        if (thumbX > 1f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(listOf(activeColor.copy(alpha = 0.85f), Color.White)),
                                topLeft = Offset(0f, centerY - trackHeight / 2f),
                                size = Size(thumbX, trackHeight),
                                cornerRadius = cr
                            )
                        }
                        if (isDragging) {
                            drawCircle(color = activeColor.copy(alpha = 0.32f), radius = 14.dp.toPx(), center = Offset(thumbX, centerY))
                        }
                        drawCircle(color = Color.White, radius = if (isDragging) 8.5.dp.toPx() else 6.5.dp.toPx(), center = Offset(thumbX, centerY))
                    }
                }

                // Vach danh dau Bookmark mau vang
                bookmarks.forEach { bm ->
                    val bmFrac = (bm.timestampMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    drawCircle(color = Color(0xFFFFEA00), radius = 3.5.dp.toPx(), center = Offset(bmFrac * size.width, centerY))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTimeMs(displayedMs),
                fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                color = if (isDragging) activeColor else Color.White.copy(alpha = 0.92f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (sleepRemainingSec > 0) {
                Text(
                    text = "H\u1EB9n gi\u1EDD: ${formatTimeMs(sleepRemainingSec * 1000L)}",
                    fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = formatTimeMs(durationMs),
                fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}