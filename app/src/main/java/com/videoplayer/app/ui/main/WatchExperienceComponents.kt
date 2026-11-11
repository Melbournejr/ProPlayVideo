package com.videoplayer.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.ui.components.*

enum class HomeFeedFilter {
    FOLDERS, WATCH_LATER, HISTORY
}

data class ContinueWatchingItem(
    val video: VideoItem,
    val positionMs: Long,
    val durationMs: Long
) {
    val progressFraction: Float
        get() = (positionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
}

/**
 * Thanh chuyển đổi chế độ xem trên HomeScreen:
 * - 📁 Thư mục
 * - 🔖 Xem sau (Watch Later)
 * - 🕒 Lịch sử xem (Playback History)
 */
@Composable
fun HomeFeedFilterTabs(
    activeFilter: HomeFeedFilter,
    watchLaterCount: Int,
    historyCount: Int,
    onSelectFilter: (HomeFeedFilter) -> Unit,
    onClearHistory: () -> Unit
) {
    val palette = LocalGlassPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassPill(
            active = activeFilter == HomeFeedFilter.FOLDERS,
            onClick = { onSelectFilter(HomeFeedFilter.FOLDERS) }
        ) {
            Icon(AppIcons.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text("Thư mục", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }

        LiquidGlassPill(
            active = activeFilter == HomeFeedFilter.WATCH_LATER,
            onClick = { onSelectFilter(HomeFeedFilter.WATCH_LATER) }
        ) {
            Icon(AppIcons.WatchLater, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (watchLaterCount > 0) "Xem sau ($watchLaterCount)" else "Xem sau",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LiquidGlassPill(
            active = activeFilter == HomeFeedFilter.HISTORY,
            onClick = { onSelectFilter(HomeFeedFilter.HISTORY) }
        ) {
            Icon(AppIcons.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (historyCount > 0) "Lịch sử ($historyCount)" else "Lịch sử",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (activeFilter == HomeFeedFilter.HISTORY && historyCount > 0) {
            LiquidGlassPill(
                active = false,
                onClick = onClearHistory
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5C8A), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Xóa lịch sử", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Khu vực "Tiếp tục xem" (Continue Watching Carousel) hiển thị các video đang xem dở
 * kèm thanh tiến trình ngay trên Thumbnail và nút gỡ nhanh.
 */
@Composable
fun ContinueWatchingCarousel(
    items: List<ContinueWatchingItem>,
    watchLaterIds: Set<String>,
    onPlayItem: (VideoItem) -> Unit,
    onRemoveFromContinue: (VideoItem) -> Unit
) {
    if (items.isEmpty()) return
    val palette = LocalGlassPalette.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▶ Tiếp tục xem (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(items, key = { "cw_${it.video.id}" }) { cw ->
                LiquidGlassCard(
                    cornerRadius = 18.dp,
                    highlighted = true,
                    onClick = { onPlayItem(cw.video) },
                    modifier = Modifier.width(190.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box {
                            VideoThumbnailView(
                                video = cw.video,
                                progressFraction = cw.progressFraction,
                                isWatchLater = cw.video.id in watchLaterIds,
                                modifier = Modifier.fillMaxWidth().height(96.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable { onRemoveFromContinue(cw.video) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Ẩn khỏi Tiếp tục xem",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = cw.video.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Đã xem ${(cw.progressFraction * 100).toInt()}% • ${cw.video.folder}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primaryGlow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}