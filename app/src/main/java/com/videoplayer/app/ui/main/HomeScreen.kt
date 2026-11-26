package com.videoplayer.app.ui.main

import android.app.Activity
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassIconButton
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.VaultHideOutcome
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File

@OptIn(FlowPreview::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedFolder: String?,
    searchQuery: String = "",
    vaultAccessMode: VaultAccessMode = VaultAccessMode.LOCKED,
    folderListState: LazyListState,
    videoListState: LazyListState,
    lastViewedVideoId: String?,
    onSelectFolder: (String?) -> Unit,
    onExitVault: () -> Unit = {},
    onOpenVaultPinSettings: () -> Unit = {},
    onMultiSelectChanged: (Boolean) -> Unit = {},
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val repository = remember(context) { VideoRepository(context) }
    val rawVideos by remember(refreshTrigger) { repository.getAllVideos() }.collectAsState(initial = emptyList())
    val savedPositions by context.observeAllPositions().collectAsState(initial = emptyMap())

    LaunchedEffect(Unit) { WatchExperienceManager.ensureInitialized(context) }
    val watchExpData by WatchExperienceManager.state.collectAsState()
    var activeFeedFilter by remember { mutableStateOf(HomeFeedFilter.FOLDERS) }

    val isVaultMode = (vaultAccessMode != VaultAccessMode.LOCKED)
    val isDecoyVault = (vaultAccessMode == VaultAccessMode.DECOY_VAULT)

    val vaultEntries = remember(refreshTrigger, vaultAccessMode) {
        if (!isVaultMode) emptyList()
        else VaultManager.getVaultVideosAsVideoItems(context, isDecoy = isDecoyVault).map { VaultManager.toVideoItem(context, it) }
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
    var pendingVaultAuthAction by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var showConfirmHidePinDialog by remember { mutableStateOf(false) }
    var isProcessingVault by remember { mutableStateOf(false) }

    fun requireAuthBeforeVaultAction(action: (isDecoyTarget: Boolean) -> Unit) {
        val act = fragmentActivity
        val now = System.currentTimeMillis()
        if (vaultSecState.isConfigured && vaultSecState.biometricEnabled && act != null && PinSecurityManager.canUseBiometric(act) && vaultSecState.lockoutUntilMs <= now) {
            PinSecurityManager.showBiometricPrompt(
                activity = act,
                onSuccess = {
                    scope.launch { context.recordVaultPinAttempt(true) }
                    action(isDecoyVault)
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

    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedVideoIds = remember { mutableStateListOf<String>() }

    var activeVideoForAction by remember { mutableStateOf<VideoItem?>(null) }
    var dialogType by remember { mutableStateOf(FileDialogType.NONE) }
    var showBottomSheetFor by remember { mutableStateOf<VideoItem?>(null) }

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
                refreshTrigger++
            }
        }
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedVideoIds.clear()
        onMultiSelectChanged(false)
    }

    LaunchedEffect(isMultiSelectMode) {
        onMultiSelectChanged(isMultiSelectMode)
    }

    LaunchedEffect(selectedFolder, vaultAccessMode) {
        exitMultiSelect()
    }

    BackHandler(enabled = isMultiSelectMode || selectedFolder != null || isVaultMode || activeFeedFilter != HomeFeedFilter.FOLDERS) {
        when {
            isMultiSelectMode -> exitMultiSelect()
            isVaultMode -> onExitVault()
            selectedFolder != null -> onSelectFolder(null)
            activeFeedFilter != HomeFeedFilter.FOLDERS -> activeFeedFilter = HomeFeedFilter.FOLDERS
        }
    }

    val hiddenPaths = remember(refreshTrigger, vaultAccessMode, vaultEntries.size) {
        VaultManager.getHiddenOriginalPaths(context)
    }
    val visibleVideos = remember(rawVideos, vaultEntries, isVaultMode, hiddenPaths, refreshTrigger) {
        if (isVaultMode) vaultEntries
        else rawVideos.filter { it.path !in hiddenPaths && File(it.path).exists() }
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

    val watchLaterIdSet = remember(watchExpData.watchLaterIds) {
        watchExpData.watchLaterIds.toSet()
    }

    val continueWatchingItems = remember(visibleVideos, watchExpData.history, savedPositions) {
        val videoMap = visibleVideos.associateBy { it.id }
        val fromHistory = watchExpData.history.mapNotNull { h ->
            val vid = videoMap[h.videoId] ?: return@mapNotNull null
            val pos = savedPositions[vid.id] ?: h.lastPositionMs
            val dur = vid.duration.coerceAtLeast(h.durationMs).coerceAtLeast(1L)
            if (pos in 2000L..(dur - 2000L)) ContinueWatchingItem(vid, pos, dur) else null
        }
        if (fromHistory.isNotEmpty()) {
            fromHistory.take(15)
        } else {
            visibleVideos.mapNotNull { vid ->
                val pos = savedPositions[vid.id] ?: 0L
                if (pos > 2000L && vid.duration > 4000L && pos < vid.duration - 2000L) {
                    ContinueWatchingItem(vid, pos, vid.duration)
                } else null
            }.take(10)
        }
    }

    val displayedVideos = remember(visibleVideos, selectedFolder, isVaultMode, activeFeedFilter, watchExpData, searchQuery, sortOption) {
        val videoMap = visibleVideos.associateBy { it.id }
        val base = when {
            isVaultMode -> visibleVideos
            selectedFolder != null -> visibleVideos.filter { it.folder == selectedFolder }
            activeFeedFilter == HomeFeedFilter.WATCH_LATER -> watchExpData.watchLaterIds.mapNotNull { videoMap[it] }
            activeFeedFilter == HomeFeedFilter.HISTORY -> watchExpData.history.mapNotNull { videoMap[it.videoId] }
            else -> visibleVideos
        }
        val filtered = if (searchQuery.isBlank()) base else {
            base.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.folder.contains(searchQuery, ignoreCase = true)
            }
        }
        if (activeFeedFilter == HomeFeedFilter.HISTORY && selectedFolder == null && !isVaultMode) {
            filtered
        } else {
            applySorting(filtered, sortOption)
        }
    }

    val currentScrollKey = remember(selectedFolder, vaultAccessMode) {
        when (vaultAccessMode) {
            VaultAccessMode.REAL_VAULT -> "__VAULT_REAL__"
            VaultAccessMode.DECOY_VAULT -> "__VAULT_DECOY__"
            VaultAccessMode.LOCKED -> selectedFolder ?: ""
        }
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
            MultiSelectActionBar(
                selectedCount = selectedVideoIds.size,
                totalCount = displayedVideos.size,
                isVaultMode = isVaultMode,
                onToggleSelectAll = {
                    if (selectedVideoIds.size == displayedVideos.size) selectedVideoIds.clear()
                    else {
                        selectedVideoIds.clear()
                        selectedVideoIds.addAll(displayedVideos.map { it.id })
                    }
                },
                onCancel = { exitMultiSelect() },
                onShareSelected = {
                    if (selectedVideoIds.isNotEmpty()) {
                        val targets = displayedVideos.filter { it.id in selectedVideoIds }
                        FileOperationHelper.shareMultipleVideos(context, targets)
                        exitMultiSelect()
                    }
                },
                onCopySelected = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_COPY },
                onMoveSelected = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_MOVE },
                onVaultActionSelected = {
                    if (selectedVideoIds.isNotEmpty() && !isProcessingVault) {
                        if (!isVaultMode && !FileOperationHelper.hasAllFilesAccess()) {
                            Toast.makeText(context, "H\u00E3y b\u1EADt quy\u1EC1n Qu\u1EA3n l\u00FD t\u1EA5t c\u1EA3 t\u1EC7p \u0111\u1EC3 di chuy\u1EC3n tr\u1EF1c ti\u1EBFp", Toast.LENGTH_LONG).show()
                            FileOperationHelper.requestAllFilesAccess(context)
                            return@MultiSelectActionBar
                        }
                        val targets = displayedVideos.filter { it.id in selectedVideoIds }
                        requireAuthBeforeVaultAction { targetDecoy ->
                            scope.launch {
                                isProcessingVault = true
                                var okCount = 0
                                if (isVaultMode) {
                                    targets.forEach { v ->
                                        if (VaultManager.restoreVideoFromVault(context, v.id, isDecoyVault).isSuccess) okCount++
                                    }
                                    Toast.makeText(context, "\u0110\u00E3 kh\u00F4i ph\u1EE5c $okCount video", Toast.LENGTH_SHORT).show()
                                } else {
                                    targets.forEach { v ->
                                        if (VaultManager.hideVideoToVaultFast(context, v, targetDecoy) is VaultHideOutcome.Success) okCount++
                                    }
                                    Toast.makeText(context, "\u0110\u00E3 chuy\u1EC3n $okCount video v\u00E0o l\u01B0u tr\u1EEF b\u1EA3o m\u1EADt", Toast.LENGTH_SHORT).show()
                                }
                                isProcessingVault = false
                                exitMultiSelect()
                                refreshTrigger++
                            }
                        }
                    }
                },
                onDeleteSelected = { if (selectedVideoIds.isNotEmpty()) dialogType = FileDialogType.BATCH_DELETE }
            )
        } else {
            // Thanh Segmented Pill chỉ hiện ở trang gốc (không hiện khi đã mở thư mục con hoặc Kho Ẩn)
            if (!isVaultMode && selectedFolder == null) {
                HomeFeedFilterTabs(
                    activeFilter = activeFeedFilter,
                    watchLaterCount = watchExpData.watchLaterIds.size,
                    historyCount = watchExpData.history.size,
                    onSelectFilter = { activeFeedFilter = it },
                    onClearHistory = {
                        WatchExperienceManager.clearAllHistory(context)
                        Toast.makeText(context, "\u0110\u00E3 x\u00F3a l\u1ECBch s\u1EED xem", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Hàng tiện ích cực gọn (chỉ hiện khi đang xem danh sách video trong thư mục / Xem sau / Lịch sử / Kho Ẩn)
            val isViewingVideoList = selectedFolder != null || isVaultMode || searchQuery.isNotBlank() || activeFeedFilter != HomeFeedFilter.FOLDERS
            if (isViewingVideoList) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${displayedVideos.size} video",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isVaultMode) {
                            LiquidGlassPill(active = true, onClick = onOpenVaultPinSettings) {
                                Icon(AppIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("C\u00E0i \u0111\u1EB7t Kho", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Box {
                            LiquidGlassIconButton(
                                onClick = { showSortMenu = true },
                                size = 34.dp,
                                active = sortOption != SortOption.DATE_DESC
                            ) {
                                Icon(AppIcons.Sort, contentDescription = "S\u1EAFp x\u1EBFp", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                SortOption.values().forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (sortOption == opt) "\u2713 ${opt.label}" else "    ${opt.label}",
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

                        LiquidGlassIconButton(
                            onClick = { isGridView = !isGridView },
                            size = 34.dp,
                            active = isGridView
                        ) {
                            Icon(
                                imageVector = if (isGridView) AppIcons.ViewList else AppIcons.GridView,
                                contentDescription = "\u0110\u1ED5i ki\u1EC3u xem",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
                                modifier = Modifier.size(52.dp),
                                tint = palette.primaryGlow
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (isVaultMode) "Th\u01B0 m\u1EE5c l\u01B0u tr\u1EEF n\u1ED9i b\u1ED9 \u0111ang tr\u1ED1ng."
                                else "Ch\u01B0a t\u00ECm th\u1EA5y video ho\u1EB7c ch\u01B0a c\u1EA5p quy\u1EC1n b\u1ED9 nh\u1EDB.",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else if (selectedFolder == null && !isVaultMode && !isSearchingAll && activeFeedFilter == HomeFeedFilter.FOLDERS) {
                FolderListSection(
                    folders = folders,
                    folderListState = folderListState,
                    continueWatchingItems = continueWatchingItems,
                    watchLaterIds = watchLaterIdSet,
                    visibleVideos = visibleVideos,
                    onSelectFolder = { onSelectFolder(it) },
                    onPlayRecent = { recent, list -> onVideoClick(recent, applySorting(list, sortOption)) },
                    onRemoveContinueWatching = { vid ->
                        WatchExperienceManager.removeFromHistory(context, vid.id)
                        scope.launch { context.saveLastPosition(vid.id, 0L) }
                    }
                )
            } else if (displayedVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    LiquidGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(28.dp)
                        ) {
                            Icon(
                                imageVector = if (activeFeedFilter == HomeFeedFilter.WATCH_LATER) AppIcons.WatchLater else AppIcons.History,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = palette.primaryGlow
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (activeFeedFilter == HomeFeedFilter.WATCH_LATER) {
                                    "Ch\u01B0a c\u00F3 video trong danh s\u00E1ch Xem sau."
                                } else {
                                    "Ch\u01B0a c\u00F3 l\u1ECBch s\u1EED xem g\u1EA7n \u0111\u00E2y."
                                },
                                color = Color.White.copy(alpha = 0.80f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                VideoCollectionSection(
                    displayedVideos = displayedVideos,
                    videoListState = videoListState,
                    isGridView = isGridView,
                    isVaultMode = isVaultMode,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedVideoIds = selectedVideoIds,
                    savedPositions = savedPositions,
                    watchLaterIds = watchLaterIdSet,
                    lastViewedVideoId = lastViewedVideoId,
                    onItemClick = { video ->
                        if (isMultiSelectMode) {
                            if (video.id in selectedVideoIds) selectedVideoIds.remove(video.id)
                            else selectedVideoIds.add(video.id)
                        } else {
                            onVideoClick(video, displayedVideos)
                        }
                    },
                    onItemLongClick = { video ->
                        if (!isMultiSelectMode) {
                            isMultiSelectMode = true
                            selectedVideoIds.clear()
                            selectedVideoIds.add(video.id)
                        }
                    },
                    onOpenMenuFor = { showBottomSheetFor = it }
                )
            }
        }
    }

    showBottomSheetFor?.let { targetVideo ->
        VideoItemActionMenuDialog(
            targetVideo = targetVideo,
            onDismiss = { showBottomSheetFor = null },
            onStartMultiSelect = { vid ->
                isMultiSelectMode = true
                selectedVideoIds.clear()
                selectedVideoIds.add(vid.id)
            },
            onVaultToggleAction = { vid, isCurrentlyInVault ->
                if (!isCurrentlyInVault && !FileOperationHelper.hasAllFilesAccess()) {
                    Toast.makeText(context, "H\u00E3y b\u1EADt quy\u1EC1n Qu\u1EA3n l\u00FD t\u1EA5t c\u1EA3 t\u1EC7p \u0111\u1EC3 di chuy\u1EC3n tr\u1EF1c ti\u1EBFp", Toast.LENGTH_LONG).show()
                    FileOperationHelper.requestAllFilesAccess(context)
                    return@VideoItemActionMenuDialog
                }
                requireAuthBeforeVaultAction { targetDecoy ->
                    scope.launch {
                        isProcessingVault = true
                        if (isCurrentlyInVault) {
                            VaultManager.restoreVideoFromVault(context, vid.id, isDecoyVault).onSuccess { path ->
                                Toast.makeText(context, "\u0110\u00E3 kh\u00F4i ph\u1EE5c v\u1EC1: $path", Toast.LENGTH_SHORT).show()
                                refreshTrigger++
                            }.onFailure { err ->
                                Toast.makeText(context, "L\u1ED7i kh\u00F4i ph\u1EE5c: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            when (val out = VaultManager.hideVideoToVaultFast(context, vid, targetDecoy)) {
                                is VaultHideOutcome.Success -> {
                                    Toast.makeText(context, "\u0110\u00E3 chuy\u1EC3n v\u00E0o l\u01B0u tr\u1EEF b\u1EA3o m\u1EADt", Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                }
                                is VaultHideOutcome.NeedsAllFilesPermission -> {
                                    FileOperationHelper.requestAllFilesAccess(context)
                                }
                                is VaultHideOutcome.Error -> {
                                    Toast.makeText(context, "L\u1ED7i: ${out.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        isProcessingVault = false
                    }
                }
            },
            onSelectDialogType = { vid, type ->
                activeVideoForAction = vid
                dialogType = type
            }
        )
    }

    if (showConfirmHidePinDialog) {
        ConfirmHidePinDialog(
            vaultSecState = vaultSecState,
            scope = scope,
            context = context,
            onDismiss = {
                showConfirmHidePinDialog = false
                pendingVaultAuthAction = null
            },
            onConfirmed = { isDecoyTarget ->
                showConfirmHidePinDialog = false
                val act = pendingVaultAuthAction
                pendingVaultAuthAction = null
                act?.invoke(isDecoyTarget)
            }
        )
    }

    FileOperationModals(
        dialogType = dialogType,
        activeVideo = activeVideoForAction,
        selectedVideoIds = selectedVideoIds,
        displayedVideos = displayedVideos,
        rawVideos = rawVideos,
        folders = folders,
        isVaultMode = isVaultMode,
        isDecoyVault = isDecoyVault,
        scope = scope,
        context = context,
        onDismiss = { dialogType = FileDialogType.NONE },
        onExitMultiSelect = { exitMultiSelect() },
        onRefresh = { refreshTrigger++ },
        onRequestMediaStorePermission = { pi, newName ->
            pendingRenameNewName = newName
            mediaStorePermissionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        }
    )
}