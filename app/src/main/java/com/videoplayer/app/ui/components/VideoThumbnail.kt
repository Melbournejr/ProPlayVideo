package com.videoplayer.app.ui.components

import android.content.ContentUris
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val thumbnailCache = object : LruCache<String, Bitmap>(60) {}

@Composable
fun VideoThumbnailView(
    video: VideoItem,
    modifier: Modifier = Modifier,
    progressFraction: Float = 0f,
    isWatchLater: Boolean = false,
    showDurationBadge: Boolean = true
) {
    val context = LocalContext.current
    val style = LocalGlassStyle.current
    val palette = style.palette
    var bitmap by remember(video.id) { mutableStateOf(thumbnailCache.get(video.id)) }

    LaunchedEffect(video.id) {
        if (bitmap == null) {
            withContext(Dispatchers.IO) {
                try {
                    val loaded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && video.id.toLongOrNull() != null) {
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            video.id.toLong()
                        )
                        context.contentResolver.loadThumbnail(uri, Size(480, 270), null)
                    } else {
                        val retriever = MediaMetadataRetriever()
                        val rawPath = video.path.removePrefix("vault://")
                        retriever.setDataSource(rawPath)
                        val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        retriever.release()
                        frame
                    }
                    if (loaded != null) {
                        thumbnailCache.put(video.id, loaded)
                        bitmap = loaded
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(style.thumbCorner))
            .background(if (style.isLightMode) Color(0xFFCBD5E1) else Color(0xFF0E131D)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = AppIcons.PlayCircleFilled,
                contentDescription = null,
                tint = palette.primaryGlow.copy(alpha = 0.65f),
                modifier = Modifier.size(30.dp)
            )
        }

        if (isWatchLater) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(if (style.isProMonitor) 3.dp else 6.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = AppIcons.WatchLater,
                    contentDescription = "Xem sau",
                    tint = palette.primaryGlow,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        if (showDurationBadge && video.duration > 0L) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (progressFraction > 0.01f) 7.dp else 5.dp, end = 5.dp)
                    .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(if (style.isProMonitor) 3.dp else 5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = video.durationFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (style.useMonoMetrics) FontFamily.Monospace else FontFamily.SansSerif,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (progressFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction.coerceIn(0.03f, 1f))
                        .background(palette.primaryGlow)
                )
            }
        }
    }
}