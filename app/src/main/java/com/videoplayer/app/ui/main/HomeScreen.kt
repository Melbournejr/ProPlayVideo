package com.videoplayer.app.ui.main

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

data class FolderSummary(
    val name: String,
    val count: Int,
    val totalSize: Long,
    val previewVideo: VideoItem
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedFolder: String?,
    onSelectFolder: (String?) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onStreamClick: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val repository = remember(context) { VideoRepository(context) }
    val videos by remember(refreshTrigger) { repository.getAllVideos() }.collectAsState(initial = emptyList())
    val savedPositions by context.observeAllPositions().collectAsState(initial = emptyMap())
    val lastPlayedId by context.observeLastPlayedVideoId().collectAsState(initial = null)

    // Xử lý nút Back vật lý trên điện thoại khi đang ở trong 1 thư mục
    BackHandler(enabled = selectedFolder != null) {
        onSelectFolder(null)
    }

    val folders = remember(videos) {
        videos.groupBy { it.folder }.map { (folderName, list) ->
            FolderSummary(
                name = folderName,
                count = list.size,
                totalSize = list.sumOf { it.size },
                previewVideo = list.first()
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
                // Thẻ "Tiếp tục xem" nếu có video đang xem dở
                lastPlayedVideo?.let { recent ->
                    val pos = savedPositions[recent.id] ?: 0L
                    if (pos > 2000L) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(recent) }
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
                    Text(
                        text = "Thư mục video (${folders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                    )
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
                                    text = "${folder.count} video",
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
                            .clickable { onVideoClick(video) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoThumbnailView(
                                video = video,
                                modifier = Modifier.size(width = 112.dp, height = 66.dp)
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
}