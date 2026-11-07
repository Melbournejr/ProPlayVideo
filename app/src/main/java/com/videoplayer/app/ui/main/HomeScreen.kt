package com.videoplayer.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (VideoItem) -> Unit,
    onStreamClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val repository = remember(context) { VideoRepository(context) }
    val videos by remember(refreshTrigger) { repository.getAllVideos() }.collectAsState(initial = emptyList())
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    val folders = remember(videos) { videos.map { it.folder }.distinct().sorted() }
    val displayedVideos = remember(videos, selectedFolder) {
        if (selectedFolder == null) videos else videos.filter { it.folder == selectedFolder }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thanh lọc theo thư mục
            if (folders.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = if (selectedFolder == null) 0 else folders.indexOf(selectedFolder) + 1,
                    edgePadding = 16.dp
                ) {
                    Tab(
                        selected = selectedFolder == null,
                        onClick = { selectedFolder = null },
                        text = { Text("Tất cả (${videos.size})") }
                    )
                    folders.forEach { folder ->
                        Tab(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            text = { Text(folder) }
                        )
                    }
                }
            }

            if (displayedVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Không tìm thấy video hoặc chưa cấp quyền bộ nhớ.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { refreshTrigger++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quét lại bộ nhớ")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
                ) {
                    items(displayedVideos, key = { it.id }) { video ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onVideoClick(video) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        text = video.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${video.folder} • ${video.sizeFormatted} • ${video.durationFormatted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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