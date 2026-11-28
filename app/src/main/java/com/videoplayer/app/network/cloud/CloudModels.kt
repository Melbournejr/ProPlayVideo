package com.videoplayer.app.network.cloud

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

@Keep
enum class CloudSourceType(val label: String, val badge: String) {
    GOOGLE_DRIVE("Google Drive", "GDrive"),
    WEBDAV("WebDAV", "WebDAV"),
    SMB("SMB (Windows / NAS)", "SMB")
}

@Keep
data class CloudSourceConfig(
    val id: String,
    val name: String,
    val type: CloudSourceType,
    val url: String = "",            // WebDAV Base URL (vd: https://dav.example.com/dav)
    val host: String = "",           // SMB IP/Host (vd: 192.168.1.10)
    val port: Int = 445,             // SMB Port (mac dinh 445)
    val shareName: String = "",      // SMB Shared Folder Name (vd: Movies)
    val username: String = "",       // WebDAV / SMB Username
    val password: String = "",       // WebDAV / SMB Password
    val domain: String = "",         // SMB Domain (tuy chon)
    val oauthClientId: String = "",      // Google Drive OAuth2 Desktop Client ID
    val oauthClientSecret: String = "",  // Google Drive OAuth2 Desktop Client Secret
    val accessToken: String = "",        // Google Drive Access Token
    val refreshToken: String = "",       // Google Drive Refresh Token
    val tokenExpiryMs: Long = 0L
)

@Keep
data class CloudEntry(
    val id: String,
    val name: String,
    val pathOrId: String,
    val isFolder: Boolean,
    val size: Long = 0L,
    val mimeType: String = "video/mp4"
) {
    val sizeFormatted: String
        get() {
            if (isFolder || size <= 0L) return ""
            val mb = size / (1024f * 1024f)
            return if (mb >= 1024f) "%.2f GB".format(mb / 1024f) else "%.1f MB".format(mb)
        }
}

@Keep
data class CloudSourcesStoreData(
    val sources: List<CloudSourceConfig> = emptyList()
)

object CloudSourceManager {
    private const val FILE_NAME = "cloud_sources_store.json"
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _sources = MutableStateFlow<List<CloudSourceConfig>>(emptyList())
    val sources: StateFlow<List<CloudSourceConfig>> = _sources.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                try {
                    val data = gson.fromJson(file.readText(Charsets.UTF_8), CloudSourcesStoreData::class.java)
                    if (data?.sources != null) {
                        _sources.value = data.sources
                    }
                } catch (_: Exception) {}
            }
            isInitialized = true
        }
    }

    @Synchronized
    private fun persist(context: Context, list: List<CloudSourceConfig>) {
        try {
            File(context.filesDir, FILE_NAME).writeText(gson.toJson(CloudSourcesStoreData(list)), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun upsertSource(context: Context, config: CloudSourceConfig) {
        ensureInitialized(context)
        val current = _sources.value
        val updated = if (current.any { it.id == config.id }) {
            current.map { if (it.id == config.id) config else it }
        } else {
            listOf(config) + current
        }
        _sources.value = updated
        ioScope.launch { persist(context.applicationContext, updated) }
    }

    fun deleteSource(context: Context, id: String) {
        ensureInitialized(context)
        val updated = _sources.value.filterNot { it.id == id }
        _sources.value = updated
        ioScope.launch { persist(context.applicationContext, updated) }
    }

    fun getSourceById(id: String): CloudSourceConfig? = _sources.value.find { it.id == id }

    private val videoExtensions = setOf("mp4", "mkv", "webm", "mov", "avi", "ts", "m4v", "flv", "3gp", "wmv", "mpg", "mpeg", "m2ts")

    fun isVideoFileName(name: String, mimeType: String = ""): Boolean {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in videoExtensions
    }

    fun guessMimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts", "m2ts" -> "video/mp2t"
        "flv" -> "video/x-flv"
        else -> "video/mp4"
    }
}