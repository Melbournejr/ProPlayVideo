package com.videoplayer.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

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
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkLatestRelease(repoFullName: String): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${repoFullName.trim()}/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Không tìm thấy bản Release nào trên repo $repoFullName"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Dữ liệu trống"))
                val release = gson.fromJson(body, GitHubRelease::class.java)
                Result.success(release)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadApk(downloadUrl: String, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.cacheDir, "update_ota.apk")
            if (apkFile.exists()) apkFile.delete()
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.failure(Exception("Tải thất bại: HTTP ${response.code}"))
                val body = response.body ?: return@withContext Result.failure(Exception("File rỗng"))
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                onProgress(((downloaded * 100) / total).toInt())
                            }
                        }
                        output.flush()
                    }
                }
            }
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
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
}