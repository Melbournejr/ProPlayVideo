package com.videoplayer.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.media.PlayerManager
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
    val scope = rememberCoroutineScope()
    val palette = LocalGlassPalette.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerManager = remember { PlayerManager(context) }
    val state = remember {
        PlayerStateHolder(
            context = context,
            scope = scope,
            playerManager = playerManager,
            initialVideo = initialVideo,
            playlist = playlist,
            streamUrl = streamUrl,
            onVideoChanged = onVideoChanged,
            onBack = onBack
        )
    }

    LaunchedEffect(Unit) {
        state.initialize(activity)
    }

    LaunchedEffect(state.showControls, state.isPlaying, state.isDraggingSlider, state.activeModal, state.showSpeedMenu, state.gestureMode) {
        if (state.showControls && state.isPlaying && !state.isDraggingSlider && state.activeModal == ActiveModal.NONE && !state.showSpeedMenu && state.gestureMode == GestureMode.NONE) {
            delay(3500)
            state.showControls = false
        }
    }

    DisposableEffect(activity, state.currentVideo) {
        val window = activity?.window
        val isVaultVideo = (state.currentVideo?.id?.startsWith("vault_") == true || state.currentVideo?.path?.startsWith("vault://") == true)
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (isVaultVideo) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner, state.backgroundAudioEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> state.persistCurrentPosition()
                Lifecycle.Event.ON_STOP -> {
                    state.persistCurrentPosition()
                    if (!state.backgroundAudioEnabled) state.playerManager.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.release()
        }
    }

    BackHandler {
        if (state.activeModal != ActiveModal.NONE) {
            state.activeModal = ActiveModal.NONE
        } else {
            state.persistCurrentPosition()
            state.onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerGestureLayer(state = state, activity = activity) {
            AndroidView(
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx).inflate(
                        com.videoplayer.app.R.layout.exo_texture_player_view,
                        null
                    ) as PlayerView).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        keepScreenOn = true
                        player = state.playerManager.player
                        useController = false
                        setResizeMode(state.resizeMode)
                    }
                },
                update = { view ->
                    view.keepScreenOn = state.isPlaying
                    view.resizeMode = state.resizeMode
                    val colorPaint = state.buildVideoColorPaint()
                    view.setLayerType(if (colorPaint != null) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, colorPaint)
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
            )
        }

        state.gestureHint?.let { hint ->
            LiquidGlassPill(active = true, modifier = Modifier.align(Alignment.Center)) {
                Text(hint, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }

        if (state.showNerdStats) {
            state.nerdStats?.let { st ->
                LiquidGlassCard(cornerRadius = 18.dp, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).widthIn(max = 260.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("📊 NERD STATS", color = palette.primaryGlow, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("✕", color = Color.White, modifier = Modifier.padding(2.dp).clickable { state.showNerdStats = false })
                        }
                        Text("• ${st.resolution} (${st.frameRate})", color = Color.White, fontSize = 11.sp)
                        Text("• Video: ${st.videoCodec} • ${st.videoBitrate}", color = Color.White, fontSize = 11.sp)
                        Text("• Drop: ${st.droppedFrames}", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            PlayerModalsLayer(state = state, activity = activity)
        }

        PlayerControlsOverlay(
            state = state,
            activity = activity,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}