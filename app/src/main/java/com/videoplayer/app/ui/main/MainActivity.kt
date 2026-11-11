package com.videoplayer.app.ui.main

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.videoplayer.app.BuildConfig
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.ui.components.*
import com.videoplayer.app.ui.player.PlayerScreen
import com.videoplayer.app.ui.stream.NetworkStreamScreen
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import com.videoplayer.app.update.GitHubRelease
import com.videoplayer.app.update.UpdateManager
import com.videoplayer.app.update.VersionMetadata
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.PermissionHelper
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : FragmentActivity() {

    private val requestStoragePermission = registerForActivityResult(RequestMultiplePermissions()) { }
    private var isCurrentlyInPlayerScreen = false
    private var externalViewIntentUri by mutableStateOf<Uri?>(null)

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
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCurrentlyInPlayerScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            } catch (_: Exception) {}
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
            val frostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.16f)
            val vaultSecState by context.observeVaultSecurityState().collectAsState(initial = VaultSecurityState())
            val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)

            val styleConfig = remember(engineId, subVariantId, themeId, tintId, frostAlpha) {
                buildEffectiveStyleConfig(
                    engineId = engineId,
                    subVariantId = subVariantId,
                    glassThemeId = themeId,
                    glassTintId = tintId,
                    frostAlpha = frostAlpha
                )
            }

            VideoPlayerTheme(styleConfig = styleConfig) {
                var isInPlayer by remember { mutableStateOf(false) }
                var selectedFolder by remember { mutableStateOf<String?>(null) }

                // Trạng thái Kho Ẩn hỗ trợ cả Kho thật (REAL_VAULT) và Kho giả (DECOY_VAULT)
                var vaultAccessMode by remember { mutableStateOf(VaultAccessMode.LOCKED) }
                val isVaultUnlocked = (vaultAccessMode != VaultAccessMode.LOCKED)

                var showTopOverflowMenu by remember { mutableStateOf(false) }
                var showGeneralSettingsModal by remember { mutableStateOf(false) }
                var showVaultOnlySettingsModal by remember { mutableStateOf(false) }
                var showStreamModal by remember { mutableStateOf(false) }

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

                // Biến ghi nhận thời điểm app xuống nền phục vụ Auto-Lock Vault
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

                // ================= AUTO-LOCK VAULT KHI APP VÀO BACKGROUND =================
                DisposableEffect(lifecycleOwner, vaultAccessMode, vaultSecState.autoLockTimeoutSec) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (vaultAccessMode != VaultAccessMode.LOCKED) {
                                    if (vaultSecState.autoLockTimeoutSec == 0) {
                                        // Khóa ngay lập tức khi xuống nền
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
                                        Toast.makeText(context, "🔒 Đã tự động khóa Lưu trữ nội bộ", Toast.LENGTH_SHORT).show()
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
                                    vaultAccessMode = VaultAccessMode.REAL_VAULT
                                },
                                onUsePinFallback = {
                                    showVaultPinDialog = true
                                }
                            )
                        }
                    } else {
                        showVaultPinDialog = true
                    }
                }

                LaunchedEffect(vaultSecState.needsMigration) {
                    if (vaultSecState.needsMigration) {
                        showMigrationDialog = true
                    }
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
                        onVideoChanged = { updatedVid ->
                            lastViewedVideoId = updatedVid.id
                        },
                        onBack = {
                            isInPlayer = false
                            selectedVideo = null
                            streamUrl = null
                        }
                    )
                } else {
                    LiquidAmbientBackground {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LiquidGlassCard(
                                cornerRadius = 24.dp,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedFolder != null || isVaultUnlocked) {
                                        IconButton(onClick = {
                                            if (isVaultUnlocked) vaultAccessMode = VaultAccessMode.LOCKED
                                            else selectedFolder = null
                                        }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                                        }
                                    } else {
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    // Khi ở DECOY_VAULT hay REAL_VAULT đều hiển thị cùng một tiêu đề để không thể phân biệt!
                                    val titleText = when {
                                        isVaultUnlocked -> "📁 Lưu trữ nội bộ"
                                        selectedFolder != null -> selectedFolder!!
                                        else -> "Video Player Pro"
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (isVaultUnlocked) vaultAccessMode = VaultAccessMode.LOCKED
                                                },
                                                onLongClick = {
                                                    triggerStealthVaultAccess()
                                                }
                                            )
                                            .padding(vertical = 2.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isVaultUnlocked) "Chế độ bảo mật riêng tư" else "${ThemeEngineId.values().find { it.id == engineId }?.title ?: "Liquid Glass"} • v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = styleConfig.palette.primaryGlow
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .combinedClickable(
                                                onClick = {
                                                    selectedFolder = null
                                                    vaultAccessMode = VaultAccessMode.LOCKED
                                                },
                                                onLongClick = {
                                                    triggerStealthVaultAccess()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = "Trang chủ",
                                            tint = if (selectedFolder == null && !isVaultUnlocked) styleConfig.palette.primaryGlow else Color.White
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = {
                                                if (isVaultUnlocked) {
                                                    showVaultOnlySettingsModal = true
                                                } else {
                                                    showTopOverflowMenu = true
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isVaultUnlocked) AppIcons.Shield else Icons.Default.MoreVert,
                                                contentDescription = "Tùy chọn",
                                                tint = if (isVaultUnlocked) styleConfig.palette.primaryGlow else Color.White
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showTopOverflowMenu && !isVaultUnlocked,
                                            onDismissRequest = { showTopOverflowMenu = false },
                                            modifier = Modifier.background(styleConfig.palette.deepBgMid)
                                        ) {
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(AppIcons.Palette, null, tint = styleConfig.palette.primaryGlow) },
                                                text = { Text("Tùy chỉnh Giao diện & OTA", color = Color.White, fontWeight = FontWeight.SemiBold) },
                                                onClick = {
                                                    showTopOverflowMenu = false
                                                    showGeneralSettingsModal = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(AppIcons.Stream, null, tint = styleConfig.palette.primaryGlow) },
                                                text = { Text("Phát luồng Stream Mạng", color = Color.White) },
                                                onClick = {
                                                    showTopOverflowMenu = false
                                                    showStreamModal = true
                                                }
                                            )
                                            if (!FileOperationHelper.hasAllFilesAccess()) {
                                                DropdownMenuItem(
                                                    leadingIcon = { Icon(AppIcons.DriveFileMove, null, tint = Color(0xFFFFD54F)) },
                                                    text = { Text("Cấp quyền Di chuyển tệp nhanh", color = Color(0xFFFFD54F)) },
                                                    onClick = {
                                                        showTopOverflowMenu = false
                                                        FileOperationHelper.requestAllFilesAccess(context)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                HomeScreen(
                                    selectedFolder = selectedFolder,
                                    vaultAccessMode = vaultAccessMode,
                                    folderListState = folderListState,
                                    videoListState = activeVideoListState,
                                    lastViewedVideoId = lastViewedVideoId,
                                    onSelectFolder = { folder -> selectedFolder = folder },
                                    onExitVault = { vaultAccessMode = VaultAccessMode.LOCKED },
                                    onOpenVaultPinSettings = {
                                        if (isVaultUnlocked) showVaultOnlySettingsModal = true
                                    },
                                    onVideoClick = { video, playlist ->
                                        selectedVideo = video
                                        lastViewedVideoId = video.id
                                        currentPlaylist = playlist
                                        streamUrl = null
                                        isInPlayer = true
                                    },
                                    onStreamClick = { showStreamModal = true }
                                )
                            }
                        }

                        // Modal 1: Cài đặt Giao diện & OTA bên ngoài
                        if (showGeneralSettingsModal && !isVaultUnlocked) {
                            Dialog(
                                onDismissRequest = { showGeneralSettingsModal = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.86f))
                                        .padding(horizontal = 16.dp, vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = styleConfig.palette.deepBgMid,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 640.dp)
                                            .border(
                                                width = 1.4.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        styleConfig.palette.primaryGlow.copy(alpha = 0.65f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(28.dp)
                                            )
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(styleConfig.palette.deepBgMid)
                                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Settings,
                                                    contentDescription = null,
                                                    tint = styleConfig.palette.primaryGlow,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    text = "Cài đặt Giao diện & OTA",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { showGeneralSettingsModal = false },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            Divider(color = Color.White.copy(alpha = 0.12f))
                                            OtaSettingsScreen(modifier = Modifier.weight(1f, fill = false))
                                        }
                                    }
                                }
                            }
                        }

                        // Modal 2: Cài đặt riêng của Kho Ẩn (Tự động ẩn tùy chọn Decoy khi đang ở DECOY_VAULT)
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

                        // Modal 3: Phát luồng Stream Mạng
                        if (showStreamModal) {
                            Dialog(
                                onDismissRequest = { showStreamModal = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.86f))
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = styleConfig.palette.deepBgMid,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.2.dp, styleConfig.palette.primaryGlow, RoundedCornerShape(28.dp))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(styleConfig.palette.deepBgMid)
                                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(AppIcons.Stream, null, tint = styleConfig.palette.primaryGlow)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Phát Luồng Stream Mạng",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { showStreamModal = false },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            NetworkStreamScreen(
                                                onPlayClick = { url ->
                                                    showStreamModal = false
                                                    selectedVideo = null
                                                    currentPlaylist = emptyList()
                                                    streamUrl = url
                                                    isInPlayer = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Hộp thoại Thiết lập PIN Giả (Decoy PIN) - Chỉ mở được khi đang ở REAL_VAULT
                        if (showConfigureDecoyPinDialog && vaultAccessMode == VaultAccessMode.REAL_VAULT) {
                            ConfigureDecoyPinDialog(
                                vaultSecState = vaultSecState,
                                scope = scope,
                                context = context,
                                onDismiss = { showConfigureDecoyPinDialog = false }
                            )
                        }

                        // Hộp thoại Đổi Mã PIN Thật
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
                                        if (hasExistingPin) "🔑 Thay đổi Mã PIN Lưu trữ" else "🔐 Thiết lập Mã PIN Lưu trữ",
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
                                                label = { Text("Mã PIN hiện tại") },
                                                visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    IconButton(onClick = { showChars = !showChars }) {
                                                        Icon(
                                                            imageVector = if (showChars) AppIcons.VisibilityOff else AppIcons.Visibility,
                                                            contentDescription = "Hiện/Ẩn",
                                                            tint = Color.White
                                                        )
                                                    }
                                                },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedTextField(
                                            value = newPinInput,
                                            onValueChange = { newPinInput = it },
                                            label = { Text("Mã PIN mới (>= 6 ký tự)") },
                                            visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showChars = !showChars }) {
                                                    Icon(
                                                        imageVector = if (showChars) AppIcons.VisibilityOff else AppIcons.Visibility,
                                                        contentDescription = "Hiện/Ẩn",
                                                        tint = Color.White
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = confirmNewPinInput,
                                            onValueChange = { confirmNewPinInput = it },
                                            label = { Text("Nhập lại Mã PIN mới") },
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
                                                Toast.makeText(context, "Mã PIN hiện tại không đúng!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                        }
                                        if (!PinSecurityManager.isValidNewPin(newPinInput)) {
                                            Toast.makeText(context, "Mã PIN mới phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (newPinInput.trim() != confirmNewPinInput.trim()) {
                                            Toast.makeText(context, "Hai ô Mã PIN mới không khớp nhau!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        scope.launch {
                                            val newSalt = PinSecurityManager.generateSaltBase64()
                                            val newHash = PinSecurityManager.hashPinPbkdf2(newPinInput, newSalt)
                                            context.saveHashedVaultPin(newHash, newSalt)
                                            showVaultChangePinDialog = false
                                            Toast.makeText(context, "✅ Đã cập nhật Mã PIN mới thành công!", Toast.LENGTH_LONG).show()
                                        }
                                    }) {
                                        Text("Lưu Mã PIN")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVaultChangePinDialog = false }) { Text("Hủy") }
                                }
                            )
                        }

                        // Hộp thoại Migration
                        if (showMigrationDialog) {
                            var oldPinInput by remember { mutableStateOf("") }
                            var newPinInput by remember { mutableStateOf("") }
                            var showPinChars by remember { mutableStateOf(false) }
                            val hasLegacyPin = !vaultSecState.legacyPlainPin.isNullOrBlank()

                            AlertDialog(
                                onDismissRequest = { if (!isMigratingVault) showMigrationDialog = false },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = {
                                    Text("🛡️ Nâng cấp Lưu trữ Bảo mật", color = Color.White, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Xác nhận mã PIN cũ và đặt mã PIN mới (tối thiểu 6 ký tự):",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (hasLegacyPin) {
                                            OutlinedTextField(
                                                value = oldPinInput,
                                                onValueChange = { oldPinInput = it },
                                                label = { Text("Mã PIN cũ") },
                                                visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedTextField(
                                            value = newPinInput,
                                            onValueChange = { newPinInput = it },
                                            label = { Text("Mã PIN mới (>= 6 ký tự)") },
                                            visualTransformation = if (showPinChars) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { showPinChars = !showPinChars }) {
                                                    Icon(
                                                        imageVector = if (showPinChars) AppIcons.VisibilityOff else AppIcons.Visibility,
                                                        contentDescription = "Hiện/Ẩn",
                                                        tint = Color.White
                                                    )
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
                                                Toast.makeText(context, "Mã PIN cũ không đúng!", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            if (!PinSecurityManager.isValidNewPin(newPinInput)) {
                                                Toast.makeText(context, "Mã PIN mới phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
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
                                                    val toMigrate = allVids.filter { it.id in legacyIds }
                                                    toMigrate.forEach { vid ->
                                                        VaultManager.moveVideoToVault(context, vid)
                                                    }
                                                }
                                                context.clearLegacyVaultDataAfterMigration()
                                                isMigratingVault = false
                                                showMigrationDialog = false
                                                vaultAccessMode = VaultAccessMode.REAL_VAULT
                                            }
                                        }
                                    ) { Text("Lưu & Hoàn tất") }
                                },
                                dismissButton = {
                                    if (!isMigratingVault) {
                                        TextButton(onClick = { showMigrationDialog = false }) { Text("Để sau") }
                                    }
                                }
                            )
                        }

                        // Hộp thoại nhập Mã PIN mở Kho (Hỗ trợ cả PIN Thật và Decoy PIN)
                        if (showVaultPinDialog) {
                            VaultUnlockPinDialog(
                                activity = this@MainActivity,
                                vaultSecState = vaultSecState,
                                scope = scope,
                                context = context,
                                onDismiss = { showVaultPinDialog = false },
                                onUnlocked = { mode ->
                                    vaultAccessMode = mode
                                    showVaultPinDialog = false
                                }
                            )
                        }

                        pendingHttpIntentUrl?.let { httpUrl ->
                            AlertDialog(
                                onDismissRequest = { pendingHttpIntentUrl = null },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = { Text("⚠️ Cảnh báo luồng HTTP không mã hóa", color = Color.White, fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("Liên kết bên ngoài ($httpUrl) sử dụng giao thức HTTP không mã hóa. Bạn có muốn tiếp tục phát không?", color = Color.White)
                                },
                                confirmButton = {
                                    Button(onClick = {
                                        scope.launch {
                                            context.saveHttpWarnAcknowledged(true)
                                            val target = httpUrl
                                            pendingHttpIntentUrl = null
                                            selectedVideo = null
                                            currentPlaylist = emptyList()
                                            streamUrl = target
                                            isInPlayer = true
                                        }
                                    }) { Text("Tiếp tục phát") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingHttpIntentUrl = null }) { Text("Hủy") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun OtaSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    val activeEngineId by context.observeThemeEngineMode().collectAsState(initial = ThemeEngineId.LIQUID_GLASS.id)
    val activeSubVariantId by context.observeThemeSubVariant().collectAsState(initial = "gold_signature")
    val currentThemeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
    val currentTintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
    val currentFrostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.16f)

    // Tráº¡ng thÃ¡i thu gá»n / má»Ÿ rá»™ng menu con cá»§a tá»«ng bá»™ Theme (Máº·c Ä‘á»‹nh Ä‘Ã³ng gá»n Ä‘á»ƒ nhÃ¬n rÃµ cáº£ 6 bá»™ Theme)
    var expandedEngineId by remember { mutableStateOf<String?>(null) }

    val savedRepo by context.readGithubRepo("Melbournejr/ProPlayVideo").collectAsState(initial = "Melbournejr/ProPlayVideo")
    var repoInput by remember(savedRepo) { mutableStateOf(savedRepo) }
    var selectedMirror by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("PhiÃªn báº£n hiá»‡n táº¡i: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var latestMeta by remember { mutableStateOf<VersionMetadata?>(null) }
    var isNewerAvailable by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var speedInfo by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ================= 1. Há»† THá»NG 6 Bá»˜ GIAO DIá»†N (CÃ“ MENU THU Gá»ŒN ACCORDION) =================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.deepBgMid)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Palette, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bá»™ Giao Diá»‡n Chá»§ Äá» (6 Theme Engines)",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Cháº¡m vÃ o bá»™ Theme Ä‘á»ƒ Ã¡p dá»¥ng & má»Ÿ/Ä‘Ã³ng menu tÃ¹y chá»‰nh riÃªng",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA7B8D8)
                    )
                }
            }

            ThemeEngineId.values().forEach { engine ->
                val isSelectedEngine = (activeEngineId == engine.id)
                val isExpanded = (expandedEngineId == engine.id)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelectedEngine) palette.primaryGlow.copy(alpha = 0.14f)
                            else Color.Black.copy(alpha = 0.30f)
                        )
                        .border(
                            width = if (isSelectedEngine) 1.5.dp else 0.8.dp,
                            color = if (isSelectedEngine) palette.primaryGlow else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    // Äáº§u má»¥c cá»§a tá»«ng bá»™ Theme (Báº¥m vÃ o Ä‘á»ƒ chá»n Theme vÃ  Báº­t/Táº¯t Menu thu gá»n)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    context.saveThemeEngineMode(engine.id)
                                    // Náº¿u chÆ°a cÃ³ biáº¿n thá»ƒ con thuá»™c engine nÃ y thÃ¬ tá»± chá»n biáº¿n thá»ƒ Ä‘áº§u tiÃªn
                                    if (engine != ThemeEngineId.LIQUID_GLASS) {
                                        val firstVar = ThemeSubVariants.firstOrNull { it.engineId == engine.id }
                                        val currentMatches = ThemeSubVariants.any { it.engineId == engine.id && it.id == activeSubVariantId }
                                        if (!currentMatches && firstVar != null) {
                                            context.saveThemeSubVariant(firstVar.id)
                                        }
                                    }
                                }
                                expandedEngineId = if (isExpanded) null else engine.id
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = engine.title,
                                    color = if (isSelectedEngine) palette.primaryGlow else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (isSelectedEngine) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "â€¢ Äang dÃ¹ng",
                                        color = Color(0xFF80FFDB),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = engine.subtitle,
                                color = Color(0xFFA7B8D8),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // NÃºt mÅ©i tÃªn bÃ¡o tráº¡ng thÃ¡i Thu gá»n (â–¼) / Äang má»Ÿ (â–²)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) "Thu gá»n â–²" else "TÃ¹y chá»‰nh â–¼",
                                color = if (isSelectedEngine) palette.primaryGlow else Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // ================= MENU CON THU Gá»ŒN Cá»¦A Tá»ªNG Bá»˜ THEME =================
                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(color = Color.White.copy(alpha = 0.12f))

                            if (engine == ThemeEngineId.LIQUID_GLASS) {
                                // Menu thu gá»n dÃ nh riÃªng cho Apple Liquid Glass
                                val pct = ((currentFrostAlpha / 0.45f) * 100).roundToInt().coerceIn(8, 100)
                                Text(
                                    "1. Äá»™ Ä‘á»¥c sÆ°Æ¡ng má» cá»§a kÃ­nh (Frost Opacity): $pct%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primaryGlow,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = currentFrostAlpha,
                                    onValueChange = { newAlpha -> scope.launch { context.saveGlassFrostAlpha(newAlpha) } },
                                    valueRange = 0.04f..0.45f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = palette.primaryGlow,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )

                                Text(
                                    "2. MÃ u phá»§ táº¥m kÃ­nh (Glass Surface Tint):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primaryGlow,
                                    fontWeight = FontWeight.Bold
                                )
                                GlassTintPresets.forEach { tintOpt ->
                                    val isTintSelected = (tintOpt.id == currentTintId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isTintSelected) tintOpt.surfaceColor.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isTintSelected) 1.3.dp else 0.6.dp,
                                                color = if (isTintSelected) tintOpt.rimTint else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { scope.launch { context.saveGlassTint(tintOpt.id) } }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(tintOpt.surfaceColor)
                                                    .border(1.dp, Color.White, CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(tintOpt.name, color = Color.White, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (isTintSelected) {
                                            Text("âœ“", color = tintOpt.rimTint, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "3. TÃ´ng mÃ u Ãnh sÃ¡ng Ná»n (Ambient Glow):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primaryGlow,
                                    fontWeight = FontWeight.Bold
                                )
                                GlassThemePresets.forEach { preset ->
                                    val isGlowSelected = (preset.id == currentThemeId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isGlowSelected) preset.primaryGlow.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isGlowSelected) 1.3.dp else 0.6.dp,
                                                color = if (isGlowSelected) preset.primaryGlow else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { scope.launch { context.saveGlassTheme(preset.id) } }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(preset.primaryGlow, preset.secondaryGlow)))
                                                    .border(1.dp, Color.White, CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(preset.name, color = Color.White, style = MaterialTheme.typography.labelMedium)
                                        }
                                        if (isGlowSelected) {
                                            Text("âœ“", color = preset.primaryGlow, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            } else {
                                // Menu thu gá»n cho 5 Bá»™ Theme má»›i (Brushed Metal, AMOLED, Cyberpunk, Velvet Cinema, Nordic Matte)
                                val subList = ThemeSubVariants.filter { it.engineId == engine.id }
                                Text(
                                    "Chá»n phiÃªn báº£n phá»‘i mÃ u cho ${engine.title}:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primaryGlow,
                                    fontWeight = FontWeight.Bold
                                )
                                subList.forEach { variant ->
                                    val isVarSelected = (isSelectedEngine && activeSubVariantId == variant.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isVarSelected) variant.primaryAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                            .border(
                                                width = if (isVarSelected) 1.4.dp else 0.6.dp,
                                                color = if (isVarSelected) variant.primaryAccent else Color.White.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                scope.launch {
                                                    context.saveThemeEngineMode(engine.id)
                                                    context.saveThemeSubVariant(variant.id)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(variant.primaryAccent, variant.secondaryAccent)))
                                                    .border(1.dp, Color.White, CircleShape)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = variant.name,
                                                color = Color.White,
                                                fontWeight = if (isVarSelected) FontWeight.Bold else FontWeight.Normal,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                        if (isVarSelected) {
                                            Text("âœ“", color = variant.primaryAccent, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ================= 2. TRUNG TÃ‚M Cáº¬P NHáº¬T OTA =================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.deepBgMid)
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.CloudDownload, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cáº­p nháº­t OTA An ToÃ n (SHA-256)",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            LiquidGlassPill(
                active = false,
                onClick = {
                    val freed = updateManager.cleanupOldApks()
                    val freedMb = "%.1f MB".format(freed / (1024f * 1024f))
                    Toast.makeText(context, "ðŸ§¹ ÄÃ£ dá»n sáº¡ch gÃ³i cáº­p nháº­t cÅ© ($freedMb)!", Toast.LENGTH_SHORT).show()
                    statusText = "âœ… ÄÃ£ giáº£i phÃ³ng $freedMb bá»™ nhá»› Ä‘á»‡m!"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ðŸ§¹ Dá»n sáº¡ch gÃ³i APK cÅ© & Cache", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }

            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                label = { Text("GitHub Repo (owner/repo)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LiquidGlassPill(active = selectedMirror == 0, onClick = { selectedMirror = 0 }) {
                    Text("GitHub", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(active = selectedMirror == 1, onClick = { selectedMirror = 1 }) {
                    Text("CDN 1", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                LiquidGlassPill(active = selectedMirror == 2, onClick = { selectedMirror = 2 }) {
                    Text("CDN 2", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }

            LiquidGlassPill(
                active = true,
                onClick = {
                    if (!isDownloading) {
                        if (!updateManager.isValidRepoFormat(repoInput)) {
                            statusText = "âŒ Äá»‹nh dáº¡ng Repo pháº£i lÃ  'owner/repo'!"
                            return@LiquidGlassPill
                        }
                        scope.launch {
                            context.saveGithubRepo(repoInput)
                            statusText = "Äang kiá»ƒm tra tá»« api.github.com..."
                            val result = updateManager.checkLatestRelease(repoInput)
                            result.onSuccess { (release, meta) ->
                                latestRelease = release
                                latestMeta = meta
                                val newer = updateManager.isRemoteVersionCodeNewer(BuildConfig.VERSION_CODE, meta)
                                isNewerAvailable = newer
                                statusText = if (newer) {
                                    "ðŸŽ‰ CÃ³ báº£n má»›i: v${meta.versionName} (${meta.versionCode})"
                                } else {
                                    "âœ… Äang dÃ¹ng báº£n má»›i nháº¥t (${BuildConfig.VERSION_CODE})!"
                                }
                            }.onFailure { err ->
                                statusText = "âŒ Lá»—i: ${err.message}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ðŸ” Kiá»ƒm tra cáº­p nháº­t má»›i", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isNewerAvailable) palette.primaryGlow else Color(0xFF80FFDB),
                fontWeight = FontWeight.SemiBold
            )

            val currentRel = latestRelease
            val currentMeta = latestMeta
            if (currentRel != null && currentMeta != null && isNewerAvailable) {
                val apkAsset = currentRel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset != null) {
                    if (downloadProgress in 0..100) {
                        LinearProgressIndicator(
                            progress = downloadProgress / 100f,
                            color = palette.primaryGlow,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        )
                        Text("Äang táº£i: $downloadProgress% ($speedInfo)", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall)
                    }
                    LiquidGlassPill(
                        active = true,
                        onClick = {
                            if (!isDownloading) {
                                if (!updateManager.canRequestPackageInstalls()) {
                                    updateManager.openManageUnknownAppSourcesSettings()
                                    return@LiquidGlassPill
                                }
                                scope.launch {
                                    isDownloading = true
                                    downloadProgress = 0
                                    val dl = updateManager.downloadAndVerifyApk(
                                        rawUrl = apkAsset.browser_download_url,
                                        expectedSize = apkAsset.size,
                                        expectedSha256 = currentMeta.sha256,
                                        mirrorIndex = selectedMirror
                                    ) { p, spd ->
                                        downloadProgress = p
                                        speedInfo = spd
                                    }
                                    isDownloading = false
                                    dl.onSuccess { verifiedFile ->
                                        statusText = "âœ… ÄÃ£ xÃ¡c thá»±c SHA-256! Äang cÃ i Ä‘áº·t..."
                                        updateManager.installVerifiedApk(verifiedFile)
                                    }.onFailure { err ->
                                        statusText = "âŒ ${err.message}"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isDownloading) "â³ Äang táº£i & xÃ¡c minh..." else "ðŸ›¡ï¸ Táº£i 8 luá»“ng & CÃ i Ä‘áº·t ngay",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
