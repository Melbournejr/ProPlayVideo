package com.videoplayer.app.ui.main

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.ui.components.VideoThumbnailView
import com.videoplayer.app.util.FileOperationHelper
import android.content.ContextWrapper
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.VaultHideOutcome
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    NONE, INFO, RENAME, COPY, MOVE, DELETE, BATCH_COPY, BATCH_MOVE, BATCH_DELETE
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
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

    // Danh sách video đã được mã hóa AES-256-GCM trong Vault
    val vaultEntries = remember(refreshTrigger, isVaultMode) {
        VaultManager.getVaultVideosAsVideoItems(context).map { VaultManager.toVideoItem(context, it) }
    }

    val fragmentActivity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val vaultSecState by context.observeVaultSecurityState().collectAsState(initial = VaultSecurityState())
    var pendingVaultAuthAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showConfirmHidePinDialog by remember { mutableStateOf(false) }
    var isProcessingVault by remember { mutableStateOf(false) }

    // Hàm yêu cầu Xác thực Vân tay hoặc Mã PIN trước khi cho phép Ẩn video vào Kho
    fun requireAuthBeforeVaultAction(action: () -> Unit) {
        val act = fragmentActivity
        val now = System.currentTimeMillis()
        if (vaultSecState.isConfigured && act != null && PinSecurityManager.canUseBiometric(act) && vaultSecState.lockoutUntilMs <= now) {
            PinSecurityManager.showBiometricPrompt(
                activity = act,
                onSuccess = {
                    scope.launch { context.recordVaultPinAttempt(true) }
                    action()
                },
                onUsePinFallback = {
                    pendingVaultAuthAction = action
                    showConfirmHidePinDialog = true
                }
            )
        } else {
            pendingVaultAuthAction = action
            showConfirmHidePinDialog = true
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<String>() }

    var activeVideoForAction by remember { mutableStateOf<VideoItem?>(null) }
    var dialogType by remember { mutableStateOf(FileDialogType.NONE) }
    var showBottomSheetFor by remember { mutableStateOf<VideoItem?>(null) }

    // Launcher cho MediaStore.createDeleteRequest / createWriteRequest (Bước 4)
    var pendingRenameNewName by remember { mutableStateOf<String?>(null) }
    val mediaStorePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val vid = activeVideoForAction
            val newName = pendingRenameNewName
            if (vid != null && !newName.isNullOrBlank()) {
                scope.launch {
                    FileOperationHelper.renameVideo(context, vid, newName)
                    pendingRenameNewName = null
                    refreshTrigger++
                }
            } else {
                Toast.makeText(context, "Đã thực hiện thao tác tệp!", Toast.LENGTH_SHORT).show()
                refreshTrigger++
            }
        }
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedVideoIds.clear()
    }

    LaunchedEffect(selectedFolder, isVaultMode) {
        exitMultiSelect()
    }

    BackHandler(enabled = isMultiSelectMode || selectedFolder != null || isVaultMode || isSearchExpanded) {
        when {
            isMultiSelectMode -> exitMultiSelect()
            isSearchExpanded -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            isVaultMode -> onExitVault()
            selectedFolder != null -> onSelectFolder(null)
        }
    }

    val visibleVideos = remember(rawVideos, vaultEntries, isVaultMode) {
        if (isVaultMode) vaultEntries else rawVideos
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

    val currentScrollKey = remember(selectedFolder, isVaultMode) {
        if (isVaultMode) "__VAULT__" else (selectedFolder ?: "")
    }
    var hasRestoredScrollForFolder by remember(currentScrollKey) { mutableStateOf(false) }

    LaunchedEffect(currentScrollKey, displayedVideos.size) {
        if (currentScrollKey.isNotEmpty() && displayedVideos.isNotEmpty() && !hasRestoredScrollForFolder) {
            hasRestoredScrollForFolder = true
            val (savedIdx, savedOff) = context.readFolderScrollState(currentScrollKey)
            if (savedIdx > 0 && savedIdx < displayedVideos.size) {
                videoListState.scrollToItem(savedIdx, savedOff)
            }
        }
    }

    LaunchedEffect(videoListState, currentScrollKey, hasRestoredScrollForFolder) {
        if (currentScrollKey.isNotEmpty() && hasRestoredScrollForFolder) {
            snapshotFlow {
                videoListState.firstVisibleItemIndex to videoListState.firstVisibleItemScrollOffset
            }
                .distinctUntilChanged()
                .debounce(250L)
                .collect { (idx, off) ->
                    context.saveFolderScrollState(currentScrollKey, idx, off)
                }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isProcessingVault) {
            LinearProgressIndicator(
                color = palette.primaryGlow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        if (isMultiSelectMode) {
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
                            text = "Đã chọn ${selectedVideoIds.size}/${displayedVideos.size} video",
                            color = palette.primaryGlow,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            LiquidGlassPill(
                                onClick = {
                                    if (selectedVideoIds.size == displayedVideos.size) selectedVideoIds.clear()
                                    else {
                                        selectedVideoIds.clear()
                                        selectedVideoIds.addAll(displayedVideos.map { it.id })
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedVideoIds.size == displayedVideos.size) "Bỏ chọn hết" else "Chọn tất cả",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            LiquidGlassPill(onClick = { exitMultiSelect() }) {
                                Text("Hủy ✕", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!isVaultMode) {
                            LiquidGlassPill(onClick = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_COPY }) {
                                Icon(AppIcons.ContentCopy, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            LiquidGlassPill(onClick = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_MOVE }) {
                                Icon(AppIcons.DriveFileMove, null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Chuyển", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        LiquidGlassPill(
                            onClick = {
                                if (selectedVideoIds.isNotEmpty() && !isProcessingVault) {
                                    val targets = displayedVideos.filter { it.id in selectedVideoIds }
                                    requireAuthBeforeVaultAction {
                                        scope.launch {
                                            isProcessingVault = true
                                            var okCount = 0
                                            val needConfirmVideos = mutableListOf<VideoItem>()
                                            if (isVaultMode) {
                                                targets.forEach { v ->
                                                    if (VaultManager.restoreVideoFromVault(context, v.id).isSuccess) okCount++
                                                }
                                                Toast.makeText(context, "🔓 Đã khôi phục tức thì $okCount video ra ngoài!", Toast.LENGTH_LONG).show()
                                            } else {
                                                targets.forEach { v ->
                                                    when (VaultManager.hideVideoToVaultFast(context, v)) {
                                                        is VaultHideOutcome.Success -> okCount++
                                                        is VaultHideOutcome.NeedsDeleteConfirmation -> {
                                                            okCount++
                                                            needConfirmVideos.add(v)
                                                        }
                                                        is VaultHideOutcome.Error -> {}
                                                    }
                                                }
                                                if (needConfirmVideos.isNotEmpty()) {
                                                    val pi = FileOperationHelper.createDeleteRequestPendingIntent(context, needConfirmVideos)
                                                    if (pi != null) {
                                                        mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                                    }
                                                }
                                                Toast.makeText(context, "🔒 Đã chuyển tức thì $okCount video vào Kho Ẩn!", Toast.LENGTH_LONG).show()
                                            }
                                            isProcessingVault = false
                                            exitMultiSelect()
                                            refreshTrigger++
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(if (isVaultMode) AppIcons.LockOpen else Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isVaultMode) "Khôi phục ra ngoài" else "Đưa vào Kho Ẩn", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!isVaultMode) {
                            LiquidGlassPill(
                                active = true,
                                onClick = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_DELETE }
                            ) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5C8A), modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Xóa (${selectedVideoIds.size})", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
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
                            text = if (isVaultMode) "Tìm trong Kho AES-256..." else "Tìm kiếm...",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }

                    if (selectedFolder != null || isVaultMode) {
                        LiquidGlassPill(onClick = { isMultiSelectMode = true }) {
                            Icon(AppIcons.Checklist, contentDescription = "Chọn nhiều", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Chọn", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Box {
                        LiquidGlassPill(
                            active = sortOption != SortOption.DATE_DESC,
                            onClick = { showSortMenu = true }
                        ) {
                            Icon(AppIcons.Sort, contentDescription = "Sắp xếp", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
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

                    LiquidGlassPill(active = isGridView, onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) AppIcons.ViewList else AppIcons.GridView,
                            contentDescription = "Đổi kiểu xem",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    LiquidGlassPill(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

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
                                if (isVaultMode) Icons.Default.Lock else AppIcons.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = palette.primaryGlow
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                if (isVaultMode) "Kho Riêng Tư (Mã hóa AES-256-GCM) đang trống.\nBấm nút 3 chấm (⋮) ở bất kỳ video nào để mã hóa đưa vào Kho."
                                else "Chưa tìm thấy video hoặc chưa cấp quyền bộ nhớ.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else if (selectedFolder == null && !isVaultMode && !isSearchingAll) {
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
            } else {
                fun handleItemClick(video: VideoItem) {
                    if (isMultiSelectMode) {
                        if (video.id in selectedVideoIds) selectedVideoIds.remove(video.id)
                        else selectedVideoIds.add(video.id)
                    } else {
                        onVideoClick(video, displayedVideos)
                    }
                }

                fun handleItemLongClick(video: VideoItem) {
                    if (!isMultiSelectMode) {
                        isMultiSelectMode = true
                        selectedVideoIds.clear()
                        selectedVideoIds.add(video.id)
                    }
                }

                if (isGridView) {
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
                            val isSelected = video.id in selectedVideoIds
                            val isJustWatched = (video.id == lastViewedVideoId)

                            LiquidGlassCard(
                                cornerRadius = 20.dp,
                                highlighted = isSelected || isJustWatched,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { handleItemClick(video) },
                                        onLongClick = { handleItemLongClick(video) }
                                    )
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Box {
                                        VideoThumbnailView(
                                            video = video,
                                            modifier = Modifier.fillMaxWidth().height(104.dp)
                                        )
                                        if (isMultiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { handleItemClick(video) },
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
                    }
                } else {
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
                            val isSelected = video.id in selectedVideoIds
                            val isJustWatched = (video.id == lastViewedVideoId)

                            LiquidGlassCard(
                                cornerRadius = 20.dp,
                                highlighted = isSelected || isJustWatched,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { handleItemClick(video) },
                                        onLongClick = { handleItemLongClick(video) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isMultiSelectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { handleItemClick(video) }
                                        )
                                    }
                                    VideoThumbnailView(
                                        video = video,
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
                                                "🔒 Đã ẩn an toàn • ${video.sizeFormatted}"
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
                                        IconButton(onClick = { showBottomSheetFor = video }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Quản lý file", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isMultiSelectMode) {
                LiquidGlassPill(
                    active = true,
                    onClick = onStreamClick,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                ) {
                    Icon(imageVector = AppIcons.Stream, contentDescription = "Stream URL", tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Stream Mạng", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ================= MENU QUẢN LÝ FILE ĐƠN LẺ =================
    showBottomSheetFor?.let { targetVideo ->
        val isVaultItem = (targetVideo.id.startsWith("vault_") || targetVideo.path.startsWith("vault://"))
        AlertDialog(
            onDismissRequest = { showBottomSheetFor = null },
            containerColor = palette.deepBgMid.copy(alpha = 0.96f),
            title = { Text(targetVideo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("Chọn nhiều video...") },
                        leadingContent = { Icon(AppIcons.Checklist, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            showBottomSheetFor = null
                            isMultiSelectMode = true
                            selectedVideoIds.clear()
                            selectedVideoIds.add(targetVideo.id)
                        }
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                if (isVaultItem) "Khôi phục video ra khỏi Kho Ẩn" else "Đưa vào Kho Ẩn (Tức thì • Khóa Vân tay/PIN)",
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryGlow
                            )
                        },
                        leadingContent = {
                            Icon(if (isVaultItem) AppIcons.LockOpen else Icons.Default.Lock, null, tint = palette.primaryGlow)
                        },
                        modifier = Modifier.clickable {
                            showBottomSheetFor = null
                            requireAuthBeforeVaultAction {
                                scope.launch {
                                    isProcessingVault = true
                                    if (isVaultItem) {
                                        VaultManager.restoreVideoFromVault(context, targetVideo.id).onSuccess { path ->
                                            Toast.makeText(context, "🔓 Đã khôi phục về: $path", Toast.LENGTH_LONG).show()
                                            refreshTrigger++
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Lỗi khôi phục: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        when (val out = VaultManager.hideVideoToVaultFast(context, targetVideo)) {
                                            is VaultHideOutcome.Success -> {
                                                Toast.makeText(context, "🔒 Đã ẩn tức thì vào Kho Riêng Tư!", Toast.LENGTH_SHORT).show()
                                                refreshTrigger++
                                            }
                                            is VaultHideOutcome.NeedsDeleteConfirmation -> {
                                                // Với video tải từ Zalo/App khác trên Android 11+, bật bảng xác nhận xóa file gốc ở thư mục Zalo
                                                Toast.makeText(context, "🔒 Đã đưa vào Kho! Hãy bấm 'Cho phép' để xóa bản gốc ở thư mục ngoài.", Toast.LENGTH_LONG).show()
                                                mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(out.pendingIntent.intentSender).build())
                                                refreshTrigger++
                                            }
                                            is VaultHideOutcome.Error -> {
                                                Toast.makeText(context, "Lỗi: ${out.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    isProcessingVault = false
                                }
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
                    if (!isVaultItem) {
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
                            leadingContent = { Icon(AppIcons.ContentCopy, null, tint = palette.primaryGlow) },
                            modifier = Modifier.clickable {
                                activeVideoForAction = targetVideo
                                dialogType = FileDialogType.COPY
                                showBottomSheetFor = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Di chuyển (Move) sang thư mục khác") },
                            leadingContent = { Icon(AppIcons.DriveFileMove, null, tint = palette.primaryGlow) },
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showBottomSheetFor = null }) { Text("Đóng") }
            }
        )
    }

    // ================= HỘP THOẠI XÁC THỰC MÃ PIN TRƯỚC KHI ẨN VIDEO =================
    if (showConfirmHidePinDialog) {
        var pinInput by remember { mutableStateOf("") }
        val isSettingNewPin = !vaultSecState.isConfigured
        AlertDialog(
            onDismissRequest = {
                showConfirmHidePinDialog = false
                pendingVaultAuthAction = null
            },
            containerColor = palette.deepBgMid,
            title = {
                Text(
                    if (isSettingNewPin) "🔐 Tạo Mã PIN Kho Ẩn" else "🔒 Xác nhận Mã PIN để Ẩn Video",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isSettingNewPin) "Thiết lập mã PIN (tối thiểu 6 ký tự) để bảo vệ video ẩn của bạn:"
                        else "Nhập mã PIN của bạn để xác nhận đưa video vào Kho Ẩn:",
                        color = Color.White
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        label = { Text("Mã PIN (>= 6 ký tự)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (isSettingNewPin) {
                        if (PinSecurityManager.isValidNewPin(pinInput)) {
                            scope.launch {
                                val salt = PinSecurityManager.generateSaltBase64()
                                val hash = PinSecurityManager.hashPinPbkdf2(pinInput, salt)
                                context.saveHashedVaultPin(hash, salt)
                                showConfirmHidePinDialog = false
                                val act = pendingVaultAuthAction
                                pendingVaultAuthAction = null
                                act?.invoke()
                            }
                        } else {
                            Toast.makeText(context, "Mã PIN phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val ok = PinSecurityManager.verifyPinConstantTime(
                            inputPin = pinInput,
                            storedHashBase64 = vaultSecState.pinHash ?: "",
                            storedSaltBase64 = vaultSecState.pinSalt ?: ""
                        )
                        if (ok) {
                            scope.launch { context.recordVaultPinAttempt(true) }
                            showConfirmHidePinDialog = false
                            val act = pendingVaultAuthAction
                            pendingVaultAuthAction = null
                            act?.invoke()
                        } else {
                            Toast.makeText(context, "Sai mã PIN!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmHidePinDialog = false
                    pendingVaultAuthAction = null
                }) { Text("Hủy") }
            }
        )
    }

    // ================= CÁC HỘP THOẠI XỬ LÝ FILE =================
    when (dialogType) {
        FileDialogType.BATCH_DELETE -> {
            val targets = remember(selectedVideoIds.toList(), rawVideos) {
                rawVideos.filter { it.id in selectedVideoIds }
            }
            AlertDialog(
                onDismissRequest = { dialogType = FileDialogType.NONE },
                containerColor = palette.deepBgMid,
                title = { Text("Xóa ${targets.size} video đã chọn?") },
                text = { Text("Bạn có chắc chắn muốn xóa vĩnh viễn ${targets.size} video này không?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            scope.launch {
                                var count = 0
                                targets.forEach { v ->
                                    if (FileOperationHelper.deleteVideo(context, v).isSuccess) count++
                                }
                                if (count < targets.size) {
                                    val pi = FileOperationHelper.createDeleteRequestPendingIntent(context, targets)
                                    if (pi != null) {
                                        mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                    }
                                } else {
                                    Toast.makeText(context, "Đã xóa $count video!", Toast.LENGTH_SHORT).show()
                                }
                                dialogType = FileDialogType.NONE
                                exitMultiSelect()
                                refreshTrigger++
                            }
                        }
                    ) { Text("Xóa tất cả") }
                },
                dismissButton = {
                    TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                }
            )
        }
        FileDialogType.BATCH_COPY, FileDialogType.BATCH_MOVE -> {
            val isMove = (dialogType == FileDialogType.BATCH_MOVE)
            val targets = remember(selectedVideoIds.toList(), rawVideos) {
                rawVideos.filter { it.id in selectedVideoIds }
            }
            var targetPath by remember { mutableStateOf("/storage/emulated/0/Movies/VideoPlayerPro") }
            AlertDialog(
                onDismissRequest = { dialogType = FileDialogType.NONE },
                containerColor = palette.deepBgMid,
                title = { Text(if (isMove) "Di chuyển ${targets.size} video tới..." else "Sao chép ${targets.size} video tới...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                            items(folders) { f ->
                                LiquidGlassCard(
                                    cornerRadius = 14.dp,
                                    onClick = { targetPath = f.folderPath },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(AppIcons.Folder, null, tint = palette.primaryGlow)
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
                            label = { Text("Tên thư mục đích") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            var ok = 0
                            targets.forEach { v ->
                                if (FileOperationHelper.copyOrMoveVideo(context, v, targetPath, isMove).isSuccess) ok++
                            }
                            Toast.makeText(context, "Hoàn tất $ok/${targets.size} video!", Toast.LENGTH_SHORT).show()
                            dialogType = FileDialogType.NONE
                            exitMultiSelect()
                            refreshTrigger++
                        }
                    }) { Text(if (isMove) "Di chuyển ngay" else "Sao chép ngay") }
                },
                dismissButton = {
                    TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                }
            )
        }
        else -> {
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
                                    Text("• Nguồn: ${if (video.path.startsWith("vault://")) "Kho Mã hóa AES-256-GCM" else video.path}")
                                    Text("• Dung lượng gốc: ${video.sizeFormatted} (${video.size} bytes)")
                                    Text("• Thời lượng: ${video.durationFormatted}")
                                    Text("• Định dạng: ${video.mimeType}")
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Đóng") }
                            }
                        )
                    }
                    FileDialogType.RENAME -> {
                        var newName by remember(video) { mutableStateOf(video.displayName.substringBeforeLast('.')) }
                        AlertDialog(
                            onDismissRequest = { dialogType = FileDialogType.NONE },
                            containerColor = palette.deepBgMid,
                            title = { Text("Đổi tên Video") },
                            text = {
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    label = { Text("Tên mới") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(onClick = {
                                    scope.launch {
                                        FileOperationHelper.renameVideo(context, video, newName).onSuccess {
                                            Toast.makeText(context, "Đã đổi tên thành: $it", Toast.LENGTH_SHORT).show()
                                            dialogType = FileDialogType.NONE
                                            refreshTrigger++
                                        }.onFailure {
                                            pendingRenameNewName = newName
                                            val pi = FileOperationHelper.createWriteRequestPendingIntent(context, listOf(video))
                                            if (pi != null) {
                                                dialogType = FileDialogType.NONE
                                                mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                            }
                                        }
                                    }
                                }) { Text("Lưu") }
                            },
                            dismissButton = {
                                TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                            }
                        )
                    }
                    FileDialogType.COPY, FileDialogType.MOVE -> {
                        val isMove = (dialogType == FileDialogType.MOVE)
                        var targetPath by remember(video) {
                            mutableStateOf(File(video.path).parent ?: "/storage/emulated/0/Movies/VideoPlayerPro")
                        }
                        AlertDialog(
                            onDismissRequest = { dialogType = FileDialogType.NONE },
                            containerColor = palette.deepBgMid,
                            title = { Text(if (isMove) "Di chuyển Video tới..." else "Sao chép Video tới...") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                                        items(folders) { f ->
                                            LiquidGlassCard(
                                                cornerRadius = 14.dp,
                                                onClick = { targetPath = f.folderPath },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                            ) {
                                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(AppIcons.Folder, null, tint = palette.primaryGlow)
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
                                        label = { Text("Thư mục đích") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    scope.launch {
                                        FileOperationHelper.copyOrMoveVideo(context, video, targetPath, isMove).onSuccess {
                                            Toast.makeText(context, "Đã lưu tới: $it", Toast.LENGTH_SHORT).show()
                                            dialogType = FileDialogType.NONE
                                            refreshTrigger++
                                        }.onFailure { err ->
                                            Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }) { Text(if (isMove) "Di chuyển" else "Sao chép") }
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
                            text = { Text("Xóa tệp '${video.displayName}' khỏi thiết bị?") },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        scope.launch {
                                            FileOperationHelper.deleteVideo(context, video).onSuccess {
                                                Toast.makeText(context, "Đã xóa tệp!", Toast.LENGTH_SHORT).show()
                                                dialogType = FileDialogType.NONE
                                                refreshTrigger++
                                            }.onFailure {
                                                val pi = FileOperationHelper.createDeleteRequestPendingIntent(context, listOf(video))
                                                dialogType = FileDialogType.NONE
                                                if (pi != null) {
                                                    mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                                                }
                                            }
                                        }
                                    }
                                ) { Text("Xóa") }
                            },
                            dismissButton = {
                                TextButton(onClick = { dialogType = FileDialogType.NONE }) { Text("Hủy") }
                            }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

private fun formatFolderSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb < 1024) "$mb MB" else "%.1f GB".format(mb / 1024f)
}