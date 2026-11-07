package com.videoplayer.app.util

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
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
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun formatDate(timestampSec: Long): String {
        val ms = if (timestampSec < 10_000_000_000L) timestampSec * 1000L else timestampSec
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    }

    suspend fun deleteVideo(context: Context, video: VideoItem): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(video.path)
            var deleted = false
            if (file.exists() && file.delete()) {
                deleted = true
            }
            val idLong = video.id.toLongOrNull()
            if (idLong != null) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) deleted = true
            }
            scanPaths(context, listOf(video.path))
            if (deleted || !file.exists()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Không thể xóa file. Hãy cấp quyền Quản lý tất cả tệp."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameVideo(context: Context, video: VideoItem, newNameWithoutExt: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(video.path)
            if (!srcFile.exists()) return@withContext Result.failure(Exception("File gốc không tồn tại"))
            val ext = srcFile.extension
            val cleanName = newNameWithoutExt.trim()
            val finalFileName = if (ext.isNotEmpty() && !cleanName.endsWith(".$ext", true)) "$cleanName.$ext" else cleanName
            val destFile = File(srcFile.parentFile, finalFileName)
            if (destFile.exists()) return@withContext Result.failure(Exception("Tên file đã tồn tại trong thư mục"))

            val success = srcFile.renameTo(destFile)
            if (!success) {
                srcFile.copyTo(destFile, overwrite = false)
                srcFile.delete()
            }
            scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyOrMoveVideo(
        context: Context,
        video: VideoItem,
        targetDirPath: String,
        isMove: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val srcFile = File(video.path)
            if (!srcFile.exists()) return@withContext Result.failure(Exception("File gốc không tồn tại"))
            val targetDir = File(targetDirPath)
            if (!targetDir.exists()) targetDir.mkdirs()

            var destFile = File(targetDir, srcFile.name)
            if (destFile.absolutePath == srcFile.absolutePath) {
                return@withContext Result.failure(Exception("Thư mục đích trùng với thư mục hiện tại"))
            }
            if (destFile.exists()) {
                val base = srcFile.nameWithoutExtension
                val ext = srcFile.extension
                destFile = File(targetDir, "${base}_copy.${ext}")
            }

            if (isMove) {
                val moved = srcFile.renameTo(destFile)
                if (!moved) {
                    srcFile.copyTo(destFile, overwrite = true)
                    srcFile.delete()
                    val idLong = video.id.toLongOrNull()
                    if (idLong != null) {
                        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
                        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                    }
                }
            } else {
                srcFile.copyTo(destFile, overwrite = true)
            }

            scanPaths(context, listOf(srcFile.absolutePath, destFile.absolutePath))
            Result.success(destFile)
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