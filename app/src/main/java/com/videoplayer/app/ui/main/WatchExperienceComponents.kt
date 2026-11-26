package com.videoplayer.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

@Composable
fun HomeFeedFilterTabs(
    activeFilter: HomeFeedFilter,
    watchLaterCount: Int,
    historyCount: Int,
    onSelectFilter: (HomeFeedFilter) -> Unit,
    onClearHistory: () -> Unit
) {
    val style = LocalGlassStyle.current
    val palette = style.palette
    val pillCorner = if (style.isProMonitor) 6.dp else 50.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(pillCorner))
                .background(if (style.isLightMode) Color(0xFF0F172A).copy(alpha = 0.06f) else Color.White.copy(alpha = 0.06f))
                .border(0.7.dp, style.cardBorderColor, RoundedCornerShape(pillCorner))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf(
                Triple(HomeFeedFilter.FOLDERS, "Th\u01B0 m\u1EE5c", AppIcons.Folder),
                Triple(HomeFeedFilter.WATCH_LATER, if (watchLaterCount > 0) "Xem sau ($watchLaterCount)" else "Xem sau", AppIcons.WatchLater),
                Triple(HomeFeedFilter.HISTORY, if (historyCount > 0) "L\u1ECBch s\u1EED ($historyCount)" else "L\u1ECBch s\u1EED", AppIcons.History)
            )

            tabs.forEach { (tab, label, icon) ->
                val selected = (activeFilter == tab)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(pillCorner))
                        .background(
                            when {
                                selected && style.isLightMode -> palette.primaryGlow
                                selected -> palette.primaryGlow.copy(alpha = 0.24f)
                                else -> Color.Transparent
                            }
                        )
                        .clickable { onSelectFilter(tab) }
                        .padding(vertical = 7.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val contentColor = when {
                        selected && style.isLightMode -> Color.White
                        selected -> palette.primaryGlow
                        else -> style.textSecondary
                    }
                    Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = if (selected) (if (style.isLightMode) Color.White else style.textPrimary) else style.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (activeFilter == HomeFeedFilter.HISTORY && historyCount > 0) {
            LiquidGlassIconButton(onClick = onClearHistory, size = 34.dp) {
                Icon(Icons.Default.Delete, contentDescription = "X\u00F3a l\u1ECBch s\u1EED", tint = Color(0xFFFF5C8A), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ContinueWatchingCarousel(
    items: List<ContinueWatchingItem>,
    watchLaterIds: Set<String>,
    onPlayItem: (VideoItem) -> Unit,
    onRemoveFromContinue: (VideoItem) -> Unit
) {
    if (items.isEmpty()) return
    val style = LocalGlassStyle.current
    val palette = style.palette

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Ti\u1EBFp t\u1EE5c xem",
            style = MaterialTheme.typography.titleSmall,
            color = style.textPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(items, key = { "cw_${it.video.id}" }) { cw ->
                LiquidGlassCard(
                    cornerRadius = style.cardCorner,
                    highlighted = false,
                    onClick = { onPlayItem(cw.video) },
                    modifier = Modifier.width(184.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box {
                            VideoThumbnailView(
                                video = cw.video,
                                progressFraction = cw.progressFraction,
                                isWatchLater = cw.video.id in watchLaterIds,
                                modifier = Modifier.fillMaxWidth().height(94.dp)
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
                                Icon(Icons.Default.Close, contentDescription = "\u1EA8n", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = cw.video.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = style.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(cw.progressFraction * 100).toInt()}% \u2022 ${cw.video.folder}",
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