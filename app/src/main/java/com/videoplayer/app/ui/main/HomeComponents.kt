package com.videoplayer.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.PositionMap
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.ui.components.*

data class FolderSummary(
    val name: String,
    val folderPath: String,
    val count: Int,
    val totalSize: Long,
    val previewVideo: VideoItem
)

enum class SortOption(val label: String) {
    DATE_DESC("Mới nhất trước"),
    DATE_ASC("Cũ nhất trước"),
    SIZE_DESC("Dung lượng: Lớn -> Nhỏ"),
    SIZE_ASC("Dung lượng: Nhỏ -> Lớn"),
    DURATION_DESC("Thời lượng: Dài -> Ngắn"),
    NAME_ASC("Tên: A -> Z")
}

fun formatFolderSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb < 1024) "$mb MB" else "%.1f GB".format(mb / 1024f)
}

@Composable
fun MultiSelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    isVaultMode: Boolean,
    onToggleSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onShareSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onVaultActionSelected: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    val palette = LocalGlassPalette.current

    LiquidGlassCard(
        cornerRadius = 20.dp,
        highlighted = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đã chọn $selectedCount/$totalCount video",
                    color = palette.primaryGlow,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidGlassPill(onClick = onToggleSelectAll) {
                        Text(
                            if (selectedCount == totalCount) "Bỏ chọn hết" else "Chọn tất cả",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LiquidGlassPill(onClick = onCancel) {
                        Text("Hủy ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LiquidGlassPill(onClick = onShareSelected) {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chia sẻ", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (!isVaultMode) {
                    LiquidGlassPill(onClick = onCopySelected) {
                        Icon(AppIcons.ContentCopy, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    LiquidGlassPill(onClick = onMoveSelected) {
                        Icon(AppIcons.DriveFileMove, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chuyển", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LiquidGlassPill(onClick = onVaultActionSelected) {
                    Icon(if (isVaultMode) AppIcons.LockOpen else Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVaultMode) "Khôi phục" else "Lưu bảo mật", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(active = true, onClick = onDeleteSelected) {
                    Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5C8A), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa ($selectedCount)", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BoxScope.FolderListSection(
    folders: List<FolderSummary>,
    folderListState: LazyListState,
    continueWatchingItems: List<ContinueWatchingItem>,
    watchLaterIds: Set<String>,
    visibleVideos: List<VideoItem>,
    onSelectFolder: (String) -> Unit,
    onPlayRecent: (VideoItem, List<VideoItem>) -> Unit,
    onRemoveContinueWatching: (VideoItem) -> Unit
) {
    val palette = LocalGlassPalette.current

    LazyColumn(
        state = folderListState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
                if (continueWatchingItems.isNotEmpty()) {
            item(key = "continue_watching_carousel") {
                ContinueWatchingCarousel(
                    items = continueWatchingItems,
                    watchLaterIds = watchLaterIds,
                    onPlayItem = { vid ->
                        val folderPlaylist = visibleVideos.filter { it.folder == vid.folder }
                        onPlayRecent(vid, folderPlaylist.ifEmpty { listOf(vid) })
                    },
                    onRemoveFromContinue = onRemoveContinueWatching
                )
            }
        }

        item(key = "folders_header") {
            Text(
                text = "Thư mục Video (${folders.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
            )
        }

        items(folders, key = { it.name }) { folder ->
            LiquidGlassCard(
                cornerRadius = 22.dp,
                onClick = { onSelectFolder(folder.name) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        VideoThumbnailView(
                            video = folder.previewVideo,
                            modifier = Modifier.size(width = 100.dp, height = 62.dp)
                        )
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(palette.primaryGlow.copy(alpha = 0.9f), CircleShape)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Folder,
                                contentDescription = null,
                                tint = Color(0xFF001F29),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${folder.count} video",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.primaryGlow,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "  •  ${formatFolderSize(folder.totalSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA7B8D8)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    FastListScrollbar(
        listState = folderListState,
        totalItemsCount = folders.size,
        modifier = Modifier.align(Alignment.CenterEnd)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCollectionSection(
    displayedVideos: List<VideoItem>,
    videoListState: LazyListState,
    isGridView: Boolean,
    isVaultMode: Boolean,
    isMultiSelectMode: Boolean,
    selectedVideoIds: List<String>,
    savedPositions: PositionMap,
    watchLaterIds: Set<String> = emptySet(),
    lastViewedVideoId: String?,
    onItemClick: (VideoItem) -> Unit,
    onItemLongClick: (VideoItem) -> Unit,
    onOpenMenuFor: (VideoItem) -> Unit
) {
    val palette = LocalGlassPalette.current

    if (isGridView) {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedVideos, key = { it.id }) { video ->
                    val savedPos = savedPositions[video.id] ?: 0L
                    val progress = if (video.duration > 0 && savedPos > 1000L) {
                        (savedPos.toFloat() / video.duration).coerceIn(0f, 1f)
                    } else 0f
                    val isSelected = video.id in selectedVideoIds
                    val isJustWatched = (video.id == lastViewedVideoId)

                    LiquidGlassCard(
                        cornerRadius = 20.dp,
                        highlighted = isSelected || isJustWatched,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onItemClick(video) },
                                onLongClick = { onItemLongClick(video) }
                            )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Box {
                                VideoThumbnailView(
                                    video = video,
                                    progressFraction = progress,
                                    isWatchLater = video.id in watchLaterIds,
                                    modifier = Modifier.fillMaxWidth().height(104.dp)
                                )
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onItemClick(video) },
                                        modifier = Modifier.align(Alignment.TopStart)
                                    )
                                }
                            }
                            if (progress > 0f) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = palette.primaryGlow,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected || isJustWatched) palette.primaryGlow else Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = video.sizeFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFA7B8D8)
                                    )
                                }
                                if (!isMultiSelectMode) {
                                    IconButton(
                                        onClick = { onOpenMenuFor(video) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FastGridScrollbar(
                gridState = gridState,
                totalItemsCount = displayedVideos.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = videoListState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedVideos, key = { it.id }) { video ->
                    val savedPos = savedPositions[video.id] ?: 0L
                    val progress = if (video.duration > 0 && savedPos > 1000L) {
                        (savedPos.toFloat() / video.duration).coerceIn(0f, 1f)
                    } else 0f
                    val isSelected = video.id in selectedVideoIds
                    val isJustWatched = (video.id == lastViewedVideoId)

                    LiquidGlassCard(
                        cornerRadius = 20.dp,
                        highlighted = isSelected || isJustWatched,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onItemClick(video) },
                                onLongClick = { onItemLongClick(video) }
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onItemClick(video) }
                                )
                            }
                            VideoThumbnailView(
                                video = video,
                                progressFraction = progress,
                                isWatchLater = video.id in watchLaterIds,
                                modifier = Modifier.size(width = 112.dp, height = 64.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = video.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected || isJustWatched) palette.primaryGlow else Color.White,
                                    fontWeight = if (isSelected || isJustWatched) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isVaultMode) {
                                        "📁 Lưu trữ • ${video.sizeFormatted}"
                                    } else if (savedPos > 2000L) {
                                        "${video.sizeFormatted} • Đã xem ${(progress * 100).toInt()}%"
                                    } else {
                                        video.sizeFormatted
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (savedPos > 2000L || isVaultMode) palette.primaryGlow else Color(0xFFA7B8D8)
                                )
                                if (progress > 0f) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = progress,
                                        color = palette.primaryGlow,
                                        trackColor = Color.White.copy(alpha = 0.12f),
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            if (!isMultiSelectMode) {
                                IconButton(onClick = { onOpenMenuFor(video) }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Quản lý file", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            FastListScrollbar(
                listState = videoListState,
                totalItemsCount = displayedVideos.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}