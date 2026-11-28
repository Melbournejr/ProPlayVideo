package com.videoplayer.app.ui.main

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.network.LanVideoHttpServer
import com.videoplayer.app.network.cloud.CloudStreamProxyServer
import com.videoplayer.app.ui.components.*
import com.videoplayer.app.ui.player.PlayerScreen
import com.videoplayer.app.ui.settings.SettingsScreen
import com.videoplayer.app.ui.stream.NetworkStreamScreen
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import com.videoplayer.app.update.UpdateManager
import com.videoplayer.app.util.PermissionHelper
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.PipController
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class MainTab { LIBRARY, STREAM, SETTINGS }

class MainActivity : FragmentActivity() {

    private val requestStoragePermission = registerForActivityResult(RequestMultiplePermissions()) { }
    private var isCurrentlyInPlayerScreen = false
    private var externalViewIntentUri by mutableStateOf<Uri?>(null)

    override fun onDestroy() {
        super.onDestroy()
        try { LanVideoHttpServer.stopServer() } catch (_: Exception) {}
        try { CloudStreamProxyServer.stopServer() } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingViewIntent(intent)
    }

    private fun handleIncomingViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            if (uri.scheme == "content") {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }
            externalViewIntentUri = uri
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) return cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment ?: "External_Video"
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipController.isInPipMode.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCurrentlyInPlayerScreen) {
            PipController.enterPipOnUserLeaveIfNeeded(this)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { UpdateManager(this).cleanupOldApks() } catch (_: Exception) {}
        handleIncomingViewIntent(intent)

        if (!PermissionHelper.hasStoragePermission(this)) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            requestStoragePermission.launch(perms)
        }

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val scope = rememberCoroutineScope()

            val engineId by context.observeThemeEngineMode().collectAsState(initial = "liquid_glass")
            val subVariantId by context.observeThemeSubVariant().collectAsState(initial = "gold_signature")
            val themeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
            val tintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
            val frostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.22f)
            val reduceTransparency by context.observeReduceTransparency().collectAsState(initial = false)
            val isGlassLightMode by context.observeGlassLightMode().collectAsState(initial = false)
            val vaultSecState by context.observeVaultSecurityState().collectAsState(initial = VaultSecurityState())
            val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)

            val styleConfig = remember(engineId, subVariantId, themeId, tintId, frostAlpha, reduceTransparency, isGlassLightMode) {
                buildEffectiveStyleConfig(
                    engineId = engineId,
                    subVariantId = subVariantId,
                    glassThemeId = themeId,
                    glassTintId = tintId,
                    frostAlpha = frostAlpha,
                    reduceTransparency = reduceTransparency,
                    isLightMode = isGlassLightMode
                )
            }

            VideoPlayerTheme(styleConfig = styleConfig) {
                var activeTab by remember { mutableStateOf(MainTab.LIBRARY) }
                var isInPlayer by remember { mutableStateOf(false) }
                var selectedFolder by remember { mutableStateOf<String?>(null) }
                var isHomeMultiSelect by remember { mutableStateOf(false) }

                var isSearchExpanded by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }

                var vaultAccessMode by remember { mutableStateOf(VaultAccessMode.LOCKED) }
                val isVaultUnlocked = (vaultAccessMode != VaultAccessMode.LOCKED)

                var showVaultOnlySettingsModal by remember { mutableStateOf(false) }
                var showVaultPinDialog by remember { mutableStateOf(false) }
                var showVaultChangePinDialog by remember { mutableStateOf(false) }
                var showConfigureDecoyPinDialog by remember { mutableStateOf(false) }
                var showMigrationDialog by remember { mutableStateOf(false) }
                var isMigratingVault by remember { mutableStateOf(false) }
                var pendingHttpIntentUrl by remember { mutableStateOf<String?>(null) }

                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var lastViewedVideoId by remember { mutableStateOf<String?>(null) }
                var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
                var streamUrl by remember { mutableStateOf<String?>(null) }
                var streamTitle by remember { mutableStateOf<String?>(null) }

                var backgroundEnteredAtMs by remember { mutableLongStateOf(0L) }

                val folderListState = remember { LazyListState() }
                val videoListStates = remember { mutableStateMapOf<String, LazyListState>() }
                val activeVideoListState = remember(selectedFolder, vaultAccessMode) {
                    val key = when (vaultAccessMode) {
                        VaultAccessMode.REAL_VAULT -> "__VAULT_REAL__"
                        VaultAccessMode.DECOY_VAULT -> "__VAULT_DECOY__"
                        VaultAccessMode.LOCKED -> selectedFolder ?: "__ROOT__"
                    }
                    videoListStates.getOrPut(key) { LazyListState() }
                }

                BackHandler(enabled = !isInPlayer && (isSearchExpanded || activeTab != MainTab.LIBRARY)) {
                    when {
                        isSearchExpanded -> {
                            isSearchExpanded = false
                            searchQuery = ""
                        }
                        activeTab != MainTab.LIBRARY -> activeTab = MainTab.LIBRARY
                    }
                }

                // Auto-lock Kho Ẩn khi xuống nền
                DisposableEffect(lifecycleOwner, vaultAccessMode, vaultSecState.autoLockTimeoutSec) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (vaultAccessMode != VaultAccessMode.LOCKED) {
                                    if (vaultSecState.autoLockTimeoutSec == 0) {
                                        vaultAccessMode = VaultAccessMode.LOCKED
                                        showVaultOnlySettingsModal = false
                                        if (isInPlayer && selectedVideo?.id?.startsWith("vault_") == true) {
                                            isInPlayer = false
                                            selectedVideo = null
                                        }
                                    } else {
                                        backgroundEnteredAtMs = System.currentTimeMillis()
                                    }
                                }
                            }
                            Lifecycle.Event.ON_START -> {
                                if (vaultAccessMode != VaultAccessMode.LOCKED && backgroundEnteredAtMs > 0L) {
                                    val elapsedSec = (System.currentTimeMillis() - backgroundEnteredAtMs) / 1000L
                                    backgroundEnteredAtMs = 0L
                                    if (elapsedSec >= vaultSecState.autoLockTimeoutSec) {
                                        vaultAccessMode = VaultAccessMode.LOCKED
                                        showVaultOnlySettingsModal = false
                                        if (isInPlayer && selectedVideo?.id?.startsWith("vault_") == true) {
                                            isInPlayer = false
                                            selectedVideo = null
                                        }
                                        Toast.makeText(context, "\u0110\u00E3 t\u1EF1 \u0111\u1ED9ng kh\u00F3a L\u01B0u tr\u1EEF n\u1ED9i b\u1ED9", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                fun triggerStealthVaultAccess() {
                    if (isVaultUnlocked) {
                        vaultAccessMode = VaultAccessMode.LOCKED
                    } else if (vaultSecState.needsMigration) {
                        showMigrationDialog = true
                    } else if (vaultSecState.isConfigured && vaultSecState.biometricEnabled && PinSecurityManager.canUseBiometric(this@MainActivity)) {
                        val now = System.currentTimeMillis()
                        if (vaultSecState.lockoutUntilMs > now) {
                            showVaultPinDialog = true
                        } else {
                            PinSecurityManager.showBiometricPrompt(
                                activity = this@MainActivity,
                                onSuccess = {
                                    scope.launch { context.recordVaultPinAttempt(true) }
                                    activeTab = MainTab.LIBRARY
                                    vaultAccessMode = VaultAccessMode.REAL_VAULT
                                },
                                onUsePinFallback = { showVaultPinDialog = true }
                            )
                        }
                    } else {
                        showVaultPinDialog = true
                    }
                }

                LaunchedEffect(vaultSecState.needsMigration) {
                    if (vaultSecState.needsMigration) showMigrationDialog = true
                }

                val isPlayingVaultVideo = (isInPlayer && (selectedVideo?.id?.startsWith("vault_") == true || selectedVideo?.path?.startsWith("vault://") == true))
                DisposableEffect(isVaultUnlocked, isPlayingVaultVideo) {
                    if (isVaultUnlocked || isPlayingVaultVideo) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    onDispose {}
                }

                LaunchedEffect(externalViewIntentUri) {
                    val uri = externalViewIntentUri ?: return@LaunchedEffect
                    externalViewIntentUri = null
                    val scheme = uri.scheme?.lowercase() ?: ""
                    if (scheme == "http" && !httpWarnAck) {
                        pendingHttpIntentUrl = uri.toString()
                    } else if (scheme == "http" || scheme == "https") {
                        selectedVideo = null
                        currentPlaylist = emptyList()
                        streamUrl = uri.toString()
                        streamTitle = null
                        isInPlayer = true
                    } else {
                        val name = resolveUriDisplayName(uri)
                        val extItem = VideoItem(
                            id = "ext_${uri.toString().hashCode()}",
                            title = name.substringBeforeLast('.'),
                            displayName = name,
                            path = uri.toString(),
                            duration = 0L,
                            size = 0L,
                            folder = "External",
                            dateAdded = System.currentTimeMillis() / 1000L,
                            mimeType = "video/*"
                        )
                        selectedVideo = extItem
                        currentPlaylist = listOf(extItem)
                        streamUrl = null
                        streamTitle = null
                        isInPlayer = true
                    }
                }

                LaunchedEffect(isInPlayer) {
                    isCurrentlyInPlayerScreen = isInPlayer
                }

                if (isInPlayer) {
                    PlayerScreen(
                        initialVideo = selectedVideo,
                        playlist = currentPlaylist,
                        streamUrl = streamUrl,
                        streamTitle = streamTitle,
                        onVideoChanged = { updatedVid -> lastViewedVideoId = updatedVid.id },
                        onBack = {
                            isInPlayer = false
                            selectedVideo = null
                            streamUrl = null
                            streamTitle = null
                        }
                    )
                } else {
                    LiquidAmbientBackground {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // ================= TOP BAR 1 DÒNG GỌN SẠCH =================
                                LiquidGlassBar(
                                    variant = LiquidGlassVariant.REGULAR,
                                    cornerRadius = 26.dp,
                                    shadowElevation = 14.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSearchExpanded && activeTab == MainTab.LIBRARY) {
                                            OutlinedTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                placeholder = { Text("T\u00ECm video ho\u1EB7c th\u01B0 m\u1EE5c...") },
                                                leadingIcon = { Icon(Icons.Default.Search, null, tint = styleConfig.palette.primaryGlow) },
                                                trailingIcon = {
                                                    IconButton(onClick = {
                                                        searchQuery = ""
                                                        isSearchExpanded = false
                                                    }) {
                                                        Icon(Icons.Default.Close, contentDescription = "\u0110\u00F3ng", tint = Color.White)
                                                    }
                                                },
                                                singleLine = true,
                                                shape = RoundedCornerShape(18.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            if (activeTab == MainTab.LIBRARY && (selectedFolder != null || isVaultUnlocked)) {
                                                LiquidGlassIconButton(
                                                    onClick = {
                                                        if (isVaultUnlocked) vaultAccessMode = VaultAccessMode.LOCKED
                                                        else selectedFolder = null
                                                    },
                                                    size = 36.dp
                                                ) {
                                                    Icon(Icons.Default.ArrowBack, contentDescription = "Quay l\u1EA1i", tint = styleConfig.textPrimary, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(Modifier.width(10.dp))
                                            } else {
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            val headerTitle = when {
                                                isVaultUnlocked -> "L\u01B0u tr\u1EEF n\u1ED9i b\u1ED9"
                                                activeTab == MainTab.STREAM -> "Stream & IPTV"
                                                activeTab == MainTab.SETTINGS -> "C\u00E0i \u0111\u1EB7t"
                                                selectedFolder != null -> selectedFolder!!
                                                else -> "Video Player Pro"
                                            }

                                            // Nhấn giữ tiêu đề để mở Kho Ẩn kín đáo
                                            Text(
                                                text = headerTitle,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = styleConfig.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isVaultUnlocked) vaultAccessMode = VaultAccessMode.LOCKED
                                                        },
                                                        onLongClick = { triggerStealthVaultAccess() }
                                                    )
                                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                                            )

                                            if (activeTab == MainTab.LIBRARY) {
                                                LiquidGlassIconButton(
                                                    onClick = { isSearchExpanded = true },
                                                    size = 36.dp
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = "T\u00ECm ki\u1EBFm", tint = styleConfig.textPrimary, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            if (isVaultUnlocked) {
                                                Spacer(Modifier.width(8.dp))
                                                LiquidGlassIconButton(
                                                    onClick = { showVaultOnlySettingsModal = true },
                                                    size = 36.dp,
                                                    active = true
                                                ) {
                                                    Icon(AppIcons.Shield, contentDescription = "C\u00E0i \u0111\u1EB7t Kho", tint = styleConfig.palette.primaryGlow, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // ================= NỘI DUNG 3 TAB CHÍNH =================
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    when (activeTab) {
                                        MainTab.LIBRARY -> HomeScreen(
                                            selectedFolder = selectedFolder,
                                            searchQuery = searchQuery,
                                            vaultAccessMode = vaultAccessMode,
                                            folderListState = folderListState,
                                            videoListState = activeVideoListState,
                                            lastViewedVideoId = lastViewedVideoId,
                                            onSelectFolder = { folder -> selectedFolder = folder },
                                            onExitVault = { vaultAccessMode = VaultAccessMode.LOCKED },
                                            onOpenVaultPinSettings = {
                                                if (isVaultUnlocked) showVaultOnlySettingsModal = true
                                            },
                                            onMultiSelectChanged = { isHomeMultiSelect = it },
                                            onVideoClick = { video, playlist ->
                                                selectedVideo = video
                                                lastViewedVideoId = video.id
                                                currentPlaylist = playlist
                                                streamUrl = null
                                                streamTitle = null
                                                isInPlayer = true
                                            }
                                        )
                                        MainTab.STREAM -> NetworkStreamScreen(
                                            modifier = Modifier.padding(bottom = 96.dp),
                                            onPlayClick = { url, title ->
                                                selectedVideo = null
                                                currentPlaylist = emptyList()
                                                streamUrl = url
                                                streamTitle = title
                                                isInPlayer = true
                                            }
                                        )
                                        MainTab.SETTINGS -> SettingsScreen()
                                    }
                                }
                            }

                            // ================= FLOATING LIQUID GLASS BOTTOM DOCK (3 TAB) =================
                            val showBottomDock = !isVaultUnlocked && !isHomeMultiSelect && !isSearchExpanded
                            AnimatedVisibility(
                                visible = showBottomDock,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 24.dp, vertical = 14.dp)
                            ) {
                                LiquidGlassBar(
                                    variant = LiquidGlassVariant.REGULAR,
                                    cornerRadius = 32.dp,
                                    shadowElevation = 20.dp,
                                    highlighted = true
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dockItems = listOf(
                                            Triple(MainTab.LIBRARY, "Th\u01B0 vi\u1EC7n", AppIcons.VideoLibrary),
                                            Triple(MainTab.STREAM, "Stream", AppIcons.Stream),
                                            Triple(MainTab.SETTINGS, "C\u00E0i \u0111\u1EB7t", Icons.Default.Settings)
                                        )
                                        dockItems.forEach { (tab, label, icon) ->
                                            val selected = (activeTab == tab)
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(
                                                        if (selected) styleConfig.palette.primaryGlow.copy(alpha = 0.24f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        activeTab = tab
                                                        if (tab != MainTab.LIBRARY) {
                                                            isSearchExpanded = false
                                                            searchQuery = ""
                                                        }
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (selected) styleConfig.palette.primaryGlow else styleConfig.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                if (selected) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = label,
                                                        color = styleConfig.textPrimary,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ================= CÁC HỘP THOẠI BẢO MẬT KHO ẨN & HTTP =================
                        if (showVaultOnlySettingsModal && isVaultUnlocked) {
                            VaultOnlySettingsModal(
                                accessMode = vaultAccessMode,
                                vaultSecState = vaultSecState,
                                scope = scope,
                                context = context,
                                onDismiss = { showVaultOnlySettingsModal = false },
                                onOpenChangeRealPin = { showVaultChangePinDialog = true },
                                onOpenConfigureDecoyPin = { showConfigureDecoyPinDialog = true },
                                onLockAndExit = {
                                    showVaultOnlySettingsModal = false
                                    vaultAccessMode = VaultAccessMode.LOCKED
                                }
                            )
                        }

                        if (showConfigureDecoyPinDialog && vaultAccessMode == VaultAccessMode.REAL_VAULT) {
                            ConfigureDecoyPinDialog(
                                vaultSecState = vaultSecState,
                                scope = scope,
                                context = context,
                                onDismiss = { showConfigureDecoyPinDialog = false }
                            )
                        }

                        if (showVaultChangePinDialog && vaultAccessMode == VaultAccessMode.REAL_VAULT) {
                            var currentPinInput by remember { mutableStateOf("") }
                            var newPinInput by remember { mutableStateOf("") }
                            var confirmNewPinInput by remember { mutableStateOf("") }
                            var showChars by remember { mutableStateOf(false) }
                            val hasExistingPin = vaultSecState.isConfigured

                            AlertDialog(
                                onDismissRequest = { showVaultChangePinDialog = false },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = {
                                    Text(
                                        if (hasExistingPin) "Thay \u0111\u1ED5i M\u00E3 PIN L\u01B0u tr\u1EEF" else "Thi\u1EBFt l\u1EADp M\u00E3 PIN L\u01B0u tr\u1EEF",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (hasExistingPin) {
                                            OutlinedTextField(
                                                value = currentPinInput,
                                                onValueChange = { currentPinInput = it },
                                                label = { Text("M\u00E3 PIN hi\u1EC7n t\u1EA1i") },
                                                visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    IconButton(onClick = { showChars = !showChars }) {
                                                        Icon(if (showChars) AppIcons.VisibilityOff else AppIcons.Visibility, null, tint = Color.White)
                                                    }
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedTextField(
                                            value = newPinInput,
                                            onValueChange = { newPinInput = it },
                                            label = { Text("M\u00E3 PIN m\u1EDBi (>= 6 k\u00FD t\u1EF1)") },
                                            visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showChars = !showChars }) {
                                                    Icon(if (showChars) AppIcons.VisibilityOff else AppIcons.Visibility, null, tint = Color.White)
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = confirmNewPinInput,
                                            onValueChange = { confirmNewPinInput = it },
                                            label = { Text("Nh\u1EADp l\u1EA1i M\u00E3 PIN m\u1EDBi") },
                                            visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        if (hasExistingPin) {
                                            val validOld = PinSecurityManager.verifyPinConstantTime(
                                                inputPin = currentPinInput,
                                                storedHashBase64 = vaultSecState.pinHash ?: "",
                                                storedSaltBase64 = vaultSecState.pinSalt ?: ""
                                            )
                                            if (!validOld) {
                                                Toast.makeText(context, "M\u00E3 PIN hi\u1EC7n t\u1EA1i kh\u00F4ng \u0111\u00FAng", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                        }
                                        if (!PinSecurityManager.isValidNewPin(newPinInput)) {
                                            Toast.makeText(context, "M\u00E3 PIN m\u1EDBi ph\u1EA3i t\u1EEB 6 k\u00FD t\u1EF1 tr\u1EDF l\u00EAn", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (newPinInput.trim() != confirmNewPinInput.trim()) {
                                            Toast.makeText(context, "Hai \u00F4 M\u00E3 PIN m\u1EDBi kh\u00F4ng kh\u1EDBp", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            val newSalt = PinSecurityManager.generateSaltBase64()
                                            val newHash = PinSecurityManager.hashPinPbkdf2(newPinInput, newSalt)
                                            context.saveHashedVaultPin(newHash, newSalt)
                                            showVaultChangePinDialog = false
                                            Toast.makeText(context, "\u0110\u00E3 c\u1EADp nh\u1EADt M\u00E3 PIN m\u1EDBi", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("L\u01B0u") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVaultChangePinDialog = false }) { Text("H\u1EE7y") }
                                }
                            )
                        }

                        if (showMigrationDialog) {
                            var oldPinInput by remember { mutableStateOf("") }
                            var newPinInput by remember { mutableStateOf("") }
                            var showPinChars by remember { mutableStateOf(false) }
                            val hasLegacyPin = !vaultSecState.legacyPlainPin.isNullOrBlank()

                            AlertDialog(
                                onDismissRequest = { if (!isMigratingVault) showMigrationDialog = false },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = { Text("N\u00E2ng c\u1EA5p L\u01B0u tr\u1EEF B\u1EA3o m\u1EADt", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "X\u00E1c nh\u1EADn m\u00E3 PIN c\u0169 v\u00E0 \u0111\u1EB7t m\u00E3 PIN m\u1EDBi (>= 6 k\u00FD t\u1EF1):",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (hasLegacyPin) {
                                            OutlinedTextField(
                                                value = oldPinInput,
                                                onValueChange = { oldPinInput = it },
                                                label = { Text("M\u00E3 PIN c\u0169") },
                                                visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedTextField(
                                            value = newPinInput,
                                            onValueChange = { newPinInput = it },
                                            label = { Text("M\u00E3 PIN m\u1EDBi (>= 6 k\u00FD t\u1EF1)") },
                                            visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPinChars = !showPinChars }) {
                                                    Icon(if (showPinChars) AppIcons.VisibilityOff else AppIcons.Visibility, null, tint = Color.White)
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = !isMigratingVault,
                                        onClick = {
                                            if (hasLegacyPin && oldPinInput.trim() != vaultSecState.legacyPlainPin?.trim()) {
                                                Toast.makeText(context, "M\u00E3 PIN c\u0169 kh\u00F4ng \u0111\u00FAng", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (!PinSecurityManager.isValidNewPin(newPinInput)) {
                                                Toast.makeText(context, "M\u00E3 PIN m\u1EDBi ph\u1EA3i t\u1EEB 6 k\u00FD t\u1EF1 tr\u1EDF l\u00EAn", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isMigratingVault = true
                                                val salt = PinSecurityManager.generateSaltBase64()
                                                val hash = PinSecurityManager.hashPinPbkdf2(newPinInput, salt)
                                                context.saveHashedVaultPin(hash, salt)

                                                val legacyIds = vaultSecState.legacyVaultIds
                                                if (legacyIds.isNotEmpty()) {
                                                    val allVids = VideoRepository(context).getAllVideos().first()
                                                    allVids.filter { it.id in legacyIds }.forEach { vid ->
                                                        VaultManager.moveVideoToVault(context, vid)
                                                    }
                                                }
                                                context.clearLegacyVaultDataAfterMigration()
                                                isMigratingVault = false
                                                showMigrationDialog = false
                                                activeTab = MainTab.LIBRARY
                                                vaultAccessMode = VaultAccessMode.REAL_VAULT
                                            }
                                        }
                                    ) { Text("Ho\u00E0n t\u1EA5t") }
                                },
                                dismissButton = {
                                    if (!isMigratingVault) {
                                        TextButton(onClick = { showMigrationDialog = false }) { Text("\u0110\u1EC3 sau") }
                                    }
                                }
                            )
                        }

                        if (showVaultPinDialog) {
                            VaultUnlockPinDialog(
                                activity = this@MainActivity,
                                vaultSecState = vaultSecState,
                                scope = scope,
                                context = context,
                                onDismiss = { showVaultPinDialog = false },
                                onUnlocked = { mode ->
                                    activeTab = MainTab.LIBRARY
                                    vaultAccessMode = mode
                                    showVaultPinDialog = false
                                }
                            )
                        }

                        pendingHttpIntentUrl?.let { httpUrl ->
                            AlertDialog(
                                onDismissRequest = { pendingHttpIntentUrl = null },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = { Text("C\u1EA3nh b\u00E1o lu\u1ED3ng HTTP kh\u00F4ng m\u00E3 h\u00F3a", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = { Text("Li\u00EAn k\u1EBFt ($httpUrl) s\u1EED d\u1EE5ng HTTP kh\u00F4ng m\u00E3 h\u00F3a. Ti\u1EBFp t\u1EE5c ph\u00E1t?", color = Color.White) },
                                confirmButton = {
                                    Button(onClick = {
                                        scope.launch {
                                            context.saveHttpWarnAcknowledged(true)
                                            val target = httpUrl
                                            pendingHttpIntentUrl = null
                                            selectedVideo = null
                                            currentPlaylist = emptyList()
                                            streamUrl = target
                                            streamTitle = null
                                            isInPlayer = true
                                        }
                                    }) { Text("Ti\u1EBFp t\u1EE5c") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingHttpIntentUrl = null }) { Text("H\u1EE7y") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}