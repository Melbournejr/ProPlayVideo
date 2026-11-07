package com.videoplayer.app.media

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.videoplayer.app.data.model.VideoItem
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerManager(context: Context) {

    val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context)
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostPercent: Int = 0

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
                    setTargetGain(currentBoostPercent * 15) // 100% boost = +1500 mB (+15 dB)
                    enabled = true
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Đặt mức khuếch đại âm lượng vượt ngưỡng (0 -> 100 tương ứng 100% -> 200% tổng âm lượng)
     */
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

    fun playLocal(video: VideoItem, startPositionMs: Long = 0L) {
        val uri = Uri.fromFile(File(video.path))
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            setupLoudnessEnhancer(player.audioSessionId)
        }
    }

    fun playStream(url: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
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