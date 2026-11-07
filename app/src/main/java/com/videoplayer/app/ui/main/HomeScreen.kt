package com.videoplayer.app.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import kotlinx.coroutines.flow.collectAsState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onVideoClick: (VideoItem) -> Unit,
    onStreamClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { VideoRepository(context) }
    val videos by repository.getAllVideos().collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(videos) { video ->
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
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material3.Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = video.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "${video.sizeFormatted} • ${video.durationFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onStreamClick,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material3.Icons.Default.Stream,
                contentDescription = null
            )
        }
    }
}
