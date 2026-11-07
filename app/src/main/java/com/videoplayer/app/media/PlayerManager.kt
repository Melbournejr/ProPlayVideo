package com.videoplayer.app.media

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
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

    val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context)
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var currentBoostPercent: Int = 0
    private var currentBassPercent: Int = 0
    private val bandLevelsDb = FloatArray(5) { 0f }

    private var currentPlaylist: List<VideoItem> = emptyList()
    private val customSubtitlesByVideoId = mutableMapOf<String, MutableList<ExternalSubtitleItem>>()

    val player: ExoPlayer = ExoPlayer.Builder(context)
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

    fun getBassBoostPercent(): Int = currentBassPercent

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

    fun findMatchingSubtitlesInFolder(video: VideoItem): List<ExternalSubtitleItem> {
        val result = mutableListOf<ExternalSubtitleItem>()
        try {
            val videoFile = File(video.path)
            val parentDir = videoFile.parentFile ?: return emptyList()
            val baseName = videoFile.nameWithoutExtension.lowercase()
            val subExtensions = mapOf(
                "srt" to MimeTypes.APPLICATION_SUBRIP,
                "ass" to MimeTypes.TEXT_SSA,
                "ssa" to MimeTypes.TEXT_SSA,
                "vtt" to MimeTypes.TEXT_VTT
            )

            parentDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    val mime = subExtensions[ext]
                    if (mime != null) {
                        val fileBase = file.nameWithoutExtension.lowercase()
                        if (fileBase == baseName || fileBase.startsWith("$baseName.")) {
                            result.add(ExternalSubtitleItem(file.name, Uri.fromFile(file), mime))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun getSubtitlesForVideo(video: VideoItem): List<ExternalSubtitleItem> {
        val custom = customSubtitlesByVideoId[video.id] ?: emptyList()
        val folderSubs = findMatchingSubtitlesInFolder(video)
        return (custom + folderSubs).distinctBy { it.uri }
    }

    private fun buildMediaItemForVideo(video: VideoItem): MediaItem {
        val vUri = Uri.fromFile(File(video.path))
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

    fun playPlaylist(playlist: List<VideoItem>, startIndex: Int, startPositionMs: Long = 0L): List<ExternalSubtitleItem> {
        val safeList = if (playlist.isEmpty()) emptyList() else playlist
        currentPlaylist = safeList
        if (safeList.isEmpty()) return emptyList()

        val validIndex = startIndex.coerceIn(0, safeList.size - 1)
        val mediaItems = safeList.map { buildMediaItemForVideo(it) }

        player.stop()
        player.clearMediaItems()
        player.setMediaItems(mediaItems, validIndex, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        player.play()

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            setupAudioEffects(player.audioSessionId)
        }
        return getSubtitlesForVideo(safeList[validIndex])
    }

    fun playNextInPlaylist(startPositionMs: Long = 0L): VideoItem? {
        if (currentPlaylist.isEmpty()) return null
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex in currentPlaylist.indices) {
            player.seekTo(nextIndex, startPositionMs)
            player.prepare()
            player.playWhenReady = true
            player.play()
            return currentPlaylist[nextIndex]
        }
        return null
    }

    fun playPreviousInPlaylist(startPositionMs: Long = 0L): VideoItem? {
        if (currentPlaylist.isEmpty()) return null
        val prevIndex = player.currentMediaItemIndex - 1
        if (prevIndex in currentPlaylist.indices) {
            player.seekTo(prevIndex, startPositionMs)
            player.prepare()
            player.playWhenReady = true
            player.play()
            return currentPlaylist[prevIndex]
        }
        return null
    }

    fun attachExternalSubtitleToCurrent(video: VideoItem, uri: Uri, displayName: String): List<ExternalSubtitleItem> {
        val curPos = getCurrentPosition()
        val curIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val lower = displayName.lowercase()
        val mime = when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val list = customSubtitlesByVideoId.getOrPut(video.id) { mutableListOf() }
        list.removeAll { it.uri == uri }
        list.add(0, ExternalSubtitleItem(displayName, uri, mime))

        val updatedItem = buildMediaItemForVideo(video)
        if (curIndex < player.mediaItemCount) {
            player.replaceMediaItem(curIndex, updatedItem)
            player.seekTo(curIndex, curPos)
            player.prepare()
            player.play()
        }
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