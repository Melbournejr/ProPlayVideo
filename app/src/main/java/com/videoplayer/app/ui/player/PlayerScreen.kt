package com.videoplayer.app.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * PlayerScreen đã được Refactor gọn sạch:
 * Chỉ đảm nhiệm Orchestration (Lifecycle, SystemUI, ExoPlayer Listener) và kết nối với PlayerStateHolder.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    initialVideo: VideoItem?,
    playlist: List<VideoItem>,
    streamUrl: String?,
    onVideoChanged: (VideoItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { PlayerManager(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val state = remember(initialVideo, playlist, streamUrl) {
        PlayerStateHolder(
            context = context,
            scope = scope,
            playerManager = playerManager,
            initialVideo = initialVideo,
            rawPlaylist = playlist,
            streamUrl = streamUrl,
            onVideoChangedCallback = onVideoChanged
        )
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
                state.showTemporaryHint("💬 Đã nạp phụ đề: $fileName", 1600L)
            }
        }
    }

    // Quản lý System Bars & FLAG_SECURE khi phát video thuộc Kho Ẩn
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Khởi tạo phát video ban đầu và đồng bộ độ sáng / kiểu dáng phụ đề
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
            onVideoChanged(startVid)
            val savedPos = context.readLastPosition(startVid.id)
            val subs = playerManager.playPlaylist(state.effectivePlaylist, startIdx, savedPos)
            state.loadedSubtitles = subs
            state.currentPos = savedPos
            delay(250)
            state.eqBands = playerManager.getEqualizerBands()
        } else if (!streamUrl.isNullOrEmpty()) {
            playerManager.playStream(streamUrl)
            state.loadedSubtitles = emptyList()
        }
    }

    // Tự động chuyển tập tiếp theo khi kết thúc video hiện tại
    DisposableEffect(playerManager, state.effectivePlaylist) {
        val listener = object : Player.Listener {
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
                            state.loadedSubtitles = subs
                            onVideoChanged(nextVid)
                            state.showTemporaryHint("⏭️ Tự phát tiếp: ${nextVid.displayName}", 1600L)
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

    // Tự động ẩn thanh điều khiển sau 3.5 giây khi video đang phát
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

    // Bộ đếm Hẹn giờ ngủ
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
            state.gestureHint = "💤 Đã tạm dừng theo Hẹn giờ ngủ"
        } else {
            state.sleepRemainingSec = 0
        }
    }

    // Vòng lặp đồng bộ tiến trình phát & kiểm tra A-B Loop
    LaunchedEffect(state) {
        var tick = 0
        while (true) {
            state.isPlaying = playerManager.isPlaying()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                state.isInPipMode = activity.isInPictureInPictureMode
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
        // Tầng 1: Bề mặt Video + Lớp Cử chỉ Cảm ứng
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

        // Tầng 2: Lớp phủ HUD, Nerd Stats & Thanh Điều Khiển
        PlayerOverlaysAndControls(
            state = state,
            activity = activity,
            onBack = onBack
        )

        // Tầng 3: Các Bảng Modal mở rộng (Pro Studio & Tiện Ích/Phụ đề)
        PlayerActiveModals(
            state = state,
            activity = activity,
            onLaunchSubtitlePicker = { subtitlePickerLauncher.launch(arrayOf("*/*")) }
        )
    }
}