package com.videoplayer.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.videoplayer.app.data.model.VideoItem
import java.io.File

class PlayerManager(context: Context) {

    val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context)

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

    fun playLocal(video: VideoItem, startPositionMs: Long = 0L) {
        val uri = Uri.fromFile(File(video.path))
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
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
    fun release() = player.release()

    fun toggleSubtitles(enabled: Boolean) {
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }
}