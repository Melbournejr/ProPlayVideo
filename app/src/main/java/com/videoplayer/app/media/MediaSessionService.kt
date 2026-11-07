package com.videoplayer.app.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionResult.ImmediateResult

class MediaSessionService : MediaSessionService() {

    private lateinit var playerManager: PlayerManager

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManager(applicationContext)
    }

    override fun onGetSession(
        controllerType: String,
        packageName: String,
        controllerName: String?
    ): MediaSession? {
        return MediaSession.Builder(this, playerManager.player).build()
    }
}
