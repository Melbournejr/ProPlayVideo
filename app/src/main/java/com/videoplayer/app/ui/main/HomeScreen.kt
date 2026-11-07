package com.videoplayer.app.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.ui.components.VideoThumbnailView
import com.videoplayer.app.util.FileOperationHelper
import kotlinx.coroutines.launch
import java.io.File

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

private enum class FileDialogType {
    NONE, INFO, RENAME, COPY, MOVE, DELETE
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedFolder: String?,
    isVaultMode: Boolean = false,
    folderListState: LazyListState,
    videoListState: LazyListState,
    lastViewedVideoId: String?,
    onSelectFolder: (String?) -> Unit,
    onExitVault: () -> Unit = {},
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onStreamClick: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val repository = remember(context) { VideoRepository(context) }
    val rawVideos by remember(refreshTrigger) { repository.getAllVideos() }.collectAsState(initial = emptyList())
    val savedPositions by context.observeAllPositions().collectAsState(initial = emptyMap())
    val lastPlayedId by context.observeLastPlayedVideoId().collectAsState(initial = null)
    val vaultIds by context.observeVaultVideoIds().collectAsState(initial = emptySet())

    // State cho Tìm kiếm, Sắp xếp & Chế độ Lưới 2 cột (Grid/List)
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    var activeVideoForAction by remember { mutableStateOf<VideoItem?>(null) }
    var dialogType by remember { mutableStateOf(FileDialogType.NONE) }
    var showBottomSheetFor by remember { mutableStateOf<VideoItem?>(null) }

    BackHandler(enabled = selectedFolder != null || isVaultMode || isSearchExpanded) {
        when {
            isSearchExpanded -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            isVaultMode -> onExitVault()
            selectedFolder != null -> onSelectFolder(null)
        }
    }

    // Lọc các video theo chế độ Thường hoặc Kho Riêng Tư
    val visibleVideos = remember(rawVideos, vaultIds, isVaultMode) {
        if (isVaultMode) {
            rawVideos.filter { it.id in vaultIds }
        } else {
            rawVideos.filter { it.id !in vaultIds }
        }
    }

    fun applySorting(list: List<VideoItem>, sort: SortOption): List<VideoItem> {
        return when (sort) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> list.sortedBy { it.dateAdded }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.size }
            SortOption.SIZE_ASC -> list.sortedBy { it.size }
            SortOption.DURATION_DESC -> list.sortedByDescending { it.duration }
            SortOption.NAME_ASC -> list.sortedBy { it.displayName.lowercase() }
        }
    }

    val folders = remember(visibleVideos) {
        visibleVideos.groupBy { it.folder }.map { (folderName, list) ->
            val firstVideo = list.first()
            val parentDir = File(firstVideo.path).parent ?: "/storage/emulated/0/Movies"
            FolderSummary(
                name = folderName,
                folderPath = parentDir,
                count = list.size,
                totalSize = list.sumOf { it.size },
                previewVideo = firstVideo
            )
        }.sortedBy { it.name.lowercase() }
    }

    val lastPlayedVideo = remember(visibleVideos, lastPlayedId) {
        visibleVideos.find { it.id == lastPlayedId }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ================= THANH CÔNG CỤ TÌM KIẾM, SẮP XẾP & CHUYỂN LƯỚI 2 CỘT =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm tên video hoặc thư mục...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = palette.primaryGlow) },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearchExpanded = false
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )
            } else {
                LiquidGlassPill(
                    active = false,
                    onClick = { isSearchExpanded = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isVaultMode) "Tìm trong Kho Riêng Tư..." else "Tìm kiếm video nhanh...",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                // Nút Sắp xếp (Sort)
                Box {
                    LiquidGlassPill(
                        active = sortOption != SortOption.DATE_DESC,
                        onClick = { showSortMenu = true }
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Sắp xếp", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xếp", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.values().forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (sortOption == opt) "✓ ${opt.label}" else "    ${opt.label}",
                                        fontWeight = if (sortOption == opt) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    sortOption = opt
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }

                // Nút Đổi Danh sách <-> Lưới 2 cột (Grid/List)
                LiquidGlassPill(
                    active = isGridView,
                    onClick = { isGridView = !isGridView }
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Đổi kiểu xem",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Nút Làm mới
                LiquidGlassPill(onClick = { refreshTrigger++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ================= NỘI DUNG CHÍNH =================
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isSearchingAll = searchQuery.isNotBlank() && selectedFolder == null && !isVaultMode

            if (visibleVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(28.dp)
                        ) {
                            Icon(
                                if (isVaultMode) Icons.Default.Lock else Icons.Default.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = palette.primaryGlow
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                if (isVaultMode) "Kho Riêng Tư đang trống.\nBạn có thể bấm nút 3 chấm (⋮) ở bất kỳ video nào để đưa vào Kho Riêng Tư."
                                else "Chưa tìm thấy video hoặc chưa cấp quyền bộ nhớ.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else if (selectedFolder == null && !isVaultMode && !isSearchingAll) {
                // ================= TRANG DANH SÁCH THƯ MỤC =================
                LazyColumn(
                    state = folderListState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    lastPlayedVideo?.let { recent ->
                        val pos = savedPositions[recent.id] ?: 0L
                        if (pos > 2000L) {
                            item {
                                val recentPlaylist = remember(visibleVideos, recent.folder, sortOption) {
                                    applySorting(visibleVideos.filter { it.folder == recent.folder }, sortOption)
                                }
                                LiquidGlassCard(
                                    tintColor = palette.primaryGlow,
                                    surfaceAlpha = 0.16f,
                                    highlighted = true,
                                    cornerRadius = 24.dp,
                                    onClick = { onVideoClick(recent, recentPlaylist) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        VideoThumbnailView(
                                            video = recent,
                                            modifier = Modifier.size(width = 108.dp, height = 64.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                            LiquidGlassPill(active = true) {
                                                Text(
                                                    text = "▶ TIẾP TỤC XEM",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = recent.displayName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            val progress = (pos.toFloat() / recent.duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                            LinearProgressIndicator(
                                                progress = progress,
                                                color = palette.primaryGlow,
                                                trackColor = Color.White.copy(alpha = 0.15f),
                                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Thư mục Kính Lỏng (${folders.size})",
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
                                            imageVector = Icons.Default.Folder,
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
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ================= TRANG DANH SÁCH VIDEO (THƯ MỤC / TÌM KIẾM / KHO RIÊNG TƯ) =================
                val displayedVideos = remember(visibleVideos, selectedFolder, isVaultMode, searchQuery, sortOption) {
                    val base = when {
                        isVaultMode -> visibleVideos
                        selectedFolder != null -> visibleVideos.filter { it.folder == selectedFolder }
                        else -> visibleVideos
                    }
                    val filtered = if (searchQuery.isBlank()) base else {
                        base.filter {
                            it.displayName.contains(searchQuery, ignoreCase = true) ||
                                it.folder.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    applySorting(filtered, sortOption)
                }

                LaunchedEffect(lastViewedVideoId, displayedVideos.size) {
                    if (!isGridView && lastViewedVideoId != null && displayedVideos.isNotEmpty()) {
                        val idx = displayedVideos.indexOfFirst { it.id == lastViewedVideoId }
                        if (idx >= 0) {
                            val visibleIndices = videoListState.layoutInfo.visibleItemsInfo.map { it.index }
                            if (visibleIndices.isEmpty() || idx !in visibleIndices) {
                                videoListState.scrollToItem((idx - 1).coerceAtLeast(0))
                            }
                        }
                    }
                }

                if (isGridView) {
                    // CHẾ ĐỘ LƯỚI 2 CỘT (GRID VIEW)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedVideos, key = { it.id }) { video ->
                            val savedPos = savedPositions[video.id] ?: 0L
                            val progress = if (video.duration > 0 && savedPos > 1000L) {
                                (savedPos.toFloat() / video.duration).coerceIn(0f, 1f)
                            } else 0f
                            val isJustWatched = (video.id == lastViewedVideoId)

                            LiquidGlassCard(
                                cornerRadius = 20.dp,
                                highlighted = isJustWatched,
                                onClick = { onVideoClick(video, displayedVideos) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    VideoThumbnailView(
                                        video = video,
                                        modifier = Modifier.fillMaxWidth().height(104.dp)
                                    )
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
                                                color = if (isJustWatched) palette.primaryGlow else Color.White,
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
                                        IconButton(
                                            onClick = { showBottomSheetFor = video },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // CHẾ ĐỘ DANH SÁCH (LIST VIEW)
                    LazyColumn(
                        state = videoListState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayedVideos, key = { it.id }) { video ->
                            val savedPos = savedPositions[video.id] ?: 0L
                            val progress = if (video.duration > 0 && savedPos > 1000L) {
                                (savedPos.toFloat() / video.duration).coerceIn(0f, 1f)
                            } else 0f
                            val isJustWatched = (video.id == lastViewedVideoId)

                            LiquidGlassCard(
                                cornerRadius = 20.dp,
                                highlighted = isJustWatched,
                                onClick = { onVideoClick(video, displayedVideos) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VideoThumbnailView(
                                        video = video,
                                        modifier = Modifier.size(width = 114.dp, height = 66.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(
                                            text = video.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isJustWatched) palette.primaryGlow else Color.White,
                                            fontWeight = if (isJustWatched) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (savedPos > 2000L) {
                                                "${video.sizeFormatted} • Đã xem ${(progress * 100).toInt()}%"
                                            } else {
                                                video.sizeFormatted
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (savedPos > 2000L) palette.primaryGlow else Color(0xFFA7B8D8)
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
                                    IconButton(onClick = { showBottomSheetFor = video }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Quản lý file", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LiquidGlassPill(
                active = true,
                onClick = onStreamClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            ) {
                Icon(imageVector = Icons.Default.Stream, contentDescription = "Stream URL", tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Stream Mạng", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ================= MENU QUẢN LÝ FILE + ĐƯA VÀO KHO RIÊNG TƯ =================
    showBottomSheetFor?.let { targetVideo ->
        val isInVault = targetVideo.id in vaultIds
        AlertDialog(
            onDismissRequest = { showBottomSheetFor = null },
            containerColor = palette.deepBgMid.copy(alpha = 0.96f),
            title = { Text(targetVideo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!FileOperationHelper.hasAllFilesAccess()) {
                        LiquidGlassCard(
                            tintColor = Color(0xFFFF5C8A),
                            surfaceAlpha = 0.2f,
                            onClick = { FileOperationHelper.requestAllFilesAccess(context) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                "⚠️ Chạm vào đây để cấp quyền 'Quản lý tất cả tệp' trước khi Đổi tên / Copy / Di chuyển / Xóa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    // Mục Đưa vào / Đưa ra khỏi Kho Riêng Tư
                    ListItem(
                        headlineContent = {
                            Text(
                                if (isInVault) "Khôi phục ra khỏi Kho Riêng Tư" else "Đưa vào Kho Riêng Tư (Ẩn video)",
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryGlow
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (isInVault) Icons.Default.LockOpen else Icons.Default.Lock,
                                null,
                                tint = palette.primaryGlow
                            )
                        },
                        modifier = Modifier.clickable {
                            scope.launch {
                                context.toggleVideoInVault(targetVideo.id, !isInVault)
                                Toast.makeText(
                                    context,
                                    if (!isInVault) "🔒 Đã ẩn vào Kho Riêng Tư!" else "🔓 Đã đưa ra ngoài danh sách thường!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showBottomSheetFor = null
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Xem thông tin chi tiết") },
                        leadingContent = { Icon(Icons.Default.Info, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.INFO
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Đổi tên video") },
                        leadingContent = { Icon(Icons.Default.Edit, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.RENAME
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Sao chép (Copy) sang thư mục khác") },
                        leadingContent = { Icon(Icons.Default.ContentCopy, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.COPY
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Di chuyển (Move) sang thư mục khác") },
                        leadingContent = { Icon(Icons.Default.DriveFileMove, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.MOVE
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.DELETE
                            showBottomSheetFor = null
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBottomSheetFor = null }) { Text("Đóng") }
            }
        )
    }

    // ================= CÁC HỘP THOẠI XỬ LÝ FILE =================
    activeVideoForAction?.let { video ->
        when (dialogType) {
            FileDialogType.INFO -> {
                AlertDialog(
                    onDismissRequest = { dialogType = FileDialogType.NONE },
                    containerColor = palette.deepBgMid,
                    title = { Text("Thông tin chi tiết Video") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("• Tên tệp: ${video.displayName}")
                            Text("• Thư mục: ${video.folder}")
                            Text("• Đường dẫn: ${video.path}")
                            Text("• Dung lượng: ${video.sizeFormatted} (${video.size} bytes)")
                            Text("• Thời lượng: ${video.durationFormatted}")
                            Text("• Định dạng: ${video.mimeType}")
                            Text("• Ngày thêm: ${FileOperationHelper.formatDate(video.dateAdded)}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Đóng") }
                    }
                )
            }
            FileDialogType.RENAME -> {
                var newName by remember(video) { mutableStateOf(File(video.path).nameWithoutExtension) }
                AlertDialog(
                    onDismissRequest = { dialogType = FileDialogType.NONE },
                    containerColor = palette.deepBgMid,
                    title = { Text("Đổi tên Video") },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Tên mới (không cần gõ đuôi .mp4)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                val res = FileOperationHelper.renameVideo(context, video, newName)
                                res.onSuccess {
                                    Toast.makeText(context, "Đã đổi tên thành: ${it.name}", Toast.LENGTH_SHORT).show()
                                    dialogType = FileDialogType.NONE
                                    kotlinx.coroutines.delay(400)
                                    refreshTrigger++
                                }.onFailure { err ->
                                    Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Text("Lưu tên mới") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                    }
                )
            }
            FileDialogType.COPY, FileDialogType.MOVE -> {
                val isMove = (dialogType == FileDialogType.MOVE)
                var targetPath by remember(video) {
                    mutableStateOf(File(video.path).parent ?: "/storage/emulated/0/Movies")
                }
                AlertDialog(
                    onDismissRequest = { dialogType = FileDialogType.NONE },
                    containerColor = palette.deepBgMid,
                    title = { Text(if (isMove) "Di chuyển Video tới..." else "Sao chép Video tới...") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Chọn nhanh thư mục có sẵn hoặc nhập đường dẫn thư mục mới:", style = MaterialTheme.typography.bodySmall)
                            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                                items(folders) { f ->
                                    LiquidGlassCard(
                                        cornerRadius = 14.dp,
                                        onClick = { targetPath = f.folderPath },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, null, tint = palette.primaryGlow)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(f.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                Text(f.folderPath, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7B8D8))
                                            }
                                        }
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = targetPath,
                                onValueChange = { targetPath = it },
                                label = { Text("Đường dẫn thư mục đích") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            scope.launch {
                                val res = FileOperationHelper.copyOrMoveVideo(context, video, targetPath, isMove)
                                res.onSuccess {
                                    Toast.makeText(context, if (isMove) "Đã di chuyển xong!" else "Đã sao chép xong!", Toast.LENGTH_SHORT).show()
                                    dialogType = FileDialogType.NONE
                                    kotlinx.coroutines.delay(500)
                                    refreshTrigger++
                                }.onFailure { err ->
                                    Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }) { Text(if (isMove) "Di chuyển ngay" else "Sao chép ngay") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                    }
                )
            }
            FileDialogType.DELETE -> {
                AlertDialog(
                    onDismissRequest = { dialogType = FileDialogType.NONE },
                    containerColor = palette.deepBgMid,
                    title = { Text("Xác nhận xóa vĩnh viễn") },
                    text = { Text("Bạn có chắc chắn muốn xóa tệp '${video.displayName}' khỏi bộ nhớ điện thoại không?") },
                    confirmButton = {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                scope.launch {
                                    val res = FileOperationHelper.deleteVideo(context, video)
                                    res.onSuccess {
                                        Toast.makeText(context, "Đã xóa tệp!", Toast.LENGTH_SHORT).show()
                                        dialogType = FileDialogType.NONE
                                        refreshTrigger++
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) { Text("Xóa vĩnh viễn") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                    }
                )
            }
            FileDialogType.NONE -> {}
        }
    }
}

private fun formatFolderSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb < 1024) "$mb MB" else "%.1f GB".format(mb / 1024f)
}