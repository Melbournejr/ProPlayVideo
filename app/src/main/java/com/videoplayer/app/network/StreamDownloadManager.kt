package com.videoplayer.app.network

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class StreamDownloadTask(
    val url: String,
    val title: String,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val speedText: String = "",
    val statusMessage: String = "\u0110ang k\u1EBFt n\u1ED1i...",
    val isRunning: Boolean = true,
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val savedFileName: String? = null
)

@Keep
data class DownloadedVideoRecord(
    val sourceKey: String,
    val url: String,
    val title: String,
    val savedFileName: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long
)

@Keep
data class DownloadedVideosStoreData(
    val records: List<DownloadedVideoRecord> = emptyList()
)

data class DuplicateDownloadMatch(
    val fileName: String,
    val sizeBytes: Long,
    val downloadedAtMs: Long
) {
    val sizeFormatted: String
        get() {
            if (sizeBytes <= 0L) return ""
            val mb = sizeBytes / (1024f * 1024f)
            return if (mb >= 1024f) "%.2f GB".format(mb / 1024f) else "%.1f MB".format(mb)
        }

    val timeFormatted: String
        get() {
            if (downloadedAtMs <= 0L) return ""
            return try {
                SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault()).format(Date(downloadedAtMs))
            } catch (_: Exception) { "" }
        }
}

private data class ExistingMediaFile(
    val name: String,
    val size: Long,
    val modifiedMs: Long
)

private data class HlsSegmentInfo(
    val index: Int,
    val sequence: Long,
    val url: String,
    val keyUri: String?,
    val ivHex: String?
)

/**
 * Trinh tai video Stream (Huong A) + Canh bao tai trung 2 lop (Registry + MediaStore):
 * - Luu lich su cac video da tai thanh cong vao downloaded_videos_store.json
 * - Truoc khi tai, kiem tra ca sourceKey lan tep vat ly trong Movies/VideoPlayerPro
 * - Neu nguoi dung da xoa tep cu khoi may -> tu don ban ghi cu va cho tai thang
 */
object StreamDownloadManager {
    private const val STORE_FILE_NAME = "downloaded_videos_store.json"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<StreamDownloadTask>>(emptyList())
    val tasks: StateFlow<List<StreamDownloadTask>> = _tasks.asStateFlow()

    private val _downloadedRecords = MutableStateFlow<List<DownloadedVideoRecord>>(emptyList())
    val downloadedRecords: StateFlow<List<DownloadedVideoRecord>> = _downloadedRecords.asStateFlow()

    @Volatile
    private var isStoreInitialized = false

    fun ensureInitialized(context: Context) {
        if (isStoreInitialized) return
        synchronized(this) {
            if (isStoreInitialized) return
            val file = File(context.applicationContext.filesDir, STORE_FILE_NAME)
            if (file.exists()) {
                try {
                    val data = gson.fromJson(file.readText(Charsets.UTF_8), DownloadedVideosStoreData::class.java)
                    if (data?.records != null) {
                        _downloadedRecords.value = data.records
                    }
                } catch (_: Exception) {}
            }
            isStoreInitialized = true
        }
    }

    @Synchronized
    private fun persistRecords(context: Context, list: List<DownloadedVideoRecord>) {
        try {
            File(context.applicationContext.filesDir, STORE_FILE_NAME)
                .writeText(gson.toJson(DownloadedVideosStoreData(list)), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun recordDownloadSuccess(
        context: Context,
        sourceKey: String,
        url: String,
        title: String,
        savedFileName: String,
        sizeBytes: Long
    ) {
        ensureInitialized(context)
        val cleanKey = sourceKey.trim().ifBlank { url.trim() }
        val newRec = DownloadedVideoRecord(
            sourceKey = cleanKey,
            url = url.trim(),
            title = title.trim(),
            savedFileName = savedFileName,
            sizeBytes = sizeBytes,
            downloadedAtMs = System.currentTimeMillis()
        )
        val updated = listOf(newRec) + _downloadedRecords.value.filterNot {
            it.sourceKey.equals(cleanKey, ignoreCase = true) ||
                it.savedFileName.equals(savedFileName, ignoreCase = true)
        }
        val capped = updated.take(1000)
        _downloadedRecords.value = capped
        persistRecords(context, capped)
    }

    fun isRecordedDownloaded(sourceKey: String, titleHint: String = ""): Boolean {
        val cleanKey = sourceKey.trim()
        val sanitizedTitle = if (titleHint.isNotBlank()) sanitizeFileName(titleHint) else ""
        return _downloadedRecords.value.any { rec ->
            (cleanKey.isNotBlank() && (rec.sourceKey.equals(cleanKey, ignoreCase = true) || rec.url.equals(cleanKey, ignoreCase = true))) ||
                (sanitizedTitle.isNotBlank() && rec.savedFileName.substringBeforeLast('.').equals(sanitizedTitle, ignoreCase = true))
        }
    }

    /**
     * Kiem tra 2 lop (chay tren Dispatchers.IO):
     * 1. Doi chieu sourceKey trong so tai ve + xac minh tep vat ly con ton tai trong Movies/VideoPlayerPro
     * 2. Quet ten tep thuc te trong Movies/VideoPlayerPro de phat hien ca tep da tai tu phien ban cu
     */
    suspend fun checkAlreadyDownloaded(
        context: Context,
        sourceKey: String,
        suggestedTitle: String
    ): DuplicateDownloadMatch? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        ensureInitialized(appCtx)

        val cleanKey = sourceKey.trim()
        val sanitizedBase = if (suggestedTitle.isNotBlank()) sanitizeFileName(suggestedTitle) else ""
        val existingFiles = queryExistingMoviesFiles(appCtx)

        // Lop 1: Kiem tra ban ghi theo sourceKey/URL
        val rec = _downloadedRecords.value.find {
            cleanKey.isNotBlank() && (it.sourceKey.equals(cleanKey, ignoreCase = true) || it.url.equals(cleanKey, ignoreCase = true))
        }
        if (rec != null) {
            val foundOnDisk = existingFiles.find {
                it.name.equals(rec.savedFileName, ignoreCase = true)
            }
            if (foundOnDisk != null) {
                return@withContext DuplicateDownloadMatch(
                    fileName = foundOnDisk.name,
                    sizeBytes = foundOnDisk.size.coerceAtLeast(rec.sizeBytes),
                    downloadedAtMs = rec.downloadedAtMs
                )
            } else {
                // Tep cu da bi nguoi dung xoa khoi may -> don ban ghi cu de khong nhac nham
                val cleaned = _downloadedRecords.value.filterNot { it == rec }
                _downloadedRecords.value = cleaned
                persistRecords(appCtx, cleaned)
            }
        }

        // Lop 2: Kiem tra truc tiep ten tep trong Movies/VideoPlayerPro
        if (sanitizedBase.isNotBlank()) {
            val byName = existingFiles.find {
                it.name.substringBeforeLast('.').equals(sanitizedBase, ignoreCase = true)
            }
            if (byName != null) {
                return@withContext DuplicateDownloadMatch(
                    fileName = byName.name,
                    sizeBytes = byName.size,
                    downloadedAtMs = byName.modifiedMs
                )
            }
        }

        null
    }

    private fun queryExistingMoviesFiles(context: Context): List<ExistingMediaFile> {
        val result = mutableListOf<ExistingMediaFile>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DATE_MODIFIED
                )
                val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                val args = arrayOf("%VideoPlayerPro%")
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val modSec = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                        if (name.isNotBlank() && size > 0L) {
                            result.add(ExistingMediaFile(name, size, modSec * 1000L))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback quet truc tiep thu muc Movies/VideoPlayerPro (Android < 10 hoac khi da co All Files Access)
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoPlayerPro")
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.length() > 0L && result.none { it.name.equals(f.name, ignoreCase = true) }) {
                        result.add(ExistingMediaFile(f.name, f.length(), f.lastModified()))
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }

    fun isDownloading(url: String): Boolean {
        val clean = url.trim()
        return activeJobs[clean]?.isActive == true
    }

    fun startDownload(
        context: Context,
        rawUrl: String,
        suggestedTitle: String,
        sourceKey: String = rawUrl,
        isRedownload: Boolean = false
    ) {
        val cleanUrl = rawUrl.trim()
        if (cleanUrl.isBlank()) return
        if (isDownloading(cleanUrl)) return

        val appCtx = context.applicationContext
        ensureInitialized(appCtx)

        val baseTitle = suggestedTitle.trim().ifBlank { "Stream_${System.currentTimeMillis() / 1000}" }
        val effectiveTitle = if (isRedownload) {
            val stamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
            "${baseTitle}_$stamp"
        } else baseTitle

        upsertTask(
            StreamDownloadTask(
                url = cleanUrl,
                title = baseTitle,
                progressPercent = 0,
                statusMessage = "\u0110ang ph\u00E2n t\u00EDch lu\u1ED3ng...",
                isRunning = true
            )
        )

        val job = scope.launch {
            try {
                if (cleanUrl.startsWith("rtmp://", true) || cleanUrl.startsWith("rtsp://", true)) {
                    throw Exception("Kh\u00F4ng h\u1ED7 tr\u1EE3 t\u1EA3i giao th\u1EE9c RTMP/RTSP tr\u1EF1c ti\u1EBFp")
                }
                val parsedHttpUrl = cleanUrl.toHttpUrlOrNull()
                    ?: throw Exception("URL kh\u00F4ng h\u1EE3p l\u1EC7")

                val lowerPath = parsedHttpUrl.encodedPath.lowercase()
                if (lowerPath.endsWith(".m3u8") || lowerPath.contains(".m3u8")) {
                    downloadHlsStream(appCtx, cleanUrl, effectiveTitle, sourceKey)
                } else {
                    downloadDirectOrDetectHls(appCtx, cleanUrl, effectiveTitle, sourceKey)
                }
            } catch (ce: CancellationException) {
                removeTask(cleanUrl)
                throw ce
            } catch (e: Exception) {
                updateTask(cleanUrl) {
                    it.copy(
                        isRunning = false,
                        isFailed = true,
                        statusMessage = e.message ?: "T\u1EA3i th\u1EA5t b\u1EA1i"
                    )
                }
            } finally {
                activeJobs.remove(cleanUrl)
            }
        }
        activeJobs[cleanUrl] = job
    }

    fun cancelDownload(url: String) {
        val clean = url.trim()
        activeJobs[clean]?.cancel()
        activeJobs.remove(clean)
        removeTask(clean)
    }

    fun dismissTask(url: String) {
        val clean = url.trim()
        if (!isDownloading(clean)) {
            removeTask(clean)
        }
    }

    private fun buildRequest(url: String): Request {
        val httpUrl = url.toHttpUrlOrNull()
        val referer = if (httpUrl != null) "${httpUrl.scheme}://${httpUrl.host}/" else ""
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (referer.isNotBlank()) header("Referer", referer) }
            .build()
    }

    fun sanitizeFileName(raw: String): String {
        val stripped = raw.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_").trim().trim('.')
        return stripped.take(64).ifBlank { "Video_${System.currentTimeMillis() / 1000}" }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> "%.1f MB/s".format(bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024f * 1024f)
        return if (mb >= 1024f) "%.2f GB".format(mb / 1024f) else "%.1f MB".format(mb)
    }

    private suspend fun downloadDirectOrDetectHls(context: Context, url: String, title: String, sourceKey: String) {
        client.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("M\u00E1y ch\u1EE7 tr\u1EA3 l\u1ED7i HTTP ${response.code}")
            }
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val body = response.body ?: throw Exception("Kh\u00F4ng c\u00F3 d\u1EEF li\u1EC7u t\u1EEB m\u00E1y ch\u1EE7")

            if (contentType.contains("mpegurl") || contentType.contains("vnd.apple.mpegurl")) {
                val text = body.string()
                processHlsPlaylistText(context, url, text, title, sourceKey)
                return
            }

            val totalBytes = body.contentLength()
            val ext = detectExtension(url, contentType)
            val mime = detectMimeType(ext)
            val finalName = "${sanitizeFileName(title)}.$ext"

            val outputTarget = createMoviesOutputTarget(context, finalName, mime)
            var downloaded = 0L
            var lastTickTime = System.currentTimeMillis()
            var lastTickBytes = 0L
            var completedOk = false

            try {
                outputTarget.outputStream.use { rawOut ->
                    BufferedOutputStream(rawOut, 64 * 1024).use { out ->
                        val input = body.byteStream()
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastTickTime >= 350L) {
                                val dt = (now - lastTickTime).coerceAtLeast(1L)
                                val speed = ((downloaded - lastTickBytes) * 1000L) / dt
                                lastTickTime = now
                                lastTickBytes = downloaded

                                val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99) else -1
                                val sizeStr = if (totalBytes > 0L) "${formatSize(downloaded)} / ${formatSize(totalBytes)}" else formatSize(downloaded)
                                updateTask(url) {
                                    it.copy(
                                        progressPercent = pct,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        speedText = formatSpeed(speed),
                                        statusMessage = "T\u1EA3i tr\u1EF1c ti\u1EBFp: $sizeStr"
                                    )
                                }
                            }
                        }
                        out.flush()
                    }
                }
                val actualSavedName = finalizeMoviesOutputTarget(context, outputTarget, mime)
                completedOk = true
                recordDownloadSuccess(context, sourceKey, url, title, actualSavedName, downloaded)
                updateTask(url) {
                    it.copy(
                        progressPercent = 100,
                        downloadedBytes = downloaded,
                        totalBytes = downloaded,
                        speedText = "",
                        isRunning = false,
                        isCompleted = true,
                        savedFileName = actualSavedName,
                        statusMessage = "\u0110\u00E3 l\u01B0u: $actualSavedName (${formatSize(downloaded)})"
                    )
                }
            } finally {
                if (!completedOk) {
                    abortMoviesOutputTarget(context, outputTarget)
                }
            }
        }
    }

    private suspend fun downloadHlsStream(context: Context, url: String, title: String, sourceKey: String) {
        val text = fetchText(url)
        processHlsPlaylistText(context, url, text, title, sourceKey)
    }

    private suspend fun processHlsPlaylistText(context: Context, initialUrl: String, initialText: String, title: String, sourceKey: String) {
        var mediaUrl = initialUrl
        var mediaText = initialText

        if (mediaText.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            updateTask(initialUrl) { it.copy(statusMessage = "\u0110ang ch\u1ECDn ch\u1EA5t l\u01B0\u1EE3ng cao nh\u1EA5t...") }
            val bestVariantUrl = pickBestMasterVariant(mediaUrl, mediaText)
                ?: throw Exception("Kh\u00F4ng t\u00ECm th\u1EA5y lu\u1ED3ng video trong Master Playlist")
            mediaUrl = bestVariantUrl
            mediaText = fetchText(mediaUrl)
        }

        val baseUrl = mediaUrl.toHttpUrlOrNull() ?: throw Exception("URL playlist kh\u00F4ng h\u1EE3p l\u1EC7")
        val lines = mediaText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val hasEndList = lines.any { it.startsWith("#EXT-X-ENDLIST", ignoreCase = true) }

        var mediaSequence = 0L
        var currentKeyUri: String? = null
        var currentIvHex: String? = null
        var initMapUrl: String? = null
        val segments = mutableListOf<HlsSegmentInfo>()

        val uriRegex = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
        val methodRegex = Regex("""METHOD=([A-Z0-9\-]+)""", RegexOption.IGNORE_CASE)
        val ivRegex = Regex("""IV=0x([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:", ignoreCase = true) -> {
                    mediaSequence = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                    val mapUriStr = uriRegex.find(line)?.groupValues?.getOrNull(1)
                    if (!mapUriStr.isNullOrBlank()) {
                        initMapUrl = baseUrl.resolve(mapUriStr)?.toString()
                    }
                }
                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val method = methodRegex.find(line)?.groupValues?.getOrNull(1)?.uppercase().orEmpty()
                    when (method) {
                        "NONE", "" -> {
                            currentKeyUri = null
                            currentIvHex = null
                        }
                        "AES-128" -> {
                            val keyUriStr = uriRegex.find(line)?.groupValues?.getOrNull(1)
                            currentKeyUri = keyUriStr?.let { baseUrl.resolve(it)?.toString() }
                            currentIvHex = ivRegex.find(line)?.groupValues?.getOrNull(1)
                        }
                        else -> {
                            throw Exception("Lu\u1ED3ng c\u00F3 m\u00E3 h\u00F3a b\u1EA3o v\u1EC7 b\u1EA3n quy\u1EC1n ($method), kh\u00F4ng th\u1EC3 t\u1EA3i")
                        }
                    }
                }
                !line.startsWith("#") -> {
                    val resolved = baseUrl.resolve(line)?.toString() ?: continue
                    val idx = segments.size
                    segments.add(
                        HlsSegmentInfo(
                            index = idx,
                            sequence = mediaSequence + idx,
                            url = resolved,
                            keyUri = currentKeyUri,
                            ivHex = currentIvHex
                        )
                    )
                }
            }
        }

        if (segments.isEmpty()) {
            throw Exception("Kh\u00F4ng t\u00ECm th\u1EA5y ph\u00E2n \u0111o\u1EA1n video trong t\u1EC7p .m3u8")
        }
        if (!hasEndList && segments.size < 12) {
            throw Exception("\u0110\u00E2y l\u00E0 lu\u1ED3ng tr\u1EF1c ti\u1EBFp (Live TV), kh\u00F4ng c\u00F3 \u0111i\u1EC3m k\u1EBFt th\u00FAc \u0111\u1EC3 t\u1EA3i tr\u1ECDn g\u00F3i")
        }

        val tempDir = File(context.cacheDir, "hls_dl_${System.currentTimeMillis()}_${initialUrl.hashCode()}").apply { mkdirs() }
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val downloadedBytes = AtomicLong(0L)
        val completedSegs = AtomicInteger(0)
        val totalSegs = segments.size
        val startMs = System.currentTimeMillis()
        var outputTarget: MoviesOutputTarget? = null
        var finishedOk = false

        try {
            var initFile: File? = null
            if (!initMapUrl.isNullOrBlank()) {
                val f = File(tempDir, "init.bin")
                val bytes = fetchBytes(initMapUrl)
                f.writeBytes(bytes)
                downloadedBytes.addAndGet(bytes.size.toLong())
                initFile = f
            }

            segments.mapNotNull { it.keyUri }.distinct().forEach { kUrl ->
                currentCoroutineContext().ensureActive()
                val keyBytes = fetchBytes(kUrl)
                if (keyBytes.size == 16) keyCache[kUrl] = keyBytes
            }

            val semaphore = Semaphore(5)
            coroutineScope {
                val jobs = segments.map { seg ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            currentCoroutineContext().ensureActive()
                            var rawBytes = fetchBytesWithRetry(seg.url)
                            val keyBytes = seg.keyUri?.let { keyCache[it] }
                            if (keyBytes != null) {
                                rawBytes = decryptAes128Cbc(rawBytes, keyBytes, seg.ivHex, seg.sequence)
                            }
                            val segFile = File(tempDir, "seg_${seg.index}.bin")
                            segFile.writeBytes(rawBytes)

                            val totalDl = downloadedBytes.addAndGet(rawBytes.size.toLong())
                            val done = completedSegs.incrementAndGet()
                            val pct = ((done * 95) / totalSegs).coerceIn(1, 95)
                            val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).coerceAtLeast(1L)
                            updateTask(initialUrl) {
                                it.copy(
                                    progressPercent = pct,
                                    downloadedBytes = totalDl,
                                    speedText = formatSpeed(totalDl / elapsedSec),
                                    statusMessage = "\u0110ang t\u1EA3i HLS: $done/$totalSegs \u0111o\u1EA1n (${formatSize(totalDl)})"
                                )
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            updateTask(initialUrl) {
                it.copy(
                    progressPercent = 96,
                    speedText = "",
                    statusMessage = "\u0110ang gh\u00E9p $totalSegs ph\u00E2n \u0111o\u1EA1n th\u00E0nh t\u1EC7p ho\u00E0n ch\u1EC9nh..."
                )
            }

            val isFmp4 = (initFile != null)
            val ext = if (isFmp4) "mp4" else "ts"
            val mime = if (isFmp4) "video/mp4" else "video/mp2t"
            val fileName = "${sanitizeFileName(title)}.$ext"
            outputTarget = createMoviesOutputTarget(context, fileName, mime)

            var totalMerged = 0L
            outputTarget.outputStream.use { rawOut ->
                BufferedOutputStream(rawOut, 128 * 1024).use { out ->
                    val copyBuf = ByteArray(128 * 1024)
                    if (initFile != null && initFile.exists()) {
                        totalMerged += appendAndDeleteFile(initFile, out, copyBuf)
                    }
                    for (i in 0 until totalSegs) {
                        currentCoroutineContext().ensureActive()
                        val segFile = File(tempDir, "seg_$i.bin")
                        if (segFile.exists()) {
                            totalMerged += appendAndDeleteFile(segFile, out, copyBuf)
                        }
                    }
                    out.flush()
                }
            }

            val actualSavedName = finalizeMoviesOutputTarget(context, outputTarget, mime)
            finishedOk = true
            recordDownloadSuccess(context, sourceKey, initialUrl, title, actualSavedName, totalMerged)
            updateTask(initialUrl) {
                it.copy(
                    progressPercent = 100,
                    downloadedBytes = totalMerged,
                    totalBytes = totalMerged,
                    speedText = "",
                    isRunning = false,
                    isCompleted = true,
                    savedFileName = actualSavedName,
                    statusMessage = "\u0110\u00E3 gh\u00E9p & l\u01B0u: $actualSavedName (${formatSize(totalMerged)})"
                )
            }
        } finally {
            if (!finishedOk && outputTarget != null) {
                abortMoviesOutputTarget(context, outputTarget)
            }
            try { tempDir.deleteRecursively() } catch (_: Exception) {}
        }
    }

    private fun pickBestMasterVariant(masterUrl: String, masterText: String): String? {
        val base = masterUrl.toHttpUrlOrNull() ?: return null
        val lines = masterText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val bwRegex = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
        var bestBw = -1L
        var bestUrl: String? = null
        var pendingBw = -1L

        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pendingBw = bwRegex.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            } else if (!line.startsWith("#") && pendingBw >= 0L) {
                val resolved = base.resolve(line)?.toString()
                if (resolved != null && pendingBw >= bestBw) {
                    bestBw = pendingBw
                    bestUrl = resolved
                }
                pendingBw = -1L
            }
        }
        return bestUrl
    }

    private fun decryptAes128Cbc(data: ByteArray, key: ByteArray, ivHex: String?, sequence: Long): ByteArray {
        val ivBytes = if (!ivHex.isNullOrBlank()) {
            val padded = ivHex.padStart(32, '0').takeLast(32)
            ByteArray(16) { i -> padded.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        } else {
            ByteBuffer.allocate(16).apply { putLong(8, sequence) }.array()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivBytes))
        return cipher.doFinal(data)
    }

    private fun fetchText(url: String): String {
        client.newCall(buildRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} khi \u0111\u1ECDc playlist")
            return resp.body?.string().orEmpty()
        }
    }

    private fun fetchBytes(url: String): ByteArray {
        client.newCall(buildRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} khi t\u1EA3i ph\u00E2n \u0111o\u1EA1n")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun fetchBytesWithRetry(url: String): ByteArray {
        var lastErr: Exception? = null
        for (attempt in 1..3) {
            currentCoroutineContext().ensureActive()
            try {
                return fetchBytes(url)
            } catch (e: Exception) {
                lastErr = e
                if (attempt < 3) delay(400L * attempt)
            }
        }
        throw lastErr ?: Exception("L\u1ED7i t\u1EA3i ph\u00E2n \u0111o\u1EA1n HLS")
    }

    private fun appendAndDeleteFile(src: File, out: OutputStream, buf: ByteArray): Long {
        var copied = 0L
        FileInputStream(src).use { input ->
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                out.write(buf, 0, r)
                copied += r
            }
        }
        src.delete()
        return copied
    }

    private fun detectExtension(url: String, contentType: String): String {
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()
        return when {
            path.endsWith(".mkv") || contentType.contains("matroska") -> "mkv"
            path.endsWith(".webm") || contentType.contains("webm") -> "webm"
            path.endsWith(".mov") || contentType.contains("quicktime") -> "mov"
            path.endsWith(".ts") || contentType.contains("mp2t") -> "ts"
            else -> "mp4"
        }
    }

    private fun detectMimeType(ext: String): String = when (ext) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "video/mp4"
    }

    private data class MoviesOutputTarget(
        val finalName: String,
        val outputStream: OutputStream,
        val mediaStoreUri: Uri? = null,
        val directFile: File? = null
    )

    private fun createMoviesOutputTarget(context: Context, desiredName: String, mimeType: String): MoviesOutputTarget {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, desiredName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoPlayerPro")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Kh\u00F4ng th\u1EC3 t\u1EA1o t\u1EC7p trong Movies/VideoPlayerPro")
            val os = context.contentResolver.openOutputStream(uri)
                ?: throw Exception("Kh\u00F4ng th\u1EC3 m\u1EDF lu\u1ED3ng ghi t\u1EC7p")
            MoviesOutputTarget(desiredName, os, mediaStoreUri = uri)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoPlayerPro").apply { mkdirs() }
            var candidate = File(dir, desiredName)
            var counter = 1
            val baseName = desiredName.substringBeforeLast('.')
            val ext = desiredName.substringAfterLast('.', "mp4")
            while (candidate.exists()) {
                candidate = File(dir, "${baseName}_$counter.$ext")
                counter++
            }
            MoviesOutputTarget(candidate.name, FileOutputStream(candidate), directFile = candidate)
        }
    }

    private fun finalizeMoviesOutputTarget(context: Context, target: MoviesOutputTarget, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && target.mediaStoreUri != null) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            context.contentResolver.update(target.mediaStoreUri, values, null, null)
            try {
                context.contentResolver.query(
                    target.mediaStoreUri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val realName = c.getString(0)
                        if (!realName.isNullOrBlank()) return realName
                    }
                }
            } catch (_: Exception) {}
        } else if (target.directFile != null) {
            MediaScannerConnection.scanFile(context, arrayOf(target.directFile.absolutePath), arrayOf(mimeType), null)
        }
        return target.finalName
    }

    private fun abortMoviesOutputTarget(context: Context, target: MoviesOutputTarget) {
        try {
            if (target.mediaStoreUri != null) {
                context.contentResolver.delete(target.mediaStoreUri, null, null)
            }
            target.directFile?.delete()
        } catch (_: Exception) {}
    }

    private fun upsertTask(task: StreamDownloadTask) {
        val list = _tasks.value.filterNot { it.url == task.url }
        _tasks.value = listOf(task) + list
    }

    private fun updateTask(url: String, transform: (StreamDownloadTask) -> StreamDownloadTask) {
        _tasks.value = _tasks.value.map { if (it.url == url) transform(it) else it }
    }

    private fun removeTask(url: String) {
        _tasks.value = _tasks.value.filterNot { it.url == url }
    }
}