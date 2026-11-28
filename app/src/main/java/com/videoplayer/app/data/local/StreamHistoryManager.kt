package com.videoplayer.app.data.local

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
    val isPinned: Boolean = false,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L
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
 * Quan ly Lich su phat Stream mang nang cao:
 * - Gioi han mac dinh 1000 muc (0 = Khong gioi han), cac kenh da ghim luon giu nguyen
 * - Luu tien do xem do (lastPositionMs / durationMs) cho tung luong Stream VOD
 * - Ho tro Sao luu / Khoi phuc toan bo danh sach qua tep JSON
 */
object StreamHistoryManager {
    private const val STORE_FILE_NAME = "stream_history_store.json"
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _items = MutableStateFlow<List<SavedStreamItem>>(emptyList())
    val items: StateFlow<List<SavedStreamItem>> = _items.asStateFlow()

    private val _m3uChannels = MutableStateFlow<List<M3uChannelItem>>(emptyList())
    val m3uChannels: StateFlow<List<M3uChannelItem>> = _m3uChannels.asStateFlow()

    @Volatile
    private var maxUnpinnedLimit: Int = 1000 // 0 = Khong gioi han

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

    fun setMaxUnpinnedLimit(context: Context, limit: Int) {
        maxUnpinnedLimit = limit.coerceAtLeast(0)
        if (isInitialized) {
            val sorted = sortHistory(_items.value)
            if (sorted.size != _items.value.size) {
                _items.value = sorted
                ioScope.launch { persist(context.applicationContext, sorted, _m3uChannels.value) }
            }
        }
    }

    private fun sortHistory(list: List<SavedStreamItem>): List<SavedStreamItem> {
        val pinned = list.filter { it.isPinned }.sortedByDescending { it.lastPlayedAtMs }
        val unpinnedSorted = list.filterNot { it.isPinned }.sortedByDescending { it.lastPlayedAtMs }
        val unpinned = if (maxUnpinnedLimit > 0) unpinnedSorted.take(maxUnpinnedLimit) else unpinnedSorted
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

    fun getSavedPositionForUrl(url: String): Long {
        val clean = url.trim()
        val entry = _items.value.find { it.url.equals(clean, ignoreCase = true) } ?: return 0L
        if (entry.durationMs <= 10_000L) return 0L
        return if (entry.lastPositionMs in 2000L..(entry.durationMs - 3000L)) entry.lastPositionMs else 0L
    }

    fun recordStreamPlayed(context: Context, rawUrl: String, optionalName: String = ""): String {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("http://127.0.0.1")) return optionalName
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
            isPinned = existing?.isPinned ?: false,
            lastPositionMs = existing?.lastPositionMs ?: 0L,
            durationMs = existing?.durationMs ?: 0L
        )

        val merged = mutableListOf(updatedItem).apply {
            addAll(current.filterNot { it.id == updatedItem.id || it.url.equals(cleanUrl, ignoreCase = true) })
        }
        val sorted = sortHistory(merged)
        _items.value = sorted
        ioScope.launch { persist(context.applicationContext, sorted, _m3uChannels.value) }
        return resolvedName
    }

    fun updateStreamProgress(context: Context, rawUrl: String, positionMs: Long, durationMs: Long) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("http://127.0.0.1") || durationMs <= 10_000L) return
        ensureInitialized(context)

        val current = _items.value
        val existing = current.find { it.url.equals(cleanUrl, ignoreCase = true) }
        val safePos = positionMs.coerceIn(0L, durationMs)
        val finalPos = if (safePos >= durationMs - 3000L) 0L else safePos

        val updated = if (existing != null) {
            current.map { item ->
                if (item.id == existing.id) {
                    item.copy(
                        lastPositionMs = finalPos,
                        durationMs = durationMs,
                        lastPlayedAtMs = System.currentTimeMillis()
                    )
                } else item
            }
        } else {
            val newItem = SavedStreamItem(
                id = "stream_${System.currentTimeMillis()}",
                url = cleanUrl,
                customName = findTitleForUrl(cleanUrl) ?: deriveDefaultName(cleanUrl),
                lastPlayedAtMs = System.currentTimeMillis(),
                isPinned = false,
                lastPositionMs = finalPos,
                durationMs = durationMs
            )
            listOf(newItem) + current
        }

        val sorted = sortHistory(updated)
        _items.value = sorted
        ioScope.launch { persist(context.applicationContext, sorted, _m3uChannels.value) }
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

    fun exportToJson(context: Context): String {
        ensureInitialized(context)
        return prettyGson.toJson(StreamHistoryStoreData(_items.value, _m3uChannels.value))
    }

    fun importFromJson(context: Context, jsonText: String): Result<Pair<Int, Int>> {
        return try {
            ensureInitialized(context)
            val parsed = gson.fromJson(jsonText, StreamHistoryStoreData::class.java)
                ?: return Result.failure(Exception("T\u1EC7p sao l\u01B0u kh\u00F4ng h\u1EE3p l\u1EC7"))

            val importedHistory = parsed.items ?: emptyList()
            val importedM3u = parsed.m3uChannels ?: emptyList()

            // Hop nhat voi danh sach hien tai (uu tien giu lai muc da co, bo sung muc moi)
            val existingByUrl = _items.value.associateBy { it.url.lowercase() }.toMutableMap()
            var addedHistory = 0
            importedHistory.forEach { item ->
                val key = item.url.trim().lowercase()
                if (key.isNotBlank()) {
                    val prev = existingByUrl[key]
                    if (prev == null) {
                        existingByUrl[key] = item
                        addedHistory++
                    } else if (!prev.isPinned && item.isPinned) {
                        existingByUrl[key] = prev.copy(isPinned = true, customName = item.customName.ifBlank { prev.customName })
                    }
                }
            }
            val mergedHistory = sortHistory(existingByUrl.values.toList())

            val existingM3uByUrl = _m3uChannels.value.associateBy { it.url.lowercase() }.toMutableMap()
            var addedM3u = 0
            importedM3u.forEach { ch ->
                val key = ch.url.trim().lowercase()
                if (key.isNotBlank() && !existingM3uByUrl.containsKey(key)) {
                    existingM3uByUrl[key] = ch
                    addedM3u++
                }
            }
            val mergedM3u = existingM3uByUrl.values.toList()

            _items.value = mergedHistory
            _m3uChannels.value = mergedM3u
            ioScope.launch { persist(context.applicationContext, mergedHistory, mergedM3u) }
            Result.success(addedHistory to addedM3u)
        } catch (e: Exception) {
            Result.failure(Exception("Kh\u00F4ng \u0111\u1ECDc \u0111\u01B0\u1EE3c JSON: ${e.message}"))
        }
    }
}