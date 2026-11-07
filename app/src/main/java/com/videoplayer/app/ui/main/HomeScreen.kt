package com.videoplayer.app.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.observeAllPositions
import com.videoplayer.app.data.local.observeLastPlayedVideoId
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
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

private enum class FileDialogType {
    NONE, INFO, RENAME, COPY, MOVE, DELETE
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    onStreamClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val repository = remember(context) { VideoRepository(context) }
    val videos by remember(refreshTrigger) { repository.getAllVideos() }.collectAsState(initial = emptyList())
    val savedPositions by context.observeAllPositions().collectAsState(initial = emptyMap())
    val lastPlayedId by context.observeLastPlayedVideoId().collectAsState(initial = null)

    // State phục vụ Quản lý File
    var activeVideoForAction by remember { mutableStateOf<VideoItem?>(null) }
    var dialogType by remember { mutableStateOf(FileDialogType.NONE) }
    var showBottomSheetFor by remember { mutableStateOf<VideoItem?>(null) }

    BackHandler(enabled = selectedFolder != null) {
        onSelectFolder(null)
    }

    val folders = remember(videos) {
        videos.groupBy { it.folder }.map { (folderName, list) ->
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

    val lastPlayedVideo = remember(videos, lastPlayedId) {
        videos.find { it.id == lastPlayedId }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chưa tìm thấy video hoặc chưa cấp quyền bộ nhớ.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { refreshTrigger++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quét lại thiết bị")
                    }
                }
            }
        } else if (selectedFolder == null) {
            // ================= TRANG ĐẦU TIÊN: DANH SÁCH THƯ MỤC =================
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                lastPlayedVideo?.let { recent ->
                    val pos = savedPositions[recent.id] ?: 0L
                    if (pos > 2000L) {
                        item {
                            val recentPlaylist = remember(videos, recent.folder) {
                                videos.filter { it.folder == recent.folder }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(recent, recentPlaylist) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VideoThumbnailView(
                                        video = recent,
                                        modifier = Modifier.size(width = 100.dp, height = 60.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(
                                            text = "▶ Tiếp tục xem",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = recent.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val progress = (pos.toFloat() / recent.duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                        LinearProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thư mục video (${folders.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                        }
                    }
                }

                items(folders, key = { it.name }) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder(folder.name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                VideoThumbnailView(
                                    video = folder.previewVideo,
                                    modifier = Modifier.size(width = 96.dp, height = 60.dp)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(topStart = 6.dp, bottomEnd = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(4.dp).size(14.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${folder.count} video • ${folder.previewVideo.let { formatFolderSize(folder.totalSize) }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            // ================= TRANG CHI TIẾT VIDEO TRONG THƯ MỤC =================
            val folderVideos = remember(videos, selectedFolder) {
                videos.filter { it.folder == selectedFolder }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folderVideos, key = { it.id }) { video ->
                    val savedPos = savedPositions[video.id] ?: 0L
                    val progress = if (video.duration > 0 && savedPos > 1000L) {
                        (savedPos.toFloat() / video.duration).coerceIn(0f, 1f)
                    } else 0f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(video, folderVideos) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoThumbnailView(
                                video = video,
                                modifier = Modifier.size(width = 110.dp, height = 64.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    text = video.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
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
                                    color = if (savedPos > 2000L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (progress > 0f) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                            IconButton(onClick = { showBottomSheetFor = video }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Quản lý file")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onStreamClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Stream, contentDescription = "Stream URL")
        }
    }

    // ================= MENU QUẢN LÝ FILE (KHI BẤM NÚT 3 CHẤM) =================
    showBottomSheetFor?.let { targetVideo ->
        AlertDialog(
            onDismissRequest = { showBottomSheetFor = null },
            title = { Text(targetVideo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (!FileOperationHelper.hasAllFilesAccess()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable {
                                FileOperationHelper.requestAllFilesAccess(context)
                            }
                        ) {
                            Text(
                                "⚠️ Chạm vào đây để cấp quyền 'Quản lý tất cả tệp' trước khi Đổi tên / Copy / Di chuyển / Xóa.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    ListItem(
                        headlineContent = { Text("Xem thông tin chi tiết") },
                        leadingContent = { Icon(Icons.Default.Info, null) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.INFO
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Đổi tên video") },
                        leadingContent = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.RENAME
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Sao chép (Copy) sang thư mục khác") },
                        leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                        modifier = Modifier.clickable {
                            activeVideoForAction = targetVideo
                            dialogType = FileDialogType.COPY
                            showBottomSheetFor = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Di chuyển (Move) sang thư mục khác") },
                        leadingContent = { Icon(Icons.Default.DriveFileMove, null) },
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
                    title = { Text(if (isMove) "Di chuyển Video tới..." else "Sao chép Video tới...") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Chọn nhanh thư mục có sẵn hoặc nhập đường dẫn thư mục mới:", style = MaterialTheme.typography.bodySmall)
                            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                                items(folders) { f ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                            targetPath = f.folderPath
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(f.name, fontWeight = FontWeight.SemiBold)
                                                Text(f.folderPath, style = MaterialTheme.typography.labelSmall)
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