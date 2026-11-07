package com.videoplayer.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VideoSnapshotHelper {

    suspend fun captureCurrentFrame(
        context: Context,
        videoPath: String?,
        positionMs: Long,
        videoTitle: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (videoPath.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Chỉ hỗ trợ chụp khung hình cho video trong máy"))
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            val bitmap: Bitmap = retriever.getFrameAtTime(
                positionMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: retriever.getFrameAtTime(
                positionMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return@withContext Result.failure(Exception("Không thể trích xuất khung hình tại vị trí này"))

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safeName = videoTitle.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(25)
            val fileName = "Snapshot_${safeName}_$timestamp.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ProPlayVideo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Không thể tạo file ảnh trong MediaStore"))

                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ProPlayVideo"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
            }
            bitmap.recycle()
            Result.success("Pictures/ProPlayVideo/$fileName")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}