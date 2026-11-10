package com.videoplayer.app.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileOperationHelper {

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun resolveShareableUri(context: Context, video: VideoItem): Uri? {
        return try {
            val idLong = video.id.toLongOrNull()
            if (idLong != null && !video.id.startsWith("vault_")) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
            } else {
                val rawPath = video.path.removePrefix("vault://")
                val file = File(rawPath)
                if (file.exists()) {
                    FileProvider.getUriForFile(context, "com.videoplayer.app.fileprovider", file)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareVideo(context: Context, video: VideoItem) {
        val uri = resolveShareableUri(context, video) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType.ifBlank { "video/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, video.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Chia sẻ: ${video.displayName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareMultipleVideos(context: Context, videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        if (videos.size == 1) {
            shareVideo(context, videos.first())
            return
        }
        val uris = ArrayList<Uri>()
        videos.forEach { v ->
            resolveShareableUri(context, v)?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Chia sẻ ${uris.size} video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

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
            val file = File(video.path)
            if (file.exists() && file.delete()) {
                val uri = getVideoContentUri(video)
                if (uri != null) {
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                scanPaths(context, listOf(video.path))
                return@withContext Result.success(Unit)
            }
            val uri = getVideoContentUri(video)
            if (uri != null) {
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) return@withContext Result.success(Unit)
            }
            Result.failure(SecurityException("Cần cấp quyền Quản lý tất cả tệp để xóa trực tiếp"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameVideo(context: Context, video: VideoItem, newNameWithoutExt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ext = video.displayName.substringAfterLast('.', "mp4")
            val cleanName = newNameWithoutExt.trim()
            val finalFileName = if (ext.isNotEmpty() && !cleanName.endsWith(".$ext", true)) "$cleanName.$ext" else cleanName

            val srcFile = File(video.path)
            val destFile = File(srcFile.parentFile, finalFileName)
            if (srcFile.exists() && srcFile.renameTo(destFile)) {
                scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
                return@withContext Result.success(finalFileName)
            }

            val uri = getVideoContentUri(video)
            if (uri != null) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalFileName)
                }
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated > 0) return@withContext Result.success(finalFileName)
            }
            Result.failure(SecurityException("Không thể đổi tên tệp"))
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
            val srcFile = File(video.path)
            val targetDir = File(targetDirPath).apply { if (!exists()) mkdirs() }
            val destFile = File(targetDir, srcFile.name)

            // Nếu là Di chuyển (Move) và có quyền All Files -> Đổi trực tiếp đường dẫn File.renameTo trong 0.001s!
            if (isMove && srcFile.exists() && srcFile.renameTo(destFile)) {
                val uri = getVideoContentUri(video)
                if (uri != null) {
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
                return@withContext Result.success(destFile.absolutePath)
            }

            if (srcFile.exists()) {
                srcFile.copyTo(destFile, overwrite = true)
                if (isMove) srcFile.delete()
                scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
                return@withContext Result.success(destFile.absolutePath)
            }
            Result.failure(Exception("Không tìm thấy tệp nguồn"))
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