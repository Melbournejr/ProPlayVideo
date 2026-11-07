package com.videoplayer.app.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelection
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.videoplayer.app.data.model.VideoItem
import java.util.concurrent.ConcurrentHashMap

class PlayerManager(private val context: Context) {

    private val trackSelector: DefaultTrackSelector = DefaultTrackSelector(context)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .build()

    private val lastPositions = ConcurrentHashMap<String, Long>()

    fun playLocal(video: VideoItem, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.fromUri(video.path)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
        lastPositions[video.id] = startPositionMs
    }

    fun playStream(url: String, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    fun playUri(uri: Uri, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration

    fun release() {
        player.release()
    }

    fun savePosition(videoId: String, positionMs: Long) {
        lastPositions[videoId] = positionMs
    }

    fun readPosition(videoId: String): Long = lastPositions[videoId] ?: 0L

    fun selectAudioTrack(trackIndex: Int) {
        val tracks = player.currentTracks ?: return
        val builder = DefaultTrackSelector.Parameters.Builder(context)
        for (i in 0 until tracks.size) {
            val group = tracks.getTrackGroup(i)
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                val mapping = DefaultTrackSelector.SelectionOverride(trackIndex)
                builder.setSelectionOverride(group, mapping)
                break
            }
        }
        trackSelector.parameters = builder.build()
    }

    fun selectSubtitleTrack(trackIndex: Int) {
        val tracks = player.currentTracks ?: return
        val builder = DefaultTrackSelector.Parameters.Builder(context)
        for (i in 0 until tracks.size) {
            val group = tracks.getTrackGroup(i)
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                val mapping = DefaultTrackSelector.SelectionOverride(trackIndex)
                builder.setSelectionOverride(group, mapping)
                break
            }
        }
        trackSelector.parameters = builder.build()
    }

    fun disableSubtitles() {
        val tracks = player.currentTracks ?: return
        val builder = DefaultTrackSelector.Parameters.Builder(context)
        for (i in 0 until tracks.size) {
            val group = tracks.getTrackGroup(i)
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                val mapping = DefaultTrackSelector.SelectionOverride(DefaultTrackSelector.SelectionOverride.SKIP)
                builder.setSelectionOverride(group, mapping)
                break
            }
        }
        trackSelector.parameters = builder.build()
    }
}
