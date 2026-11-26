package com.videoplayer.app.util

import android.content.Context
import android.net.Uri
import com.videoplayer.app.data.local.M3uChannelItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Bo phan tich danh sach kenh IPTV dinh dang .m3u / .m3u8:
 * - Ho tro doc tu duong dan mang (HTTP/HTTPS) hoac tep noi bo qua Storage Access Framework
 * - Trich xuat ten kenh, nhom kenh (group-title) va URL phat
 */
object M3uPlaylistParser {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val groupTitleRegex = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val tvgNameRegex = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parseText(rawContent: String): List<M3uChannelItem> {
        val result = mutableListOf<M3uChannelItem>()
        val lines = rawContent.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()

        var pendingName: String? = null
        var pendingGroup = ""
        var idx = 0

        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaIndex = line.lastIndexOf(',')
                    val nameAfterComma = if (commaIndex >= 0 && commaIndex < line.length - 1) {
                        line.substring(commaIndex + 1).trim()
                    } else ""
                    val tvgName = tvgNameRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    pendingName = nameAfterComma.ifBlank { tvgName }.ifBlank { "K\u00EAnh #${idx + 1}" }
                    pendingGroup = groupTitleRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                }
                line.startsWith("#") -> {
                    // Bo qua cac the metadata khac
                }
                line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) ||
                    line.startsWith("rtmp://", ignoreCase = true) ||
                    line.startsWith("rtsp://", ignoreCase = true) -> {
                    idx++
                    val channelName = pendingName ?: "K\u00EAnh #$idx"
                    result.add(
                        M3uChannelItem(
                            id = "m3u_${idx}_${line.hashCode()}",
                            name = channelName,
                            url = line,
                            group = pendingGroup
                        )
                    )
                    pendingName = null
                    pendingGroup = ""
                }
            }
        }
        return result.distinctBy { it.url }
    }

    suspend fun fetchFromUrl(url: String): Result<List<M3uChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true)) {
                return@withContext Result.failure(Exception("URL ph\u1EA3i b\u1EAFt \u0111\u1EA7u b\u1EB1ng http:// ho\u1EB7c https://"))
            }
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "VideoPlayerPro-IPTV/1.1")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Kh\u00F4ng th\u1EC3 t\u1EA3i danh s\u00E1ch M3U"))
                }
                val body = response.body?.string().orEmpty()
                val channels = parseText(body)
                if (channels.isEmpty()) {
                    Result.failure(Exception("Kh\u00F4ng t\u00ECm th\u1EA5y k\u00EAnh h\u1EE3p l\u1EC7 trong danh s\u00E1ch n\u00E0y"))
                } else {
                    Result.success(channels)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseFromUri(context: Context, uri: Uri): Result<List<M3uChannelItem>> = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            } ?: return@withContext Result.failure(Exception("Kh\u00F4ng th\u1EC3 \u0111\u1ECDc t\u1EC7p M3U"))

            val channels = parseText(text)
            if (channels.isEmpty()) {
                Result.failure(Exception("T\u1EC7p kh\u00F4ng ch\u1EE9a k\u00EAnh IPTV h\u1EE3p l\u1EC7"))
            } else {
                Result.success(channels)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}