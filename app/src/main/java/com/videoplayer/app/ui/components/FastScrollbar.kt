package com.videoplayer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FastListScrollbar(
    listState: LazyListState,
    totalItemsCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItemsCount <= 6) return
    val firstVisible by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val visibleCount by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) } }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val scope = rememberCoroutineScope()

    CoreVerticalFastScrollbar(
        totalItemsCount = totalItemsCount,
        firstVisibleIndex = firstVisible,
        visibleItemsCount = visibleCount,
        isScrolling = isScrolling,
        onScrollToIndex = { targetIdx ->
            scope.launch { listState.scrollToItem(targetIdx) }
        },
        modifier = modifier
    )
}

@Composable
fun FastGridScrollbar(
    gridState: LazyGridState,
    totalItemsCount: Int,
    modifier: Modifier = Modifier
) {
    if (totalItemsCount <= 8) return
    val firstVisible by remember { derivedStateOf { gridState.firstVisibleItemIndex } }
    val visibleCount by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) } }
    val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
    val scope = rememberCoroutineScope()

    CoreVerticalFastScrollbar(
        totalItemsCount = totalItemsCount,
        firstVisibleIndex = firstVisible,
        visibleItemsCount = visibleCount,
        isScrolling = isScrolling,
        onScrollToIndex = { targetIdx ->
            scope.launch { gridState.scrollToItem(targetIdx) }
        },
        modifier = modifier
    )
}

@Composable
private fun CoreVerticalFastScrollbar(
    totalItemsCount: Int,
    firstVisibleIndex: Int,
    visibleItemsCount: Int,
    isScrolling: Boolean,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isBarVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isScrolling, isDragging) {
        if (isScrolling || isDragging) {
            isBarVisible = true
        } else {
            delay(1600L)
            isBarVisible = false
        }
    }

    val maxScrollIndex = (totalItemsCount - visibleItemsCount).coerceAtLeast(1)
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (firstVisibleIndex.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
    }

    val thumbHeightDp = 46.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
    val availableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetY = (currentFraction * availableTrackPx).roundToInt()

    val currentItemNumber = if (isDragging) {
        ((dragFraction * (totalItemsCount - 1)).roundToInt() + 1).coerceIn(1, totalItemsCount)
    } else {
        (firstVisibleIndex + 1).coerceIn(1, totalItemsCount)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(vertical = 8.dp)
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) },
        contentAlignment = Alignment.TopEnd
    ) {
        // Bong bóng hiển thị số thứ tự video đang lướt tới (ví dụ: 145 / 334)
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .offset { IntOffset(x = -34.dp.roundToPx(), y = thumbOffsetY) }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.deepBgMid.copy(alpha = 0.95f))
                    .border(1.2.dp, palette.primaryGlow, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$currentItemNumber / $totalItemsCount",
                    color = palette.primaryGlow,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Vùng cảm ứng kéo thanh trượt bên mép phải (rộng 30dp để dễ chạm tay)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(30.dp)
                .pointerInput(totalItemsCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        isDragging = true
                        isBarVisible = true
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val initialFrac = (down.position.y / h).coerceIn(0f, 1f)
                        dragFraction = initialFrac
                        onScrollToIndex((initialFrac * (totalItemsCount - 1)).roundToInt().coerceIn(0, totalItemsCount - 1))

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                change.consume()
                                val frac = (change.position.y / h).coerceIn(0f, 1f)
                                dragFraction = frac
                                val targetIdx = (frac * (totalItemsCount - 1)).roundToInt().coerceIn(0, totalItemsCount - 1)
                                onScrollToIndex(targetIdx)
                            }
                        } while (event.changes.any { it.pressed })

                        isDragging = false
                    }
                },
            contentAlignment = Alignment.TopEnd
        ) {
            // Đường rãnh mảnh dọc mép phải
            AnimatedVisibility(
                visible = isBarVisible || isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxHeight().padding(end = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                )
            }

            // Con trượt Kính Lỏng phát sáng (Thumb)
            AnimatedVisibility(
                visible = isBarVisible || isDragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = thumbOffsetY) }
                    .padding(end = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(if (isDragging) 10.dp else 7.dp)
                        .height(thumbHeightDp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White,
                                    palette.primaryGlow,
                                    palette.secondaryGlow
                                )
                            )
                        )
                        .border(0.8.dp, Color.White, CircleShape)
                )
            }
        }
    }
}