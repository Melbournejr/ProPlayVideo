package com.videoplayer.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()

    private val mirrors = listOf(
        "", // Trực tiếp GitHub
        "https://ghfast.top/",
        "https://mirror.ghproxy.com/"
    )

    suspend fun checkLatestRelease(repoFullName: String): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${repoFullName.trim()}/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ProPlayVideo-OTA")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Không tìm thấy bản Release"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Dữ liệu trống"))
                Result.success(gson.fromJson(body, GitHubRelease::class.java))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tải bằng 8 luồng song song (8-Thread Parallel Downloader) + Báo cáo tốc độ thực tế MB/s
     */
    suspend fun downloadApkMultiThread(
        rawUrl: String,
        expectedSize: Long = 0L,
        mirrorIndex: Int = 0,
        onProgress: (percent: Int, speedText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val prefix = mirrors.getOrElse(mirrorIndex) { "" }
            val downloadUrl = if (prefix.isNotEmpty() && rawUrl.startsWith("https://github.com/")) {
                "$prefix$rawUrl"
            } else {
                rawUrl
            }

            val apkFile = File(context.cacheDir, "update_ota.apk")
            if (apkFile.exists()) apkFile.delete()

            val totalSize = if (expectedSize > 0L) expectedSize else fetchContentLength(downloadUrl)
            val startTimeMs = System.currentTimeMillis()

            if (totalSize > 1_000_000L) {
                RandomAccessFile(apkFile, "rw").use { raf ->
                    raf.setLength(totalSize)
                }

                val threadCount = 8
                val chunkSize = totalSize / threadCount
                val totalDownloaded = AtomicLong(0L)
                val lastReportedPercent = AtomicInteger(-1)

                coroutineScope {
                    val jobs = (0 until threadCount).map { index ->
                        val start = index * chunkSize
                        val end = if (index == threadCount - 1) totalSize - 1 else (start + chunkSize - 1)

                        async(Dispatchers.IO) {
                            downloadChunk(downloadUrl, apkFile, start, end) { bytesRead ->
                                val currentTotal = totalDownloaded.addAndGet(bytesRead)
                                val percent = ((currentTotal * 100L) / totalSize).toInt().coerceIn(0, 100)
                                val prev = lastReportedPercent.get()
                                if (percent > prev && lastReportedPercent.compareAndSet(prev, percent)) {
                                    val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000f).coerceAtLeast(0.2f)
                                    val speedMb = (currentTotal / (1024f * 1024f)) / elapsedSec
                                    onProgress(percent, "%.2f MB/s".format(speedMb))
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            } else {
                downloadSingleStream(downloadUrl, apkFile, totalSize, startTimeMs, onProgress)
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tải bằng trình DownloadManager cấp hệ thống của Android (Tự động tối ưu hóa phần cứng mạng & tự mở cài đặt)
     */
    fun downloadWithSystemManager(rawUrl: String, fileName: String, useMirror: Boolean): Long {
        val downloadUrl = if (useMirror && rawUrl.startsWith("https://github.com/")) {
            "https://ghfast.top/$rawUrl"
        } else {
            rawUrl
        }
        cleanupOldApks()
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val destFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destFile.exists()) destFile.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Đang cập nhật $fileName")
            .setDescription("Tải bản cập nhật OTA bằng trình tăng tốc hệ thống")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId) {
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    if (destFile.exists()) {
                        installApk(destFile)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        return downloadId
    }

    private fun fetchContentLength(url: String): Long {
        return try {
            val req = Request.Builder().url(url).head().header("User-Agent", "ProPlayVideo-OTA").build()
            client.newCall(req).execute().use { resp ->
                resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        } catch (_: Exception) { -1L }
    }

    private fun downloadChunk(
        url: String,
        file: File,
        start: Long,
        end: Long,
        onBytes: (Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .header("User-Agent", "ProPlayVideo-OTA")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("HTTP ${response.code}")
            }
            val body = response.body ?: throw Exception("Rỗng")
            body.byteStream().use { input ->
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(128 * 1024) // Bộ đệm 128KB siêu tốc
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        }
    }

    private fun downloadSingleStream(
        url: String,
        file: File,
        totalSize: Long,
        startTimeMs: Long,
        onProgress: (Int, String) -> Unit
    ) {
        val request = Request.Builder().url(url).header("User-Agent", "ProPlayVideo-OTA").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("File rỗng")
            val contentLen = if (totalSize > 0) totalSize else body.contentLength()
            var downloaded = 0L
            var lastPercent = -1

            body.byteStream().use { input ->
                file.outputStream().buffered(128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLen > 0) {
                            val percent = ((downloaded * 100L) / contentLen).toInt().coerceIn(0, 100)
                            if (percent > lastPercent) {
                                lastPercent = percent
                                val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000f).coerceAtLeast(0.2f)
                                val speedMb = (downloaded / (1024f * 1024f)) / elapsedSec
                                onProgress(percent, "%.2f MB/s".format(speedMb))
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(context, "com.videoplayer.app.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
    /**
     * Quét và xóa sạch toàn bộ các file .apk cập nhật cũ bị tích tụ trong User Data (ExternalFilesDir) & Cache
     * Trả về tổng số Bytes đã giải phóng được.
     */
    fun cleanupOldApks(): Long {
        var freedBytes = 0L
        val dirsToClean = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        )
        dirsToClean.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                    val len = file.length()
                    if (file.delete()) {
                        freedBytes += len
                    }
                }
            }
        }
        return freedBytes
    }

    fun getOccupiedApkStorageBytes(): Long {
        var total = 0L
        val dirs = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        )
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                    total += file.length()
                }
            }
        }
        return total
    }

    fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
        if (cleanCurrent == cleanRemote) return false

        val curParts = cleanCurrent.split(".", "-").mapNotNull { it.toIntOrNull() }
        val remParts = cleanRemote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(curParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return cleanRemote != cleanCurrent
    }
}