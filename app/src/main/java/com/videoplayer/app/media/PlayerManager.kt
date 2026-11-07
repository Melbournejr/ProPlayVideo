package com.videoplayer.app.media

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(private val context: Context) {

    val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context)
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostPercent: Int = 0

    private var currentVideoUri: Uri? = null
    private var activeSubtitles = mutableListOf<ExternalSubtitleItem>()

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
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupLoudnessEnhancer(audioSessionId)
            }
        })
    }

    private fun setupLoudnessEnhancer(audioSessionId: Int) {
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
    }

    fun setAudioBoostPercent(boostPercent: Int) {
        currentBoostPercent = boostPercent.coerceIn(0, 100)
        try {
            if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupLoudnessEnhancer(player.audioSessionId)
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
     * Quét tự động các file phụ đề trong cùng thư mục có tên giống với tên video
     */
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
                        // Khớp chính xác tên video hoặc dạng tên_video.vi.srt / tên_video.en.srt
                        if (fileBase == baseName || fileBase.startsWith("$baseName.")) {
                            result.add(ExternalSubtitleItem(file.name, Uri.fromFile(file), mime))
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun playLocal(video: VideoItem, startPositionMs: Long = 0L): List<ExternalSubtitleItem> {
        val vUri = Uri.fromFile(File(video.path))
        currentVideoUri = vUri
        activeSubtitles.clear()

        val matchedSubs = findMatchingSubtitlesInFolder(video)
        activeSubtitles.addAll(matchedSubs)

        buildAndPrepareMediaItem(vUri, startPositionMs)
        return matchedSubs
    }

    /**
     * Nạp thêm file phụ đề do người dùng chọn từ bộ nhớ máy
     */
    fun attachExternalSubtitle(uri: Uri, displayName: String) {
        val vUri = currentVideoUri ?: return
        val curPos = getCurrentPosition()
        val lower = displayName.lowercase()
        val mime = when {
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val newSub = ExternalSubtitleItem(displayName, uri, mime)
        activeSubtitles.removeAll { it.uri == uri }
        activeSubtitles.add(0, newSub)

        toggleSubtitles(true)
        buildAndPrepareMediaItem(vUri, curPos)
    }

    fun getLoadedSubtitles(): List<ExternalSubtitleItem> = activeSubtitles.toList()

    private fun buildAndPrepareMediaItem(videoUri: Uri, positionMs: Long) {
        val subConfigs = activeSubtitles.map { sub ->
            MediaItem.SubtitleConfiguration.Builder(sub.uri)
                .setMimeType(sub.mimeType)
                .setLanguage("vi")
                .setLabel(sub.name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
                .build()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setSubtitleConfigurations(subConfigs)
            .build()

        player.setMediaItem(mediaItem, positionMs)
        player.prepare()
        player.play()
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            setupLoudnessEnhancer(player.audioSessionId)
        }
    }

    fun playStream(url: String) {
        val uri = Uri.parse(url)
        currentVideoUri = uri
        activeSubtitles.clear()
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
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
        loudnessEnhancer = null
        player.release()
    }

    fun toggleSubtitles(enabled: Boolean) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }
}