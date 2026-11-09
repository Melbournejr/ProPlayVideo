package com.videoplayer.app.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileOperationHelper {

    fun formatDate(timestampSec: Long): String {
        val ms = if (timestampSec < 10_000_000_000L) timestampSec * 1000L else timestampSec
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    }

    fun getVideoContentUri(video: VideoItem): Uri? {
        val idLong = video.id.toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
    }

    fun createDeleteRequestPendingIntent(context: Context, videos: List<VideoItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = videos.mapNotNull { getVideoContentUri(it) }
            if (uris.isNotEmpty()) {
                return MediaStore.createDeleteRequest(context.contentResolver, uris)
            }
        }
        return null
    }

    fun createWriteRequestPendingIntent(context: Context, videos: List<VideoItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = videos.mapNotNull { getVideoContentUri(it) }
            if (uris.isNotEmpty()) {
                return MediaStore.createWriteRequest(context.contentResolver, uris)
            }
        }
        return null
    }

    suspend fun deleteVideo(context: Context, video: VideoItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = getVideoContentUri(video)
            if (uri != null) {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) return@withContext Result.success(Unit)
            }
            val file = File(video.path)
            if (file.exists() && file.delete()) {
                scanPaths(context, listOf(video.path))
                return@withContext Result.success(Unit)
            }
            Result.failure(SecurityException("Cần xác nhận quyền xóa qua hộp thoại hệ thống MediaStore"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameVideo(context: Context, video: VideoItem, newNameWithoutExt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ext = video.displayName.substringAfterLast('.', "mp4")
            val cleanName = newNameWithoutExt.trim()
            val finalFileName = if (ext.isNotEmpty() && !cleanName.endsWith(".$ext", true)) "$cleanName.$ext" else cleanName

            val uri = getVideoContentUri(video)
            if (uri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated > 0) return@withContext Result.success(finalFileName)
            }

            val srcFile = File(video.path)
            val destFile = File(srcFile.parentFile, finalFileName)
            if (srcFile.exists() && srcFile.renameTo(destFile)) {
                scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
                return@withContext Result.success(finalFileName)
            }
            Result.failure(SecurityException("Không thể đổi tên. Hãy cấp quyền ghi MediaStore cho tệp này."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyOrMoveVideo(
        context: Context,
        video: VideoItem,
        targetDirPath: String,
        isMove: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = getVideoContentUri(video)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                val folderName = File(targetDirPath).name.ifBlank { "VideoPlayerPro" }
                val relativePath = "${Environment.DIRECTORY_MOVIES}/$folderName"

                if (isMove) {
                    val moveValues = ContentValues().apply {
                        put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    }
                    val updated = context.contentResolver.update(uri, moveValues, null, null)
                    if (updated > 0) return@withContext Result.success("$relativePath/${video.displayName}")
                }

                // Copy qua MediaStore stream
                val insertValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, video.displayName)
                    put(MediaStore.Video.Media.MIME_TYPE, video.mimeType.ifBlank { "video/mp4" })
                    put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val destUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, insertValues)
                    ?: return@withContext Result.failure(Exception("Không thể tạo tệp đích trong MediaStore"))

                context.contentResolver.openInputStream(uri)?.use { input ->
                    context.contentResolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                insertValues.clear()
                insertValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(destUri, insertValues, null, null)

                if (isMove) {
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                return@withContext Result.success("$relativePath/${video.displayName}")
            }

            // Fallback cho Android 9 trở xuống
            val srcFile = File(video.path)
            val targetDir = File(targetDirPath).apply { if (!exists()) mkdirs() }
            val destFile = File(targetDir, srcFile.name)
            srcFile.copyTo(destFile, overwrite = true)
            if (isMove) srcFile.delete()
            scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scanPaths(context: Context, paths: List<String>) {
        try {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        } catch (_: Exception) {}
    }
}