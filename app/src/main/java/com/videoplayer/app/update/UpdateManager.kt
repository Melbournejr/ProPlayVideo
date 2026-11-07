package com.videoplayer.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.squareup.okhttp3.OkHttpClient
import com.squareup.okhttp3.Request
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class GitHubRelease(
    val tag_name: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient()
    private val gson = Gson()
    private const val REPO_OWNER = "your-github-username"
    private const val REPO_NAME = "VideoPlayerApp"

    suspend fun checkForUpdate(currentVersionCode: Int): GitHubRelease? {
        return suspendCoroutine { cont ->
            val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).enqueue(object : com.squareup.okhttp3.Callback {
                override fun onFailure(call: com.squareup.okhttp3.Call, e: java.io.IOException) {
                    cont.resume(null)
                }

                override fun onResponse(call: com.squareup.okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: run { cont.resume(null); return }
                    try {
                        val release = gson.fromJson(body, GitHubRelease::class.java)
                        val tag = release.tag_name.removePrefix("v").toIntOrNull() ?: 0
                        if (tag > currentVersionCode) {
                            cont.resume(release)
                        } else {
                            cont.resume(null)
                        }
                    } catch (e: Exception) {
                        cont.resume(null)
                    }
                }
            })
        }
    }

    fun downloadApk(downloadUrl: String, fileName: String, onProgress: (progress: Int) -> Unit): File {
        val apkFile = File(context.cacheDir, fileName)
        val request = Request.Builder().url(downloadUrl).build()
        client.newCall(request).enqueue(object : com.squareup.okhttp3.Callback {
            override fun onFailure(call: com.squareup.okhttp3.Call, e: java.io.IOException) {}

            override fun onResponse(call: com.squareup.okhttp3.Call, response: okhttp3.Response) {
                val inputStream = response.body?.byteStream() ?: return
                val totalLength = response.body?.contentLength() ?: 0L
                var downloaded = 0L
                val buffer = ByteArray(8192)
                var out: FileOutputStream? = null
                try {
                    out = FileOutputStream(apkFile)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (totalLength > 0) {
                            val progress = ((downloaded * 100) / totalLength).toInt()
                            onProgress.invoke(progress)
                        }
                    }
                    out.flush()
                } catch (e: Exception) {
                    // Bỏ qua lỗi tải xuống
                } finally {
                    inputStream.close()
                    out?.close()
                }
            }
        })
        return apkFile
    }

    fun installApk(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "com.videoplayer.app.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
