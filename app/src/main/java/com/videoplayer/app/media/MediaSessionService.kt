package com.videoplayer.app.media

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MediaSessionService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var playerManager: PlayerManager

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManager(this)
        mediaSession = MediaSession.Builder(this, playerManager.player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}