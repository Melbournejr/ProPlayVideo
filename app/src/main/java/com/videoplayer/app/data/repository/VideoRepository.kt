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
        val items = withContext(Dispatchers.Default) { scanInternal() }
        emit(items)
    }

    private fun scanInternal(): List<VideoItem> {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = resolver.query(uri, projection, null, null, sortOrder)
        val items = mutableListOf<VideoItem>()

        cursor?.use { cur ->
            while (cur.moveToNext()) {
                items.add(
                    VideoItem(
                        id = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media._ID)),
                        title = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)),
                        displayName = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
                        path = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)),
                        size = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                        duration = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                        dateAdded = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)),
                        folder = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)),
                        mimeType = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
                    )
                )
            }
        }
        return items
    }

    fun getFolders(): Flow<List<String>> = flow {
        val folders = withContext(Dispatchers.Default) { scanFoldersInternal() }
        emit(folders)
    }

    private fun scanFoldersInternal(): List<String> {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val folderProjection = arrayOf(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val cursor: Cursor? = resolver.query(uri, folderProjection, null, null, null)
        val folders = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        cursor?.use { cur ->
            while (cur.moveToNext()) {
                val folder = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME))
                if (folder !in seen) {
                    seen.add(folder)
                    folders.add(folder)
                }
            }
        }
        return folders.sorted()
    }

    fun getVideosByFolder(folder: String): Flow<List<VideoItem>> = flow {
        val items = withContext(Dispatchers.Default) { scanByFolderInternal(folder) }
        emit(items)
    }

    private fun scanByFolderInternal(folder: String): List<VideoItem> {
        val resolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(folder)
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
        val items = mutableListOf<VideoItem>()

        cursor?.use { cur ->
            while (cur.moveToNext()) {
                items.add(
                    VideoItem(
                        id = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media._ID)),
                        title = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)),
                        displayName = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)),
                        path = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)),
                        size = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)),
                        duration = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)),
                        dateAdded = cur.getLong(cur.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)),
                        folder = folder,
                        mimeType = cur.getString(cur.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE))
                    )
                )
            }
        }
        return items
    }
}
