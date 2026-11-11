package com.videoplayer.app.data.local

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

@Keep
data class VideoBookmark(
    val id: String,
    val videoId: String,
    val timestampMs: Long,
    val note: String,
    val createdAtMs: Long
)

@Keep
data class WatchHistoryEntry(
    val videoId: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastWatchedAtMs: Long
)

@Keep
data class WatchExperienceStoreData(
    val watchLaterIds: List<String> = emptyList(),
    val history: List<WatchHistoryEntry> = emptyList(),
    val bookmarksByVideoId: Map<String, List<VideoBookmark>> = emptyMap()
)

/**
 * Quản lý tập trung dữ liệu Trải nghiệm xem (Phần C):
 * - Tiếp tục xem (Continue Watching) & Lịch sử xem (Playback History - tối đa 100 video)
 * - Danh sách Xem sau (Watch Later)
 * - Bookmark có ghi chú văn bản theo từng video
 * Tự động đồng bộ StateFlow thời gian thực và loại trừ các video thuộc Kho Ẩn khỏi lịch sử công khai.
 */
object WatchExperienceManager {
    private const val STORE_FILE_NAME = "watch_experience_store.json"
    private const val MAX_HISTORY_ITEMS = 100
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private val _state = MutableStateFlow(WatchExperienceStoreData())
    val state: StateFlow<WatchExperienceStoreData> = _state.asStateFlow()

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
                    val loaded = gson.fromJson(json, WatchExperienceStoreData::class.java)
                    if (loaded != null) {
                        _state.value = loaded
                    }
                } catch (_: Exception) {}
            }
            isInitialized = true
        }
    }

    @Synchronized
    private fun persistToDisk(context: Context, data: WatchExperienceStoreData) {
        try {
            val file = File(context.filesDir, STORE_FILE_NAME)
            file.writeText(gson.toJson(data), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    /**
     * Ghi nhận lịch sử xem & vị trí phát gần nhất (Chỉ lưu video thường, không ghi lịch sử video Kho Ẩn)
     */
    fun recordPlaybackProgress(context: Context, video: VideoItem, positionMs: Long, durationMs: Long) {
        if (video.id.startsWith("vault_") || video.path.startsWith("vault://")) return
        ensureInitialized(context)

        val current = _state.value
        val now = System.currentTimeMillis()
        val effectiveDur = durationMs.coerceAtLeast(video.duration).coerceAtLeast(1L)
        val entry = WatchHistoryEntry(
            videoId = video.id,
            lastPositionMs = positionMs.coerceAtLeast(0L),
            durationMs = effectiveDur,
            lastWatchedAtMs = now
        )

        val updatedHistory = mutableListOf(entry).apply {
            addAll(current.history.filterNot { it.videoId == video.id })
        }.take(MAX_HISTORY_ITEMS)

        // Nếu người dùng đã xem hết (> 96% thời lượng), tự động gỡ khỏi danh sách "Xem sau"
        val isCompleted = effectiveDur > 10_000L && positionMs >= (effectiveDur * 0.96f).toLong()
        val updatedWatchLater = if (isCompleted) {
            current.watchLaterIds.filterNot { it == video.id }
        } else {
            current.watchLaterIds
        }

        val nextData = current.copy(
            history = updatedHistory,
            watchLaterIds = updatedWatchLater
        )
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
    }

    /**
     * Thêm / Gỡ video khỏi danh sách "Xem sau" (Watch Later)
     */
    fun toggleWatchLater(context: Context, videoId: String): Boolean {
        ensureInitialized(context)
        val current = _state.value
        val exists = videoId in current.watchLaterIds
        val updated = if (exists) {
            current.watchLaterIds.filterNot { it == videoId }
        } else {
            listOf(videoId) + current.watchLaterIds.filterNot { it == videoId }
        }
        val nextData = current.copy(watchLaterIds = updated)
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
        return !exists
    }

    fun isInWatchLater(videoId: String): Boolean {
        return videoId in _state.value.watchLaterIds
    }

    /**
     * Xóa 1 mục hoặc xóa toàn bộ Lịch sử xem
     */
    fun removeFromHistory(context: Context, videoId: String) {
        ensureInitialized(context)
        val current = _state.value
        val nextData = current.copy(history = current.history.filterNot { it.videoId == videoId })
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
    }

    fun clearAllHistory(context: Context) {
        ensureInitialized(context)
        val current = _state.value
        val nextData = current.copy(history = emptyList())
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
    }

    /**
     * Quản lý Bookmark có ghi chú theo từng video
     */
    fun getBookmarksForVideo(context: Context, videoId: String): List<VideoBookmark> {
        ensureInitialized(context)
        return _state.value.bookmarksByVideoId[videoId]?.sortedBy { it.timestampMs } ?: emptyList()
    }

    fun addBookmark(context: Context, videoId: String, timestampMs: Long, note: String): List<VideoBookmark> {
        ensureInitialized(context)
        val current = _state.value
        val cleanNote = note.trim().ifEmpty { "Mốc đánh dấu" }
        val newBm = VideoBookmark(
            id = "bm_${System.currentTimeMillis()}",
            videoId = videoId,
            timestampMs = timestampMs.coerceAtLeast(0L),
            note = cleanNote,
            createdAtMs = System.currentTimeMillis()
        )
        val existingList = current.bookmarksByVideoId[videoId] ?: emptyList()
        val updatedList = (existingList + newBm).sortedBy { it.timestampMs }
        val updatedMap = current.bookmarksByVideoId.toMutableMap().apply {
            put(videoId, updatedList)
        }
        val nextData = current.copy(bookmarksByVideoId = updatedMap)
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
        return updatedList
    }

    fun updateBookmarkNote(context: Context, videoId: String, bookmarkId: String, newNote: String): List<VideoBookmark> {
        ensureInitialized(context)
        val current = _state.value
        val existingList = current.bookmarksByVideoId[videoId] ?: return emptyList()
        val updatedList = existingList.map { bm ->
            if (bm.id == bookmarkId) bm.copy(note = newNote.trim().ifEmpty { bm.note }) else bm
        }
        val updatedMap = current.bookmarksByVideoId.toMutableMap().apply {
            put(videoId, updatedList)
        }
        val nextData = current.copy(bookmarksByVideoId = updatedMap)
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
        return updatedList
    }

    fun deleteBookmark(context: Context, videoId: String, bookmarkId: String): List<VideoBookmark> {
        ensureInitialized(context)
        val current = _state.value
        val existingList = current.bookmarksByVideoId[videoId] ?: return emptyList()
        val updatedList = existingList.filterNot { it.id == bookmarkId }
        val updatedMap = current.bookmarksByVideoId.toMutableMap().apply {
            if (updatedList.isEmpty()) remove(videoId) else put(videoId, updatedList)
        }
        val nextData = current.copy(bookmarksByVideoId = updatedMap)
        _state.value = nextData
        ioScope.launch { persistToDisk(context.applicationContext, nextData) }
        return updatedList
    }
}