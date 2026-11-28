package com.videoplayer.app.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.util.PipController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    initialVideo: VideoItem?,
    playlist: List<VideoItem>,
    streamUrl: String?,
    streamTitle: String? = null,
    onVideoChanged: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { PlayerManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val state = remember(initialVideo, playlist, streamUrl, streamTitle) {
        PlayerStateHolder(
            context = context,
            scope = scope,
            playerManager = playerManager,
            initialVideo = initialVideo,
            rawPlaylist = playlist,
            streamUrl = streamUrl,
            streamTitle = streamTitle,
            onVideoChangedCallback = onVideoChanged
        )
    }

    // Dong bo trang thai PiP tuc thi tu MainActivity.onPictureInPictureModeChanged
    val pipModeFlow by PipController.isInPipMode.collectAsState()
    LaunchedEffect(pipModeFlow) {
        state.isInPipMode = pipModeFlow
        if (pipModeFlow) {
            state.showControls = false
            state.activeModal = ActiveModal.NONE
            state.resetZoom()
        }
    }

    // Cap nhat PictureInPictureParams (Ty le Doc/Ngang + Nut Play/Pause/Next/Prev + SeamlessResize + AutoEnter)
    LaunchedEffect(
        activity,
        state.isPlaying,
        state.effectivePlaylist.size,
        state.hasPrevious,
        state.hasNext,
        state.videoWidth,
        state.videoHeight,
        state.videoPixelRatio,
        state.playerBoundsRect,
        state.isLocked
    ) {
        PipController.updateActivityPipParams(
            activity = activity,
            isPlaying = state.isPlaying,
            hasPlaylistNavigation = state.effectivePlaylist.size > 1,
            hasPrevious = state.hasPrevious,
            hasNext = state.hasNext,
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            pixelWidthHeightRatio = state.videoPixelRatio,
            sourceRectHint = state.playerBoundsRect,
            autoEnterEnabled = state.isPlaying && !state.isLocked
        )
    }

    // Dang ky BroadcastReceiver nhan su kien bam nut tren cua so PiP
    DisposableEffect(context, state) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != PipController.ACTION_PIP_CONTROL) return
                when (intent.getIntExtra(PipController.EXTRA_CONTROL_TYPE, 0)) {
                    PipController.CONTROL_PLAY_PAUSE -> state.togglePlayPause()
                    PipController.CONTROL_PREV -> state.triggerPreviousVideo()
                    PipController.CONTROL_NEXT -> state.triggerNextVideo()
                    PipController.CONTROL_REWIND_10 -> state.seekByDelta(-10_000L)
                    PipController.CONTROL_FORWARD_10 -> state.seekByDelta(10_000L)
                }
            }
        }
        val filter = IntentFilter(PipController.ACTION_PIP_CONTROL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val vid = state.currentVideo
        if (uri != null && vid != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            val fileName = context.resolveUriFileName(uri)
            scope.launch {
                state.loadedSubtitles = playerManager.attachExternalSubtitleToCurrent(vid, uri, fileName)
                state.subtitlesEnabled = true
                state.showTemporaryHint("\u0110\u00E3 n\u1EA1p ph\u1EE5 \u0111\u1EC1: $fileName", 1600L)
            }
        }
    }

    // Quan ly System Bars, FLAG_SECURE & tat Auto-Enter PiP khi thoat PlayerScreen
    DisposableEffect(activity, state.isVaultVideo) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (state.isVaultVideo) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            PipController.disableAutoEnter(activity)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Khoi tao phat video ban dau
    LaunchedEffect(state) {
        val initBright = context.readPreferredBrightness()
        state.currentScreenBrightness = initBright
        activity?.window?.let { win ->
            val attrs = win.attributes
            attrs.screenBrightness = initBright
            win.attributes = attrs
        }
        val (savedSubSize, savedSubCol) = context.readSubtitleAppearance()
        state.subtitleSizeSp = savedSubSize
        state.subtitleTextColor = Color(savedSubCol)

        if (initialVideo != null && state.effectivePlaylist.isNotEmpty()) {
            val startIdx = state.effectivePlaylist.indexOfFirst { it.id == initialVideo.id }.coerceAtLeast(0)
            val startVid = state.effectivePlaylist[startIdx]
            state.currentVideo = startVid
            state.refreshBookmarksForCurrentVideo()
            onVideoChanged(startVid)
            val savedPos = context.readLastPosition(startVid.id)
            val subs = playerManager.playPlaylist(state.effectivePlaylist, startIdx, savedPos)
            state.loadedSubtitles = subs
            state.currentPos = savedPos
            delay(250)
            state.eqBands = playerManager.getEqualizerBands()
        } else if (!streamUrl.isNullOrEmpty()) {
            StreamHistoryManager.ensureInitialized(context)
            playerManager.playStream(streamUrl)
            val savedStreamPos = StreamHistoryManager.getSavedPositionForUrl(streamUrl)
            if (savedStreamPos > 2000L) {
                playerManager.seekToFast(savedStreamPos)
                state.currentPos = savedStreamPos
                state.showTemporaryHint("Ti\u1EBFp t\u1EE5c t\u1EEB ${formatTimeMs(savedStreamPos)}", 1400L)
            }
            state.loadedSubtitles = emptyList()
        }
    }

    // Lang nghe ExoPlayer: Tu chuyen tap + Cap nhat kich thuoc video Doc/Ngang + Dong bo Play/Pause tuc thi
    DisposableEffect(playerManager, state.effectivePlaylist) {
        val initialSize = playerManager.player.videoSize
        if (initialSize.width > 0 && initialSize.height > 0) {
            state.videoWidth = initialSize.width
            state.videoHeight = initialSize.height
            state.videoPixelRatio = initialSize.pixelWidthHeightRatio
        }

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    state.videoWidth = videoSize.width
                    state.videoHeight = videoSize.height
                    state.videoPixelRatio = videoSize.pixelWidthHeightRatio
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    state.currentVideo?.let { finishedVid ->
                        CoroutineScope(Dispatchers.IO).launch { context.saveLastPosition(finishedVid.id, 0L) }
                    }
                    val nextIdx = playerManager.currentIndex + 1
                    if (nextIdx in state.effectivePlaylist.indices) {
                        val nextVid = state.effectivePlaylist[nextIdx]
                        state.resetVideoTransformAndLoop()
                        scope.launch {
                            val savedPos = context.readLastPosition(nextVid.id)
                            val (_, subs) = playerManager.playVideoAtIndex(nextIdx, savedPos)
                            state.currentVideo = nextVid
                            state.refreshBookmarksForCurrentVideo()
                            state.loadedSubtitles = subs
                            onVideoChanged(nextVid)
                            if (!state.isInPipMode) {
                                state.showTemporaryHint("T\u1EF1 ph\u00E1t ti\u1EBFp: ${nextVid.displayName}", 1600L)
                            }
                        }
                    }
                }
            }
        }
        playerManager.player.addListener(listener)
        onDispose { playerManager.player.removeListener(listener) }
    }

    BackHandler {
        if (state.activeModal != ActiveModal.NONE) {
            state.activeModal = ActiveModal.NONE
        } else {
            state.persistCurrentPosition()
            onBack()
        }
    }

    LaunchedEffect(
        state.showControls,
        state.isPlaying,
        state.isDraggingSlider,
        state.activeModal,
        state.showSpeedMenu,
        state.gestureMode
    ) {
        if (state.showControls && state.isPlaying && !state.isDraggingSlider &&
            state.activeModal == ActiveModal.NONE && !state.showSpeedMenu && state.gestureMode == GestureMode.NONE
        ) {
            delay(3500)
            state.showControls = false
        }
    }

    LaunchedEffect(state.sleepTimerMinutes) {
        if (state.sleepTimerMinutes > 0) {
            state.sleepRemainingSec = state.sleepTimerMinutes * 60
            while (state.sleepRemainingSec > 0) {
                delay(1000)
                state.sleepRemainingSec--
            }
            playerManager.pause()
            state.persistCurrentPosition()
            state.sleepTimerMinutes = 0
            state.gestureHint = "\u0110\u00E3 t\u1EA1m d\u1EEBng theo H\u1EB9n gi\u1EDD ng\u1EE7"
        } else {
            state.sleepRemainingSec = 0
        }
    }

    LaunchedEffect(state) {
        var tick = 0
        while (true) {
            state.isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                val sysPip = activity.isInPictureInPictureMode
                if (state.isInPipMode != sysPip) {
                    state.isInPipMode = sysPip
                    PipController.isInPipMode.value = sysPip
                }
            }
            val pos = playerManager.getCurrentPosition()
            val now = System.currentTimeMillis()
            if (!state.isDraggingSlider && state.gestureMode != GestureMode.SEEK && (now - state.lastSeekCompletedTimeMs > 500L)) {
                state.currentPos = pos
            }
            state.duration = playerManager.getDuration().coerceAtLeast(1L)

            val ptA = state.loopPointA
            val ptB = state.loopPointB
            if (ptA != null && ptB != null && ptB > ptA + 500L && pos >= ptB) {
                playerManager.seekToFast(ptA)
                if (!state.isDraggingSlider) state.currentPos = ptA
            }

            if (state.showNerdStats) {
                state.nerdStats = playerManager.getNerdStats()
            }

            tick++
            val activeVid = state.currentVideo
            if (activeVid != null && state.isPlaying && !state.isDraggingSlider && tick % 4 == 0) {
                context.saveLastPosition(activeVid.id, pos)
                WatchExperienceManager.recordPlaybackProgress(context, activeVid, pos, state.duration)
            } else if (activeVid == null && !streamUrl.isNullOrBlank() && state.isPlaying && !state.isDraggingSlider && tick % 8 == 0 && state.duration > 10_000L) {
                StreamHistoryManager.updateStreamProgress(context, streamUrl, pos, state.duration)
            }
            delay(300)
        }
    }

    DisposableEffect(lifecycleOwner, state.backgroundAudioEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activity?.isInPictureInPictureMode == true
            } else false
            state.isInPipMode = inPip

            when (event) {
                Lifecycle.Event.ON_PAUSE -> state.persistCurrentPosition()
                Lifecycle.Event.ON_STOP -> {
                    state.persistCurrentPosition()
                    if (!inPip && !state.backgroundAudioEnabled) playerManager.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            state.persistCurrentPosition()
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(
                    com.videoplayer.app.R.layout.exo_texture_player_view,
                    null
                ) as PlayerView).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    player = playerManager.player
                    useController = false
                    setResizeMode(state.resizeMode)
                }
            },
            update = { view ->
                view.keepScreenOn = state.isPlaying
                view.resizeMode = state.resizeMode

                val colorPaint = buildVideoColorPaint(state.videoSaturation, state.videoContrast, state.videoBrightnessFilter)
                if (colorPaint != null) view.setLayerType(View.LAYER_TYPE_HARDWARE, colorPaint)
                else view.setLayerType(View.LAYER_TYPE_NONE, null)

                view.subtitleView?.apply {
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, state.subtitleSizeSp)
                    setStyle(
                        CaptionStyleCompat(
                            state.subtitleTextColor.toArgb(),
                            android.graphics.Color.argb(140, 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    val r = Rect(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                    if (!r.isEmpty && r != state.playerBoundsRect) {
                        state.playerBoundsRect = r
                    }
                }
                .graphicsLayer(
                    scaleX = if (state.isMirrorFlipped) -state.zoomScale else state.zoomScale,
                    scaleY = state.zoomScale,
                    translationX = state.zoomOffsetX,
                    translationY = state.zoomOffsetY
                )
                .playerTouchGestures(
                    state = state,
                    activity = activity,
                    audioManager = audioManager
                )
        )

        PlayerOverlaysAndControls(
            state = state,
            activity = activity,
            onBack = onBack
        )

        PlayerActiveModals(
            state = state,
            activity = activity,
            onLaunchSubtitlePicker = { subtitlePickerLauncher.launch(arrayOf("*/*")) }
        )
    }
}