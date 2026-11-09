package com.videoplayer.app.media

import android.content.ContentUris
import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.videoplayer.app.data.local.readCustomSubtitleForVideo
import com.videoplayer.app.data.local.saveCustomSubtitleForVideo
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

data class ExternalSubtitleItem(
    val name: String,
    val uri: Uri,
    val mimeType: String
)

data class MediaTrackOption(
    val trackType: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val trackGroup: TrackGroup,
    val label: String,
    val isSelected: Boolean
)

data class VideoNerdStats(
    val resolution: String,
    val videoCodec: String,
    val frameRate: String,
    val videoBitrate: String,
    val audioCodec: String,
    val droppedFrames: Int
)

data class EqBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val label: String,
    val levelDb: Float
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(private val context: Context) {

    val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredTextLanguage("vi")
                .setSelectUndeterminedTextLanguage(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedAudioConstraintsIfNecessary(true)
        )
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 400, 800)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // Bước 5: Đặt EXTENSION_RENDERER_MODE_PREFER để ưu tiên tối đa bộ giải mã mở rộng AC3/EAC3/DTS
    private val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    // Bước 4: OkHttpClient riêng dành cho luồng Stream của người dùng (hỗ trợ cả MODERN_TLS và CLEARTEXT khi được phép)
    private val streamHttpClient = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val okHttpDataSourceFactory = OkHttpDataSource.Factory(streamHttpClient)
        .setUserAgent("VideoPlayerPro/1.1")

    private val baseDataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)

    // Bước 2: Bọc trong VaultCipherDataSource.Factory để giải mã trực tiếp scheme vault:// trên RAM
    private val vaultAwareDataSourceFactory = VaultCipherDataSource.Factory(baseDataSourceFactory)

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var currentBoostPercent: Int = 0
    private var currentBassPercent: Int = 0
    private val bandLevelsDb = FloatArray(5) { 0f }

    private var currentPlaylist: List<VideoItem> = emptyList()
    var currentIndex: Int = 0
        private set

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(DefaultMediaSourceFactory(vaultAwareDataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .build()

    init {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupAudioEffects(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                val vid = currentPlaylist.getOrNull(currentIndex) ?: return
                if (vid.path.startsWith("vault://")) return
                val curPos = getCurrentPosition()
                val fallbackUri = Uri.fromFile(File(vid.path))
                if (player.currentMediaItem?.localConfiguration?.uri != fallbackUri) {
                    val item = buildSingleMediaItem(vid, emptyList(), useFileUriFallback = true)
                    player.setMediaItem(item, curPos)
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    /**
     * Bước 5: Lấy danh sách các Track Âm thanh (C.TRACK_TYPE_AUDIO) hoặc Phụ đề (C.TRACK_TYPE_TEXT) hiện có trong video
     */
    fun getAvailableTracks(trackType: Int): List<MediaTrackOption> {
        val result = mutableListOf<MediaTrackOption>()
        val groups = player.currentTracks.groups
        for (gIdx in 0 until groups.size) {
            val group = groups[gIdx]
            if (group.type == trackType) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (tIdx in 0 until mediaTrackGroup.length) {
                    val format = group.getTrackFormat(tIdx)
                    val lang = format.language?.uppercase() ?: ""
                    val label = format.label ?: ""
                    val mime = format.sampleMimeType?.substringAfter("/")?.uppercase() ?: ""
                    val ch = if (format.channelCount > 0) "${format.channelCount}ch" else ""
                    val display = listOf(label, lang, mime, ch)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                        .ifBlank { "Track #${tIdx + 1}" }

                    result.add(
                        MediaTrackOption(
                            trackType = trackType,
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            trackGroup = mediaTrackGroup,
                            label = display,
                            isSelected = group.isTrackSelected(tIdx)
                        )
                    )
                }
            }
        }
        return result
    }

    /**
     * Bước 5: Chọn Track cụ thể bằng TrackSelectionOverride của Media3
     */
    fun selectTrackOverride(option: MediaTrackOption) {
        if (option.trackType == C.TRACK_TYPE_TEXT) {
            toggleSubtitles(true)
        }
        val override = TrackSelectionOverride(option.trackGroup, listOf(option.trackIndex))
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(option.trackType)
            .addOverride(override)
            .build()
    }

    fun clearTrackOverrides(trackType: Int) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .clearOverridesOfType(trackType)
            .build()
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                if (currentBoostPercent > 0) {
                    setTargetGain(currentBoostPercent * 15)
                    enabled = true
                }
            }
        } catch (_: Exception) {}

        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                val numBands = numberOfBands.toInt().coerceAtMost(5)
                val range = bandLevelRange
                for (i in 0 until numBands) {
                    val targetMb = (bandLevelsDb[i] * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                    setBandLevel(i.toShort(), targetMb)
                }
            }
        } catch (_: Exception) {}

        try {
            bassBoost?.release()
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = currentBassPercent > 0
                if (strengthSupported) {
                    setStrength((currentBassPercent * 10).coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {}
    }

    fun getEqualizerBands(): List<EqBandInfo> {
        val eq = equalizer
        val defaultLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
        return (0 until 5).map { idx ->
            val freqHz = try {
                if (eq != null && idx < eq.numberOfBands) (eq.getCenterFreq(idx.toShort()) / 1000) else defaultFreqs[idx]
            } catch (_: Exception) { defaultFreqs[idx] }
            val label = if (freqHz >= 1000) "%.1fkHz".format(freqHz / 1000f).replace(".0", "") else "${freqHz}Hz"
            EqBandInfo(idx, freqHz, label.ifEmpty { defaultLabels[idx] }, bandLevelsDb[idx])
        }
    }

    fun setEqBandLevel(bandIndex: Int, levelDb: Float) {
        if (bandIndex !in 0..4) return
        val clamped = levelDb.coerceIn(-15f, 15f)
        bandLevelsDb[bandIndex] = clamped
        try {
            equalizer?.let { eq ->
                if (bandIndex < eq.numberOfBands) {
                    val range = eq.bandLevelRange
                    val mb = (clamped * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
                    eq.setBandLevel(bandIndex.toShort(), mb)
                }
            }
        } catch (_: Exception) {}
    }

    fun applyEqPreset(levelsDb: List<Float>, bassPercent: Int) {
        for (i in 0 until minOf(5, levelsDb.size)) {
            setEqBandLevel(i, levelsDb[i])
        }
        setBassBoostPercent(bassPercent)
    }

    fun setBassBoostPercent(percent: Int) {
        currentBassPercent = percent.coerceIn(0, 100)
        try {
            bassBoost?.apply {
                enabled = currentBassPercent > 0
                if (strengthSupported) {
                    setStrength((currentBassPercent * 10).coerceIn(0, 1000).toShort())
                }
            }
        } catch (_: Exception) {}
    }

    fun setAudioBoostPercent(boostPercent: Int) {
        currentBoostPercent = boostPercent.coerceIn(0, 100)
        try {
            loudnessEnhancer?.apply {
                if (currentBoostPercent > 0) {
                    setTargetGain(currentBoostPercent * 15)
                    enabled = true
                } else {
                    setTargetGain(0)
                    enabled = false
                }
            }
        } catch (_: Exception) {}
    }

    private fun detectSubtitleMime(fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    suspend fun findSubtitlesIo(video: VideoItem): List<ExternalSubtitleItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ExternalSubtitleItem>()
        try {
            val savedSub = context.readCustomSubtitleForVideo(video.id)
            if (savedSub != null) {
                val (uriOrPath, displayName) = savedSub
                val mime = detectSubtitleMime(displayName)
                if (uriOrPath.startsWith("/")) {
                    val f = File(uriOrPath)
                    if (f.exists() && f.length() > 0L) {
                        result.add(ExternalSubtitleItem(displayName, Uri.fromFile(f), mime))
                    }
                } else {
                    result.add(ExternalSubtitleItem(displayName, Uri.parse(uriOrPath), mime))
                }
            }

            if (!video.path.startsWith("vault://") && !video.path.startsWith("content://")) {
                val videoFile = File(video.path)
                val parentDir = videoFile.parentFile
                if (parentDir != null) {
                    val baseName = videoFile.nameWithoutExtension
                    val candidates = listOf(
                        "$baseName.srt" to MimeTypes.APPLICATION_SUBRIP,
                        "$baseName.vi.srt" to MimeTypes.APPLICATION_SUBRIP,
                        "$baseName.en.srt" to MimeTypes.APPLICATION_SUBRIP,
                        "$baseName.ass" to MimeTypes.TEXT_SSA,
                        "$baseName.ssa" to MimeTypes.TEXT_SSA,
                        "$baseName.vtt" to MimeTypes.TEXT_VTT
                    )
                    for ((fileName, mime) in candidates) {
                        val subFile = File(parentDir, fileName)
                        if (subFile.exists() && subFile.isFile) {
                            result.add(ExternalSubtitleItem(subFile.name, Uri.fromFile(subFile), mime))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        result.distinctBy { it.uri }
    }

    private fun resolveVideoUri(video: VideoItem, useFileUriFallback: Boolean = false): Uri {
        if (video.path.startsWith("vault://") || video.path.startsWith("content://") || video.path.startsWith("http://") || video.path.startsWith("https://")) {
            return Uri.parse(video.path)
        }
        if (video.id.startsWith("vault_") && !video.path.startsWith("vault://")) return Uri.fromFile(File(video.path))
        val idLong = video.id.toLongOrNull()
        return if (!useFileUriFallback && idLong != null) {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
        } else {
            Uri.fromFile(File(video.path))
        }
    }

    private fun buildSingleMediaItem(
        video: VideoItem,
        subs: List<ExternalSubtitleItem>,
        useFileUriFallback: Boolean = false
    ): MediaItem {
        val vUri = resolveVideoUri(video, useFileUriFallback)
        val subConfigs = subs.mapIndexed { index, sub ->
            MediaItem.SubtitleConfiguration.Builder(sub.uri)
                .setMimeType(sub.mimeType)
                .setLanguage("vi")
                .setLabel(sub.name)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT else C.SELECTION_FLAG_AUTOSELECT)
                .build()
        }

        return MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(vUri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.displayName).build())
            .setSubtitleConfigurations(subConfigs)
            .build()
    }

    suspend fun playPlaylist(playlist: List<VideoItem>, startIndex: Int, startPositionMs: Long = 0L): List<ExternalSubtitleItem> {
        val safeList = if (playlist.isEmpty()) emptyList() else playlist
        currentPlaylist = safeList
        if (safeList.isEmpty()) return emptyList()

        currentIndex = startIndex.coerceIn(0, safeList.size - 1)
        val targetVideo = safeList[currentIndex]
        val subs = findSubtitlesIo(targetVideo)
        val mediaItem = buildSingleMediaItem(targetVideo, subs)

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()
        return subs
    }

    suspend fun playVideoAtIndex(index: Int, startPositionMs: Long = 0L): Pair<VideoItem?, List<ExternalSubtitleItem>> {
        if (index !in currentPlaylist.indices) return null to emptyList()
        currentIndex = index
        val targetVideo = currentPlaylist[currentIndex]
        val subs = findSubtitlesIo(targetVideo)
        val mediaItem = buildSingleMediaItem(targetVideo, subs)

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()
        return targetVideo to subs
    }

    suspend fun attachExternalSubtitleToCurrent(video: VideoItem, uri: Uri, displayName: String): List<ExternalSubtitleItem> {
        val curPos = getCurrentPosition()
        val persistedPathOrUri = withContext(Dispatchers.IO) {
            try {
                val subDir = File(context.filesDir, "subtitles")
                if (!subDir.exists()) subDir.mkdirs()
                val ext = displayName.substringAfterLast('.', "srt")
                val destFile = File(subDir, "sub_${video.id}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else uri.toString()
            } catch (_: Exception) {
                uri.toString()
            }
        }

        context.saveCustomSubtitleForVideo(video.id, persistedPathOrUri, displayName)

        val subs = findSubtitlesIo(video)
        val updatedItem = buildSingleMediaItem(video, subs)
        player.setMediaItem(updatedItem, curPos)
        player.prepare()
        player.playWhenReady = true
        player.play()
        toggleSubtitles(true)
        return subs
    }

    fun playStream(url: String) {
        currentPlaylist = emptyList()
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun seekToFast(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))
    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0L))
    fun setPlaybackSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    fun pause() = player.pause()
    fun resume() = player.play()
    fun isPlaying(): Boolean = player.isPlaying
    fun getCurrentPosition(): Long = player.currentPosition.coerceAtLeast(0L)
    fun getDuration(): Long = player.duration.coerceAtLeast(1L)

    fun stepFrame(forward: Boolean) {
        player.pause()
        val vf = player.videoFormat
        val fps = if (vf != null && vf.frameRate > 0f) vf.frameRate else 30f
        val frameDurationMs = (1000f / fps).toLong().coerceIn(16L, 66L)
        val delta = if (forward) frameDurationMs else -frameDurationMs
        val target = (getCurrentPosition() + delta).coerceIn(0L, getDuration())
        player.seekTo(target)
    }

    fun getNerdStats(): VideoNerdStats {
        val vf = player.videoFormat
        val af = player.audioFormat
        val counters = player.videoDecoderCounters
        val res = if (vf != null && vf.width > 0) "${vf.width}x${vf.height}" else "Đang đo..."
        val vCodec = vf?.sampleMimeType?.substringAfter("/")?.uppercase() ?: "HW Decoder"
        val fps = if (vf != null && vf.frameRate > 0f) "%.1f FPS".format(vf.frameRate) else "Auto FPS"
        val vBitrate = if (vf != null && vf.bitrate > 0) "%.2f Mbps".format(vf.bitrate / 1_000_000f) else "VBR (Auto)"
        val aCodec = if (af != null) {
            val mime = af.sampleMimeType?.substringAfter("/")?.uppercase() ?: "Audio"
            val ch = if (af.channelCount > 0) "${af.channelCount}ch" else ""
            val hz = if (af.sampleRate > 0) "${af.sampleRate / 1000}kHz" else ""
            "$mime $ch $hz".trim()
        } else "Stereo"
        val dropped = counters?.droppedBufferCount ?: 0
        return VideoNerdStats(res, vCodec, fps, vBitrate, aCodec, dropped)
    }

    fun release() {
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        equalizer = null
        bassBoost = null
        player.release()
    }

    fun toggleSubtitles(enabled: Boolean) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }
}