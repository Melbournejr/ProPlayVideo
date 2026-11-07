package com.videoplayer.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class VideoRepository(private val context: Context) {

    private val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.TITLE,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.MIME_TYPE
    )

    fun getAllVideos(): Flow<List<VideoItem>> = flow {
        val items = withContext(Dispatchers.IO) { scanInternal() }
        emit(items)
    }

    private fun scanInternal(): List<VideoItem> {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val items = mutableListOf<VideoItem>()

        try {
            val cursor: Cursor? = resolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media._ID)) ?: ""
                    val title = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)) ?: "Video"
                    val displayName = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: title
                    val path = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)) ?: ""
                    val size = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    val duration = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                    val dateAdded = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                    val folder = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)) ?: "Bộ nhớ trong"
                    val mimeType = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)) ?: "video/*"

                    if (path.isNotEmpty()) {
                        items.add(VideoItem(id, title, displayName, path, size, duration, dateAdded, folder, mimeType))
                    }
                }
            }
        } catch (_: Exception) {}
        return items
    }
}