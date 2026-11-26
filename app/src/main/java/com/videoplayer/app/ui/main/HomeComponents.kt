package com.videoplayer.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
    DATE_DESC("M\u1EDBi nh\u1EA5t tr\u01B0\u1EDBc"),
    DATE_ASC("C\u0169 nh\u1EA5t tr\u01B0\u1EDBc"),
    SIZE_DESC("Dung l\u01B0\u1EE3ng: L\u1EDBn \u2192 Nh\u1ECF"),
    SIZE_ASC("Dung l\u01B0\u1EE3ng: Nh\u1ECF \u2192 L\u1EDBn"),
    DURATION_DESC("Th\u1EDDi l\u01B0\u1EE3ng: D\u00E0i \u2192 Ng\u1EAFn"),
    NAME_ASC("T\u00EAn: A \u2192 Z")
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
    val style = LocalGlassStyle.current
    val palette = style.palette

    LiquidGlassBar(
        variant = LiquidGlassVariant.REGULAR,
        cornerRadius = 22.dp,
        shadowElevation = 12.dp,
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
                    text = "\u0110\u00E3 ch\u1ECDn $selectedCount/$totalCount",
                    color = palette.primaryGlow,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiquidGlassPill(onClick = onToggleSelectAll) {
                        Text(
                            if (selectedCount == totalCount) "B\u1ECF ch\u1ECDn" else "Ch\u1ECDn h\u1EBFt",
                            color = style.textPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    LiquidGlassPill(onClick = onCancel) {
                        Text("H\u1EE7y", color = style.textPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LiquidGlassPill(onClick = onShareSelected) {
                    Icon(Icons.Default.Share, null, tint = style.textPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chia s\u1EBB", color = style.textPrimary, style = MaterialTheme.typography.labelSmall)
                }
                if (!isVaultMode) {
                    LiquidGlassPill(onClick = onCopySelected) {
                        Icon(AppIcons.ContentCopy, null, tint = style.textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy", color = style.textPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                    LiquidGlassPill(onClick = onMoveSelected) {
                        Icon(AppIcons.DriveFileMove, null, tint = style.textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chuy\u1EC3n", color = style.textPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                LiquidGlassPill(onClick = onVaultActionSelected) {
                    Icon(if (isVaultMode) AppIcons.LockOpen else Icons.Default.Lock, null, tint = style.textPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isVaultMode) "Kh\u00F4i ph\u1EE5c" else "\u1EA8n", color = style.textPrimary, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(active = true, onClick = onDeleteSelected) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("X\u00F3a", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
    val style = LocalGlassStyle.current
    val palette = style.palette

    LazyColumn(
        state = folderListState,
        modifier = Modifier.fillMaxSize().padding(horizontal = if (style.isPureAmoled) 8.dp else 14.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(if (style.isPureAmoled) 0.dp else if (style.isProMonitor) 6.dp else 10.dp)
    ) {
        // CINEMA SHOWCASE: Hien thi Hero Banner 16:9 lon cho video dang xem do gan nhat!
        if (style.isCinema && continueWatchingItems.isNotEmpty()) {
            val hero = continueWatchingItems.first()
            item(key = "cinema_hero_${hero.video.id}") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            val folderList = visibleVideos.filter { it.folder == hero.video.folder }
                            onPlayRecent(hero.video, folderList.ifEmpty { listOf(hero.video) })
                        }
                ) {
                    VideoThumbnailView(
                        video = hero.video,
                        progressFraction = hero.progressFraction,
                        showDurationBadge = false,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.90f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "TI\u1EBEP T\u1EE4C XEM \u2022 ${(hero.progressFraction * 100).toInt()}%",
                            color = palette.primaryGlow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = hero.video.displayName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(palette.primaryGlow)
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Ph\u00E1t ti\u1EBFp", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = hero.video.folder,
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        } else if (continueWatchingItems.isNotEmpty()) {
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
                text = if (style.isProMonitor) "DIRECTORIES [${folders.size}]" else "Th\u01B0 m\u1EE5c (${folders.size})",
                style = if (style.isPureAmoled) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                color = if (style.isProMonitor) palette.primaryGlow else style.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 6.dp)
            )
        }

        items(folders, key = { it.name }) { folder ->
            Column {
                LiquidGlassCard(
                    cornerRadius = style.cardCorner,
                    onClick = { onSelectFolder(folder.name) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (style.isPureAmoled) 8.dp else 10.dp,
                            vertical = if (style.isProMonitor) 8.dp else 10.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            VideoThumbnailView(
                                video = folder.previewVideo,
                                showDurationBadge = false,
                                modifier = Modifier.size(
                                    width = if (style.isProMonitor) 82.dp else 96.dp,
                                    height = if (style.isProMonitor) 48.dp else 58.dp
                                )
                            )
                            if (!style.isPureAmoled) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .background(palette.primaryGlow.copy(alpha = 0.92f), if (style.isProMonitor) RoundedCornerShape(3.dp) else CircleShape)
                                        .padding(3.5.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF001F29),
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = style.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${folder.count} video \u2022 ${formatFolderSize(folder.totalSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                                color = style.textSecondary
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = style.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (style.isPureAmoled) {
                    Divider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
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
    val style = LocalGlassStyle.current
    val palette = style.palette

    if (isGridView) {
        val gridState = rememberLazyGridState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedVideos, key = { it.id }) { video ->
                    val savedPos = savedPositions[video.id] ?: 0L
                    val progress = if (video.duration > 0 && savedPos > 1000L) (savedPos.toFloat() / video.duration).coerceIn(0f, 1f) else 0f
                    val isSelected = video.id in selectedVideoIds
                    val isJustWatched = (video.id == lastViewedVideoId)

                    LiquidGlassCard(
                        cornerRadius = style.cardCorner,
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
                                    modifier = Modifier.fillMaxWidth().height(102.dp)
                                )
                                if (isMultiSelectMode) {
                                    Checkbox(checked = isSelected, onCheckedChange = { onItemClick(video) }, modifier = Modifier.align(Alignment.TopStart))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected || isJustWatched) palette.primaryGlow else style.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = video.sizeFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                                        color = style.textSecondary
                                    )
                                }
                                if (!isMultiSelectMode) {
                                    IconButton(onClick = { onOpenMenuFor(video) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.MoreVert, null, tint = style.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FastGridScrollbar(gridState = gridState, totalItemsCount = displayedVideos.size, modifier = Modifier.align(Alignment.CenterEnd))
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = videoListState,
                modifier = Modifier.fillMaxSize().padding(horizontal = if (style.isPureAmoled) 8.dp else 14.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(if (style.isPureAmoled) 0.dp else if (style.isProMonitor) 6.dp else if (style.isCinema) 14.dp else 9.dp)
            ) {
                items(displayedVideos, key = { it.id }) { video ->
                    val savedPos = savedPositions[video.id] ?: 0L
                    val progress = if (video.duration > 0 && savedPos > 1000L) (savedPos.toFloat() / video.duration).coerceIn(0f, 1f) else 0f
                    val isSelected = video.id in selectedVideoIds
                    val isJustWatched = (video.id == lastViewedVideoId)

                    when {
                        // 1. CINEMA SHOWCASE: The anh lon tran vien (Immersive Poster Card) chu de tren lop bong mo day anh!
                        style.isCinema -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(186.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        width = if (isSelected || isJustWatched) 1.5.dp else 0.7.dp,
                                        color = if (isSelected || isJustWatched) palette.primaryGlow else Color.White.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { onItemClick(video) },
                                        onLongClick = { onItemLongClick(video) }
                                    )
                            ) {
                                VideoThumbnailView(
                                    video = video,
                                    progressFraction = progress,
                                    isWatchLater = video.id in watchLaterIds,
                                    showDurationBadge = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.88f))
                                            )
                                        )
                                )
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onItemClick(video) },
                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 6.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = video.displayName,
                                            color = if (isSelected || isJustWatched) palette.primaryGlow else Color.White,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${video.durationFormatted} \u2022 ${video.sizeFormatted}",
                                            color = Color.White.copy(alpha = 0.72f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (!isMultiSelectMode) {
                                        IconButton(onClick = { onOpenMenuFor(video) }) {
                                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // 2. PRO MONITOR HI-RES: Danh sach ky thuat mat do cao, goc vuong 5dp, Font Monospace & Badge dinh dang (MP4/MKV)
                        style.isProMonitor -> {
                            val extBadge = video.displayName.substringAfterLast('.', "VID").uppercase().take(4)
                            LiquidGlassCard(
                                cornerRadius = 6.dp,
                                highlighted = isSelected || isJustWatched,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onItemClick(video) },
                                        onLongClick = { onItemLongClick(video) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isMultiSelectMode) {
                                        Checkbox(checked = isSelected, onCheckedChange = { onItemClick(video) })
                                    }
                                    VideoThumbnailView(
                                        video = video,
                                        progressFraction = progress,
                                        isWatchLater = video.id in watchLaterIds,
                                        modifier = Modifier.size(width = 92.dp, height = 52.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                        Text(
                                            text = video.displayName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected || isJustWatched) palette.primaryGlow else style.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(palette.primaryGlow.copy(alpha = 0.22f))
                                                    .border(0.7.dp, palette.primaryGlow, RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = extBadge,
                                                    color = palette.primaryGlow,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "${video.sizeFormatted} | ${video.durationFormatted}",
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = style.textSecondary
                                            )
                                        }
                                    }
                                    if (!isMultiSelectMode) {
                                        IconButton(onClick = { onOpenMenuFor(video) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.MoreVert, null, tint = style.textSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // 3 & 4. APPLE LIQUID GLASS (Dark/Light) & PURE AMOLED (Borderless Hairline)
                        else -> {
                            Column {
                                LiquidGlassCard(
                                    cornerRadius = style.cardCorner,
                                    highlighted = isSelected || isJustWatched,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onItemClick(video) },
                                            onLongClick = { onItemLongClick(video) }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (style.isPureAmoled) 8.dp else 9.dp,
                                            vertical = if (style.isPureAmoled) 11.dp else 9.dp
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isMultiSelectMode) {
                                            Checkbox(checked = isSelected, onCheckedChange = { onItemClick(video) })
                                        }
                                        VideoThumbnailView(
                                            video = video,
                                            progressFraction = progress,
                                            isWatchLater = video.id in watchLaterIds,
                                            modifier = Modifier.size(width = 108.dp, height = 62.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                            Text(
                                                text = video.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isSelected || isJustWatched) palette.primaryGlow else style.textPrimary,
                                                fontWeight = if (isSelected || isJustWatched) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isVaultMode) {
                                                    "L\u01B0u tr\u1EEF n\u1ED9i b\u1ED9 \u2022 ${video.sizeFormatted}"
                                                } else if (savedPos > 2000L) {
                                                    "${video.sizeFormatted} \u2022 \u0110\u00E3 xem ${(progress * 100).toInt()}%"
                                                } else {
                                                    video.sizeFormatted
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (savedPos > 2000L || isVaultMode) palette.primaryGlow else style.textSecondary
                                            )
                                        }
                                        if (!isMultiSelectMode) {
                                            IconButton(onClick = { onOpenMenuFor(video) }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "T\u00F9y ch\u1ECDn", tint = style.textSecondary)
                                            }
                                        }
                                    }
                                }
                                if (style.isPureAmoled) {
                                    Divider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
            FastListScrollbar(listState = videoListState, totalItemsCount = displayedVideos.size, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}