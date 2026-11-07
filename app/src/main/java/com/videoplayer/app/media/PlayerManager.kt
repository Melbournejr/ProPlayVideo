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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.videoplayer.app.data.model.VideoItem
import java.io.File

data class ExternalSubtitleItem(
    val name: String,
    val uri: Uri,
    val mimeType: String
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
                .setExceedRendererCapabilitiesIfNecessary(true)
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedAudioConstraintsIfNecessary(true)
        )
    }

    private val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var currentBoostPercent: Int = 0
    private var currentBassPercent: Int = 0
    private val bandLevelsDb = FloatArray(5) { 0f }

    private var currentPlaylist: List<VideoItem> = emptyList()
    var currentIndex: Int = 0
        private set

    private val customSubtitlesByVideoId = mutableMapOf<String, MutableList<ExternalSubtitleItem>>()

    val player: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
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
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupAudioEffects(audioSessionId)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Nếu ContentUri gặp lỗi hoặc ngược lại, tự động thử fallback sang File Uri
                val vid = currentPlaylist.getOrNull(currentIndex) ?: return
                val curPos = getCurrentPosition()
                val fallbackUri = Uri.fromFile(File(vid.path))
                if (player.currentMediaItem?.localConfiguration?.uri != fallbackUri) {
                    val item = buildSingleMediaItem(vid, useFileUriFallback = true)
                    player.setMediaItem(item, curPos)
                    player.prepare()
                    player.play()
                }
            }
        })
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
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (i in 0 until numBands) {
                    val targetMb = (bandLevelsDb[i] * 100f).toInt().coerceIn(minMb, maxMb).toShort()
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
            if (equalizer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupAudioEffects(player.audioSessionId)
            }
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
            if (bassBoost == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupAudioEffects(player.audioSessionId)
            }
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
            if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupAudioEffects(player.audioSessionId)
            }
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

    /**
     * Chỉ quét phụ đề cho ĐÚNG 1 VIDEO đang mở (tối ưu 0ms, không quét cả 333 video cùng lúc)
     */
    fun findMatchingSubtitlesForSingleVideo(video: VideoItem): List<ExternalSubtitleItem> {
        val result = mutableListOf<ExternalSubtitleItem>()
        try {
            val videoFile = File(video.path)
            val parentDir = videoFile.parentFile ?: return emptyList()
            val baseName = videoFile.nameWithoutExtension
            val candidates = listOf(
                "$baseName.srt" to MimeTypes.APPLICATION_SUBRIP,
                "$baseName.vi.srt" to MimeTypes.APPLICATION_SUBRIP,
                "$baseName.en.srt" to MimeTypes.APPLICATION_SUBRIP,
                "$baseName.ass" to MimeTypes.TEXT_SSA,
                "$baseName.ssa" to MimeTypes.TEXT_SSA,
                "$baseName.vtt" to MimeTypes.TEXT_VTT
            )
            // Kiểm tra trực tiếp sự tồn tại của file thay vì gọi parentDir.listFiles() trên thư mục 333 file!
            for ((fileName, mime) in candidates) {
                val subFile = File(parentDir, fileName)
                if (subFile.exists() && subFile.isFile) {
                    result.add(ExternalSubtitleItem(subFile.name, Uri.fromFile(subFile), mime))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun getSubtitlesForVideo(video: VideoItem): List<ExternalSubtitleItem> {
        val custom = customSubtitlesByVideoId[video.id] ?: emptyList()
        val folderSubs = findMatchingSubtitlesForSingleVideo(video)
        return (custom + folderSubs).distinctBy { it.uri }
    }

    private fun resolveVideoUri(video: VideoItem, useFileUriFallback: Boolean = false): Uri {
        val idLong = video.id.toLongOrNull()
        return if (!useFileUriFallback && idLong != null) {
            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
        } else {
            Uri.fromFile(File(video.path))
        }
    }

    private fun buildSingleMediaItem(video: VideoItem, useFileUriFallback: Boolean = false): MediaItem {
        val vUri = resolveVideoUri(video, useFileUriFallback)
        val subs = getSubtitlesForVideo(video)
        val subConfigs = subs.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(sub.uri)
                .setMimeType(sub.mimeType)
                .setLanguage("vi")
                .setLabel(sub.name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
                .build()
        }

        return MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(vUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.displayName)
                    .build()
            )
            .setSubtitleConfigurations(subConfigs)
            .build()
    }

    /**
     * Nạp tức thì DUY NHẤT video đang chọn vào ExoPlayer (mở trong 0.05s ngay cả với thư mục 1000 video 4K)
     */
    fun playPlaylist(playlist: List<VideoItem>, startIndex: Int, startPositionMs: Long = 0L): List<ExternalSubtitleItem> {
        val safeList = if (playlist.isEmpty()) emptyList() else playlist
        currentPlaylist = safeList
        if (safeList.isEmpty()) return emptyList()

        currentIndex = startIndex.coerceIn(0, safeList.size - 1)
        val targetVideo = safeList[currentIndex]
        val mediaItem = buildSingleMediaItem(targetVideo)

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            setupAudioEffects(player.audioSessionId)
        }
        return getSubtitlesForVideo(targetVideo)
    }

    fun playVideoAtIndex(index: Int, startPositionMs: Long = 0L): VideoItem? {
        if (index !in currentPlaylist.indices) return null
        currentIndex = index
        val targetVideo = currentPlaylist[currentIndex]
        val mediaItem = buildSingleMediaItem(targetVideo)

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()
        return targetVideo
    }

    fun playNextInPlaylist(startPositionMs: Long = 0L): VideoItem? {
        return playVideoAtIndex(currentIndex + 1, startPositionMs)
    }

    fun playPreviousInPlaylist(startPositionMs: Long = 0L): VideoItem? {
        return playVideoAtIndex(currentIndex - 1, startPositionMs)
    }

    fun attachExternalSubtitleToCurrent(video: VideoItem, uri: Uri, displayName: String): List<ExternalSubtitleItem> {
        val curPos = getCurrentPosition()
        val lower = displayName.lowercase()
        val mime = when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val list = customSubtitlesByVideoId.getOrPut(video.id) { mutableListOf() }
        list.removeAll { it.uri == uri }
        list.add(0, ExternalSubtitleItem(displayName, uri, mime))

        val updatedItem = buildSingleMediaItem(video)
        player.setMediaItem(updatedItem, curPos)
        player.prepare()
        player.playWhenReady = true
        player.play()
        toggleSubtitles(true)
        return getSubtitlesForVideo(video)
    }

    fun playStream(url: String) {
        currentPlaylist = emptyList()
        val uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(uri)
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun setPlaybackSpeed(speed: Float) { player.playbackParameters = PlaybackParameters(speed) }
    fun pause() = player.pause()
    fun resume() = player.play()
    fun isPlaying(): Boolean = player.isPlaying
    fun getCurrentPosition(): Long = player.currentPosition.coerceAtLeast(0L)
    fun getDuration(): Long = player.duration.coerceAtLeast(1L)

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