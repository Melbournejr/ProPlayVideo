package com.videoplayer.app.util

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import com.videoplayer.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

/**
 * Bo dieu khien Picture-in-Picture (PiP) goc cua Android:
 * - Tu tinh ty le khung hinh theo video Doc / Ngang (kep an toan 0.426..2.35 chong crash)
 * - Gan RemoteAction: [Truoc | Phat/Dung | Tiep] cho playlist, hoac [-10s | Phat/Dung | +10s] cho video le/Stream
 * - Bat SeamlessResize + AutoEnter + SourceRectHint tren Android 12+ (API 31+)
 */
object PipController {
    const val ACTION_PIP_CONTROL = "com.videoplayer.app.ACTION_PIP_CONTROL"
    const val EXTRA_CONTROL_TYPE = "control_type"

    const val CONTROL_PLAY_PAUSE = 1
    const val CONTROL_PREV = 2
    const val CONTROL_NEXT = 3
    const val CONTROL_REWIND_10 = 4
    const val CONTROL_FORWARD_10 = 5

    val isInPipMode = MutableStateFlow(false)

    @Volatile
    var shouldAutoEnterOnLeave: Boolean = false

    @Volatile
    private var cachedParams: Any? = null

    fun computeSafeAspectRatio(videoWidth: Int, videoHeight: Int, pixelWidthHeightRatio: Float = 1f): Rational {
        if (videoWidth <= 0 || videoHeight <= 0) return Rational(16, 9)
        val safePixelRatio = if (pixelWidthHeightRatio > 0.1f) pixelWidthHeightRatio else 1f
        val rawRatio = (videoWidth * safePixelRatio) / videoHeight.toFloat()
        // Android cho phep tu 1/2.39 (0.4184) den 2.39 -> kep an toan trong 0.426..2.35
        val clamped = rawRatio.coerceIn(0.426f, 2.35f)
        return Rational((clamped * 1000f).roundToInt().coerceAtLeast(426), 1000)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createAction(
        context: Context,
        @DrawableRes iconRes: Int,
        title: String,
        controlType: Int,
        enabled: Boolean = true
    ): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            controlType,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(Icon.createWithResource(context, iconRes), title, title, pi).apply {
            isEnabled = enabled
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildParams(
        context: Context,
        isPlaying: Boolean,
        hasPlaylistNavigation: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
        sourceRectHint: Rect?,
        autoEnterEnabled: Boolean
    ): PictureInPictureParams {
        val actions = ArrayList<RemoteAction>(3)
        if (hasPlaylistNavigation) {
            actions.add(
                createAction(
                    context = context,
                    iconRes = R.drawable.ic_pip_prev,
                    title = "Video tr\u01B0\u1EDBc",
                    controlType = CONTROL_PREV,
                    enabled = hasPrevious
                )
            )
        } else {
            actions.add(
                createAction(
                    context = context,
                    iconRes = R.drawable.ic_pip_rew10,
                    title = "Tua l\u00F9i 10 gi\u00E2y",
                    controlType = CONTROL_REWIND_10,
                    enabled = true
                )
            )
        }

        actions.add(
            createAction(
                context = context,
                iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
                title = if (isPlaying) "T\u1EA1m d\u1EEBng" else "Ph\u00E1t",
                controlType = CONTROL_PLAY_PAUSE,
                enabled = true
            )
        )

        if (hasPlaylistNavigation) {
            actions.add(
                createAction(
                    context = context,
                    iconRes = R.drawable.ic_pip_next,
                    title = "Video ti\u1EBFp",
                    controlType = CONTROL_NEXT,
                    enabled = hasNext
                )
            )
        } else {
            actions.add(
                createAction(
                    context = context,
                    iconRes = R.drawable.ic_pip_ff10,
                    title = "Tua t\u1EDBi 10 gi\u00E2y",
                    controlType = CONTROL_FORWARD_10,
                    enabled = true
                )
            )
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(computeSafeAspectRatio(videoWidth, videoHeight, pixelWidthHeightRatio))
            .setActions(actions)

        if (sourceRectHint != null && !sourceRectHint.isEmpty) {
            builder.setSourceRectHint(sourceRectHint)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true)
            builder.setAutoEnterEnabled(autoEnterEnabled)
        }

        return builder.build()
    }

    fun updateActivityPipParams(
        activity: Activity?,
        isPlaying: Boolean,
        hasPlaylistNavigation: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
        sourceRectHint: Rect?,
        autoEnterEnabled: Boolean
    ) {
        shouldAutoEnterOnLeave = autoEnterEnabled
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val params = buildParams(
                context = activity,
                isPlaying = isPlaying,
                hasPlaylistNavigation = hasPlaylistNavigation,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                pixelWidthHeightRatio = pixelWidthHeightRatio,
                sourceRectHint = sourceRectHint,
                autoEnterEnabled = autoEnterEnabled
            )
            cachedParams = params
            activity.setPictureInPictureParams(params)
        } catch (_: Exception) {}
    }

    fun enterPipNow(
        activity: Activity?,
        isPlaying: Boolean,
        hasPlaylistNavigation: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float,
        sourceRectHint: Rect?
    ): Boolean {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val params = buildParams(
                context = activity,
                isPlaying = isPlaying,
                hasPlaylistNavigation = hasPlaylistNavigation,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                pixelWidthHeightRatio = pixelWidthHeightRatio,
                sourceRectHint = sourceRectHint,
                autoEnterEnabled = isPlaying
            )
            cachedParams = params
            activity.enterPictureInPictureMode(params)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun enterPipOnUserLeaveIfNeeded(activity: Activity) {
        // Android 12+ da tu xu ly bang setAutoEnterEnabled(true) muot hon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!shouldAutoEnterOnLeave || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val params = (cachedParams as? PictureInPictureParams)
                ?: PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {}
    }

    fun disableAutoEnter(activity: Activity?) {
        shouldAutoEnterOnLeave = false
        cachedParams = null
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            activity.setPictureInPictureParams(
                PictureInPictureParams.Builder().setAutoEnterEnabled(false).build()
            )
        } catch (_: Exception) {}
    }
}