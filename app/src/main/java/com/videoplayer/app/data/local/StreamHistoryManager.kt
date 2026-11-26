package com.videoplayer.app.data.local

import android.content.Context
import android.net.Uri
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
data class SavedStreamItem(
    val id: String,
    val url: String,
    val customName: String,
    val lastPlayedAtMs: Long,
    val isPinned: Boolean = false
)

@Keep
data class M3uChannelItem(
    val id: String,
    val name: String,
    val url: String,
    val group: String = ""
)

@Keep
data class StreamHistoryStoreData(
    val items: List<SavedStreamItem> = emptyList(),
    val m3uChannels: List<M3uChannelItem> = emptyList()
)

/**
 * Quan ly Lich su phat Stream mang, Ghim kenh yeu thich (Pin) va Danh sach kenh IPTV (M3U):
 * - Cac muc duoc ghim (isPinned = true) luon xep tren dau va khong bi xoa khi vuot moc 50 lich su
 * - Ho tro tra cuu nhanh ten goi nho theo URL de hien thi len thanh tieu de Player
 */
object StreamHistoryManager {
    private const val STORE_FILE_NAME = "stream_history_store.json"
    private const val MAX_UNPINNED_ITEMS = 50
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _items = MutableStateFlow<List<SavedStreamItem>>(emptyList())
    val items: StateFlow<List<SavedStreamItem>> = _items.asStateFlow()

    private val _m3uChannels = MutableStateFlow<List<M3uChannelItem>>(emptyList())
    val m3uChannels: StateFlow<List<M3uChannelItem>> = _m3uChannels.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            val file = File(context.filesDir, STORE_FILE_NAME)
            if (file.exists()) {
                try {
                    val json = file.readText(Charsets.UTF_8)
                    val loaded = gson.fromJson(json, StreamHistoryStoreData::class.java)
                    if (loaded != null) {
                        _items.value = sortHistory(loaded.items ?: emptyList())
                        _m3uChannels.value = loaded.m3uChannels ?: emptyList()
                    }
                } catch (_: Exception) {}
            }
            isInitialized = true
        }
    }

    private fun sortHistory(list: List<SavedStreamItem>): List<SavedStreamItem> {
        val pinned = list.filter { it.isPinned }.sortedByDescending { it.lastPlayedAtMs }
        val unpinned = list.filterNot { it.isPinned }
            .sortedByDescending { it.lastPlayedAtMs }
            .take(MAX_UNPINNED_ITEMS)
        return pinned + unpinned
    }

    @Synchronized
    private fun persist(context: Context, historyList: List<SavedStreamItem>, channels: List<M3uChannelItem>) {
        try {
            val file = File(context.filesDir, STORE_FILE_NAME)
            file.writeText(gson.toJson(StreamHistoryStoreData(historyList, channels)), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun deriveDefaultName(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val lastSeg = uri.lastPathSegment?.takeIf { it.isNotBlank() && it.length > 2 }
            val host = uri.host?.takeIf { it.isNotBlank() }
            when {
                lastSeg != null && host != null -> "$lastSeg ($host)"
                lastSeg != null -> lastSeg
                host != null -> host
                else -> url.take(36)
            }
        } catch (_: Exception) {
            url.take(36)
        }
    }

    fun findTitleForUrl(url: String): String? {
        val clean = url.trim()
        val fromHistory = _items.value.find { it.url.equals(clean, ignoreCase = true) }?.customName
        if (!fromHistory.isNullOrBlank()) return fromHistory
        val fromM3u = _m3uChannels.value.find { it.url.equals(clean, ignoreCase = true) }?.name
        if (!fromM3u.isNullOrBlank()) return fromM3u
        return null
    }

    fun recordStreamPlayed(context: Context, rawUrl: String, optionalName: String = ""): String {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return ""
        ensureInitialized(context)

        val current = _items.value
        val existing = current.find { it.url.equals(cleanUrl, ignoreCase = true) }
        val resolvedName = when {
            optionalName.isNotBlank() -> optionalName.trim()
            existing != null && existing.customName.isNotBlank() -> existing.customName
            else -> findTitleForUrl(cleanUrl) ?: deriveDefaultName(cleanUrl)
        }

        val updatedItem = SavedStreamItem(
            id = existing?.id ?: "stream_${System.currentTimeMillis()}",
            url = cleanUrl,
            customName = resolvedName,
            lastPlayedAtMs = System.currentTimeMillis(),
            isPinned = existing?.isPinned ?: false
        )

        val merged = mutableListOf(updatedItem).apply {
            addAll(current.filterNot { it.id == updatedItem.id || it.url.equals(cleanUrl, ignoreCase = true) })
        }
        val sorted = sortHistory(merged)
        _items.value = sorted
        ioScope.launch { persist(context.applicationContext, sorted, _m3uChannels.value) }
        return resolvedName
    }

    fun togglePin(context: Context, id: String): Boolean {
        ensureInitialized(context)
        var nowPinned = false
        val updated = _items.value.map { item ->
            if (item.id == id) {
                nowPinned = !item.isPinned
                item.copy(isPinned = nowPinned)
            } else item
        }
        val sorted = sortHistory(updated)
        _items.value = sorted
        ioScope.launch { persist(context.applicationContext, sorted, _m3uChannels.value) }
        return nowPinned
    }

    fun renameStream(context: Context, id: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        ensureInitialized(context)

        val updated = _items.value.map { item ->
            if (item.id == id) item.copy(customName = cleanName) else item
        }
        _items.value = updated
        ioScope.launch { persist(context.applicationContext, updated, _m3uChannels.value) }
    }

    fun deleteStream(context: Context, id: String) {
        ensureInitialized(context)
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        ioScope.launch { persist(context.applicationContext, updated, _m3uChannels.value) }
    }

    fun clearUnpinnedHistory(context: Context) {
        ensureInitialized(context)
        val onlyPinned = _items.value.filter { it.isPinned }
        _items.value = onlyPinned
        ioScope.launch { persist(context.applicationContext, onlyPinned, _m3uChannels.value) }
    }

    fun saveM3uChannels(context: Context, channels: List<M3uChannelItem>) {
        ensureInitialized(context)
        _m3uChannels.value = channels
        ioScope.launch { persist(context.applicationContext, _items.value, channels) }
    }

    fun clearM3uChannels(context: Context) {
        ensureInitialized(context)
        _m3uChannels.value = emptyList()
        ioScope.launch { persist(context.applicationContext, _items.value, emptyList()) }
    }
}