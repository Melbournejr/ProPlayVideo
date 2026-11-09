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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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
            val scope = rememberCoroutineScope()
            val themeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
            val tintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
            val frostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.16f)
            val vaultSecState by context.observeVaultSecurityState().collectAsState(initial = VaultSecurityState())
            val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)

            val styleConfig = remember(themeId, tintId, frostAlpha) {
                GlassStyleConfig(
                    palette = getGlassPaletteById(themeId),
                    tint = getGlassTintById(tintId),
                    frostAlpha = frostAlpha
                )
            }

            VideoPlayerTheme(styleConfig = styleConfig) {
                var screen by remember { mutableStateOf(Screen.Home) }
                var selectedFolder by remember { mutableStateOf<String?>(null) }
                var isVaultUnlocked by remember { mutableStateOf(false) }
                var showVaultPinDialog by remember { mutableStateOf(false) }
                var showMigrationDialog by remember { mutableStateOf(false) }
                var isMigratingVault by remember { mutableStateOf(false) }
                var pendingHttpIntentUrl by remember { mutableStateOf<String?>(null) }

                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var lastViewedVideoId by remember { mutableStateOf<String?>(null) }
                var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
                var streamUrl by remember { mutableStateOf<String?>(null) }

                val folderListState = remember { LazyListState() }
                val videoListStates = remember { mutableStateMapOf<String, LazyListState>() }
                val activeVideoListState = remember(selectedFolder, isVaultUnlocked) {
                    val key = if (isVaultUnlocked) "__VAULT__" else (selectedFolder ?: "__ROOT__")
                    videoListStates.getOrPut(key) { LazyListState() }
                }

                // Bước 2: Tự động phát hiện dữ liệu Vault cũ (ID và PIN thô) khi mở app lần đầu sau cập nhật
                LaunchedEffect(vaultSecState.needsMigration) {
                    if (vaultSecState.needsMigration) {
                        showMigrationDialog = true
                    }
                }

                // Bước 2: Đặt FLAG_SECURE cho các màn hình Vault và Player đang phát video từ Vault
                val isPlayingVaultVideo = (screen == Screen.Player && selectedVideo?.path?.startsWith("vault://") == true)
                DisposableEffect(isVaultUnlocked, isPlayingVaultVideo) {
                    if (isVaultUnlocked || isPlayingVaultVideo) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    onDispose {}
                }

                // Bước 5: Xử lý mở video trực tiếp từ ACTION_VIEW (File Manager / Browser)
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
                        screen = Screen.Player
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
                        screen = Screen.Player
                    }
                }

                LaunchedEffect(screen) {
                    isCurrentlyInPlayerScreen = (screen == Screen.Player)
                }

                if (screen == Screen.Player) {
                    PlayerScreen(
                        initialVideo = selectedVideo,
                        playlist = currentPlaylist,
                        streamUrl = streamUrl,
                        onVideoChanged = { updatedVid ->
                            lastViewedVideoId = updatedVid.id
                        },
                        onBack = {
                            screen = Screen.Home
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
                                    if (screen == Screen.Home && (selectedFolder != null || isVaultUnlocked)) {
                                        IconButton(onClick = {
                                            if (isVaultUnlocked) isVaultUnlocked = false
                                            else selectedFolder = null
                                        }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                                        }
                                    } else if (screen != Screen.Home) {
                                        IconButton(onClick = { screen = Screen.Home }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                                        }
                                    } else {
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    val titleText = when {
                                        screen == Screen.Home && isVaultUnlocked -> "🔒 Kho AES-256 Riêng Tư"
                                        screen == Screen.Home && selectedFolder != null -> selectedFolder!!
                                        screen == Screen.Stream -> "Stream Mạng"
                                        screen == Screen.Settings -> "Tùy biến Kính & OTA"
                                        else -> "Video Player Pro"
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = titleText,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Apple Liquid Glass • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = styleConfig.palette.primaryGlow
                                        )
                                    }

                                    // Nút Mở Kho Video Riêng Tư (Ưu tiên BiometricPrompt nếu đã cài PIN, có fallback PIN)
                                    IconButton(onClick = {
                                        if (isVaultUnlocked) {
                                            isVaultUnlocked = false
                                        } else if (vaultSecState.needsMigration) {
                                            showMigrationDialog = true
                                        } else if (vaultSecState.isConfigured && PinSecurityManager.canUseBiometric(this@MainActivity)) {
                                            val now = System.currentTimeMillis()
                                            if (vaultSecState.lockoutUntilMs > now) {
                                                showVaultPinDialog = true
                                            } else {
                                                PinSecurityManager.showBiometricPrompt(
                                                    activity = this@MainActivity,
                                                    onSuccess = {
                                                        scope.launch { context.recordVaultPinAttempt(true) }
                                                        isVaultUnlocked = true
                                                        screen = Screen.Home
                                                    },
                                                    onUsePinFallback = {
                                                        showVaultPinDialog = true
                                                    }
                                                )
                                            }
                                        } else {
                                            showVaultPinDialog = true
                                        }
                                    }) {
                                        Icon(
                                            if (isVaultUnlocked) AppIcons.LockOpen else Icons.Default.Lock,
                                            contentDescription = "Kho Riêng Tư",
                                            tint = if (isVaultUnlocked) styleConfig.palette.primaryGlow else Color.White
                                        )
                                    }

                                    IconButton(onClick = {
                                        screen = Screen.Home
                                        selectedFolder = null
                                        isVaultUnlocked = false
                                    }) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = "Thư mục gốc",
                                            tint = if (screen == Screen.Home && selectedFolder == null && !isVaultUnlocked) styleConfig.palette.primaryGlow else Color.White
                                        )
                                    }

                                    IconButton(onClick = { screen = Screen.Settings }) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = "Cài đặt & OTA",
                                            tint = if (screen == Screen.Settings) styleConfig.palette.primaryGlow else Color.White
                                        )
                                    }
                                }
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (screen) {
                                    Screen.Home -> HomeScreen(
                                        selectedFolder = selectedFolder,
                                        isVaultMode = isVaultUnlocked,
                                        folderListState = folderListState,
                                        videoListState = activeVideoListState,
                                        lastViewedVideoId = lastViewedVideoId,
                                        onSelectFolder = { folder -> selectedFolder = folder },
                                        onExitVault = { isVaultUnlocked = false },
                                        onVideoClick = { video, playlist ->
                                            selectedVideo = video
                                            lastViewedVideoId = video.id
                                            currentPlaylist = playlist
                                            streamUrl = null
                                            screen = Screen.Player
                                        },
                                        onStreamClick = { screen = Screen.Stream }
                                    )
                                    Screen.Stream -> NetworkStreamScreen(
                                        onPlayClick = { url ->
                                            selectedVideo = null
                                            currentPlaylist = emptyList()
                                            streamUrl = url
                                            screen = Screen.Player
                                        }
                                    )
                                    Screen.Settings -> OtaSettingsScreen()
                                    else -> {}
                                }
                            }
                        }

                        // ================= HỘP THOẠI MIGRATION CHO NGƯỜI DÙNG CŨ (BƯỚC 2) =================
                        if (showMigrationDialog) {
                            var oldPinInput by remember { mutableStateOf("") }
                            var newPinInput by remember { mutableStateOf("") }
                            val hasLegacyPin = !vaultSecState.legacyPlainPin.isNullOrBlank()

                            AlertDialog(
                                onDismissRequest = { if (!isMigratingVault) showMigrationDialog = false },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = {
                                    Text("🛡️ Nâng cấp Bảo mật Kho Riêng Tư (AES-256)", color = Color.White, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Phát hiện ${vaultSecState.legacyVaultIds.size} video trong Kho phiên bản cũ. Hãy xác nhận mã PIN cũ và đặt mã PIN mới (tối thiểu 6 ký tự) để băm PBKDF2 và mã hóa AES-256-GCM toàn bộ video của bạn:",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (hasLegacyPin) {
                                            OutlinedTextField(
                                                value = oldPinInput,
                                                onValueChange = { oldPinInput = it },
                                                label = { Text("Mã PIN cũ của bạn") },
                                                visualTransformation = PasswordVisualTransformation(),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedTextField(
                                            value = newPinInput,
                                            onValueChange = { newPinInput = it },
                                            label = { Text("Mã PIN mới (Tối thiểu 6 ký tự)") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (isMigratingVault) {
                                            LinearProgressIndicator(color = styleConfig.palette.primaryGlow, modifier = Modifier.fillMaxWidth())
                                            Text("Đang mã hóa AES-256-GCM các video cũ...", color = styleConfig.palette.primaryGlow, style = MaterialTheme.typography.labelSmall)
                                        }
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
                                                isVaultUnlocked = true
                                                Toast.makeText(context, "✅ Đã nâng cấp mã hóa AES-256-GCM hoàn tất!", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    ) {
                                        Text("Nâng cấp & Mã hóa")
                                    }
                                },
                                dismissButton = {
                                    if (!isMigratingVault) {
                                        TextButton(onClick = { showMigrationDialog = false }) { Text("Để sau") }
                                    }
                                }
                            )
                        }

                        // ================= HỘP THOẠI MÃ PIN PBKDF2 + KHÓA TĂNG DẦN (BƯỚC 2) =================
                        if (showVaultPinDialog) {
                            var pinInput by remember { mutableStateOf("") }
                            val isSettingNewPin = !vaultSecState.isConfigured
                            var remainingLockSec by remember { mutableLongStateOf(0L) }

                            LaunchedEffect(vaultSecState.lockoutUntilMs) {
                                while (true) {
                                    val rem = ((vaultSecState.lockoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                                    remainingLockSec = rem
                                    if (rem <= 0L) break
                                    delay(1000L)
                                }
                            }

                            AlertDialog(
                                onDismissRequest = { showVaultPinDialog = false },
                                containerColor = styleConfig.palette.deepBgMid,
                                title = {
                                    Text(
                                        if (isSettingNewPin) "🔐 Thiết lập Mã PIN Kho AES-256" else "🔒 Mở Kho Video Riêng Tư",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (remainingLockSec > 0L) {
                                            Text(
                                                "⏳ Bạn đã nhập sai quá ${vaultSecState.failedAttempts} lần. Vui lòng thử lại sau ${remainingLockSec} giây.",
                                                color = Color(0xFFFF5C8A),
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                if (isSettingNewPin) "Nhập mã PIN (tối thiểu 6 ký tự, băm PBKDF2-HMAC-SHA256 120.000 vòng):"
                                                else "Nhập mã PIN của bạn để giải mã xem Kho Riêng Tư:",
                                                color = Color.White
                                            )
                                        }
                                        OutlinedTextField(
                                            value = pinInput,
                                            onValueChange = { pinInput = it },
                                            label = { Text("Mã PIN (>= 6 ký tự)") },
                                            enabled = remainingLockSec <= 0L,
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (!isSettingNewPin && PinSecurityManager.canUseBiometric(this@MainActivity) && remainingLockSec <= 0L) {
                                            TextButton(onClick = {
                                                showVaultPinDialog = false
                                                PinSecurityManager.showBiometricPrompt(
                                                    activity = this@MainActivity,
                                                    onSuccess = {
                                                        scope.launch { context.recordVaultPinAttempt(true) }
                                                        isVaultUnlocked = true
                                                        screen = Screen.Home
                                                    },
                                                    onUsePinFallback = { showVaultPinDialog = true }
                                                )
                                            }) {
                                                Text("👆 Dùng Vân tay / Khuôn mặt (Biometrics)", color = styleConfig.palette.primaryGlow)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        enabled = remainingLockSec <= 0L,
                                        onClick = {
                                            if (isSettingNewPin) {
                                                if (PinSecurityManager.isValidNewPin(pinInput)) {
                                                    scope.launch {
                                                        val salt = PinSecurityManager.generateSaltBase64()
                                                        val hash = PinSecurityManager.hashPinPbkdf2(pinInput, salt)
                                                        context.saveHashedVaultPin(hash, salt)
                                                        isVaultUnlocked = true
                                                        screen = Screen.Home
                                                        showVaultPinDialog = false
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Mã PIN bắt buộc tối thiểu 6 ký tự!", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                val ok = PinSecurityManager.verifyPinConstantTime(
                                                    inputPin = pinInput,
                                                    storedHashBase64 = vaultSecState.pinHash ?: "",
                                                    storedSaltBase64 = vaultSecState.pinSalt ?: ""
                                                )
                                                if (ok) {
                                                    scope.launch {
                                                        context.recordVaultPinAttempt(true)
                                                        isVaultUnlocked = true
                                                        screen = Screen.Home
                                                        showVaultPinDialog = false
                                                    }
                                                } else {
                                                    val nextFail = vaultSecState.failedAttempts + 1
                                                    val lockMs = PinSecurityManager.computeLockoutDurationMs(nextFail)
                                                    scope.launch {
                                                        context.recordVaultPinAttempt(false, lockMs)
                                                    }
                                                    val msg = if (lockMs > 0L) {
                                                        "Sai mã PIN ($nextFail lần)! Tạm khóa ${lockMs / 1000} giây."
                                                    } else {
                                                        "Sai mã PIN! (Lần $nextFail/5)"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (isSettingNewPin) "Lưu & Mở Kho" else "Mở Kho")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVaultPinDialog = false }) { Text("Hủy") }
                                }
                            )
                        }

                        // Hộp thoại cảnh báo HTTP Cleartext khi mở link http:// từ trình duyệt bên ngoài (Bước 4 & 5)
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
                                            screen = Screen.Player
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

    private enum class Screen { Home, Player, Stream, Settings }
}

@Composable
fun OtaSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    val currentThemeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
    val currentTintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
    val currentFrostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.16f)

    val savedRepo by context.readGithubRepo("Melbournejr/ProPlayVideo").collectAsState(initial = "Melbournejr/ProPlayVideo")
    var repoInput by remember(savedRepo) { mutableStateOf(savedRepo) }
    var selectedMirror by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("Phiên bản hiện tại: v${BuildConfig.VERSION_NAME} (versionCode: ${BuildConfig.VERSION_CODE})") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var latestMeta by remember { mutableStateOf<VersionMetadata?>(null) }
    var isNewerAvailable by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var speedInfo by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. ĐỘ MỜ SƯƠNG & MÀU PHỦ BỀ MẶT KÍNH
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.BlurOn, contentDescription = null, tint = palette.primaryGlow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Chất liệu & Màu sắc Tấm Kính (Glass Material)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                val pct = ((currentFrostAlpha / 0.45f) * 100).roundToInt().coerceIn(8, 100)
                Text("Độ đục sương mờ của kính (Frost Opacity): $pct%", style = MaterialTheme.typography.bodySmall, color = Color.White)
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

                GlassTintPresets.forEach { tintOpt ->
                    val isSelected = (tintOpt.id == currentTintId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tintOpt.surfaceColor.copy(alpha = if (isSelected) 0.28f else 0.10f))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                color = if (isSelected) tintOpt.rimTint else Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { scope.launch { context.saveGlassTint(tintOpt.id) } }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(tintOpt.surfaceColor)
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = tintOpt.name,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (isSelected) {
                            Text("✓", color = tintOpt.rimTint, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // 2. BẢNG CHỌN TÔNG MÀU ÁNH SÁNG NỀN
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Palette, contentDescription = null, tint = palette.primaryGlow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tông màu Ánh sáng Nền (Ambient Glow)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                GlassThemePresets.forEach { preset ->
                    val isSelected = (preset.id == currentThemeId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Brush.horizontalGradient(
                                    listOf(preset.primaryGlow.copy(alpha = 0.28f), preset.secondaryGlow.copy(alpha = 0.28f))
                                ) else Brush.horizontalGradient(
                                    listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.03f))
                                )
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.8.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(preset.primaryGlow, preset.secondaryGlow, preset.tertiaryGlow)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { scope.launch { context.saveGlassTheme(preset.id) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(preset.primaryGlow, preset.secondaryGlow, preset.tertiaryGlow)))
                                    .border(1.dp, Color.White, CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = preset.name,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (isSelected) {
                            Text("Đang dùng ✓", color = preset.primaryGlow, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. TRUNG TÂM CẬP NHẬT OTA AN TOÀN (SHA-256 + VERSIONCODE + CERT VERIFY)
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Cập nhật OTA An Toàn (SHA-256 & Chữ ký số)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                LiquidGlassPill(
                    active = false,
                    onClick = {
                        val freed = updateManager.cleanupOldApks()
                        val freedMb = "%.1f MB".format(freed / (1024f * 1024f))
                        Toast.makeText(context, "🧹 Đã dọn sạch gói cập nhật cũ ($freedMb)!", Toast.LENGTH_SHORT).show()
                        statusText = "✅ Đã giải phóng $freedMb dữ liệu gói cài đặt cũ!"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧹 Dọn sạch gói APK cũ & Bộ nhớ đệm", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repository (owner/repo)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Chọn tuyến máy chủ tải APK (Hash SHA-256 luôn lấy trực tiếp từ api.github.com):", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA7B8D8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassPill(active = selectedMirror == 0, onClick = { selectedMirror = 0 }) {
                        Text("GitHub Gốc", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    LiquidGlassPill(active = selectedMirror == 1, onClick = { selectedMirror = 1 }) {
                        Text("CDN 1 (ghfast)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    LiquidGlassPill(active = selectedMirror == 2, onClick = { selectedMirror = 2 }) {
                        Text("CDN 2 (ghproxy)", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }

                LiquidGlassPill(
                    active = true,
                    onClick = {
                        if (!isDownloading) {
                            if (!updateManager.isValidRepoFormat(repoInput)) {
                                statusText = "❌ Lỗi: Tên Repo chỉ được phép có dạng 'owner/repo' hợp lệ!"
                                return@LiquidGlassPill
                            }
                            scope.launch {
                                context.saveGithubRepo(repoInput)
                                statusText = "Đang kiểm tra versionCode & SHA-256 từ api.github.com..."
                                val result = updateManager.checkLatestRelease(repoInput)
                                result.onSuccess { (release, meta) ->
                                    latestRelease = release
                                    latestMeta = meta
                                    val newer = updateManager.isRemoteVersionCodeNewer(BuildConfig.VERSION_CODE, meta)
                                    isNewerAvailable = newer
                                    statusText = if (newer) {
                                        "🎉 Có bản mới: v${meta.versionName} (versionCode ${meta.versionCode} > ${BuildConfig.VERSION_CODE})\n🔒 SHA-256: ${meta.sha256.take(16)}..."
                                    } else {
                                        "✅ Bạn đang dùng bản mới nhất (versionCode: ${BuildConfig.VERSION_CODE})!"
                                    }
                                }.onFailure { err ->
                                    statusText = "❌ Lỗi: ${err.message}"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 Kiểm tra bản cập nhật mới", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isNewerAvailable) palette.primaryGlow else Color(0xFF80FFDB),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val currentRel = latestRelease
        val currentMeta = latestMeta
        if (currentRel != null && currentMeta != null && isNewerAvailable) {
            val apkAsset = currentRel.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset != null) {
                val sizeMb = "%.2f MB".format(apkAsset.size / (1024f * 1024f))
                LiquidGlassCard(
                    cornerRadius = 24.dp,
                    tintColor = palette.primaryGlow,
                    highlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "📦 ${apkAsset.name} ($sizeMb) • Code: ${currentMeta.versionCode}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "SHA-256: ${currentMeta.sha256}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA7B8D8)
                        )
                        if (downloadProgress in 0..100) {
                            LinearProgressIndicator(
                                progress = downloadProgress / 100f,
                                color = palette.primaryGlow,
                                trackColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Text("Đang tải 8 luồng: $downloadProgress% ($speedInfo)", color = palette.primaryGlow)
                        }
                        LiquidGlassPill(
                            active = true,
                            onClick = {
                                if (!isDownloading) {
                                    if (!updateManager.canRequestPackageInstalls()) {
                                        Toast.makeText(context, "Vui lòng bật quyền 'Cài đặt ứng dụng không rõ nguồn gốc' cho Video Player Pro!", Toast.LENGTH_LONG).show()
                                        updateManager.openManageUnknownAppSourcesSettings()
                                        return@LiquidGlassPill
                                    }
                                    scope.launch {
                                        isDownloading = true
                                        downloadProgress = 0
                                        speedInfo = "Đang kết nối HTTPS..."
                                        statusText = "Đang tải 8 luồng & xác minh SHA-256 + Chữ ký số..."
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
                                            statusText = "✅ Xác thực SHA-256 & Chữ ký APK thành công! Đang mở trình cài đặt..."
                                            updateManager.installVerifiedApk(verifiedFile)
                                        }.onFailure { err ->
                                            statusText = "❌ ${err.message}"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isDownloading) "⏳ Đang tải & xác minh..." else "🛡️ Tải 8 luồng, Xác minh SHA-256 & Cài đặt",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}