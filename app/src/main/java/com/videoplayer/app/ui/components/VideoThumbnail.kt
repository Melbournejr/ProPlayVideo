package com.videoplayer.app.ui.components

import android.content.ContentUris
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircleFilled
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbnailCache {
    val cache = LruCache<String, Bitmap>(60)
}

@Composable
fun VideoThumbnailView(
    video: VideoItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf(ThumbnailCache.cache.get(video.id)) }

    LaunchedEffect(video.id) {
        if (bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val idLong = video.id.toLongOrNull()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && idLong != null) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
                        context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                    } else {
                        @Suppress("DEPRECATION")
                        ThumbnailUtils.createVideoThumbnail(video.path, MediaStore.Images.Thumbnails.MINI_KIND)
                    }
                } catch (_: Exception) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ThumbnailUtils.createVideoThumbnail(File(video.path), Size(320, 180), null)
                        } else null
                    } catch (_: Exception) { null }
                }
            }
            if (loaded != null) {
                ThumbnailCache.cache.put(video.id, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E2C)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = video.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }

        // Nhãn thời lượng ở góc dưới bên phải ảnh thu nhỏ
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            Text(
                text = video.durationFormatted,
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
}