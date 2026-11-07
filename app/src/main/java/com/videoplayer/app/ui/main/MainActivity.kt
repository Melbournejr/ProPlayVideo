package com.videoplayer.app.ui.main

import android.Manifest
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.BuildConfig
import com.videoplayer.app.data.local.*
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.ui.components.*
import com.videoplayer.app.ui.player.PlayerScreen
import com.videoplayer.app.ui.stream.NetworkStreamScreen
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import com.videoplayer.app.update.GitHubRelease
import com.videoplayer.app.update.UpdateManager
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.PermissionHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val requestStoragePermission = registerForActivityResult(RequestMultiplePermissions()) { }
    private var isCurrentlyInPlayerScreen = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isCurrentlyInPlayerScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
            val themeId by context.observeGlassTheme().collectAsState(initial = "apple_ice")
            val tintId by context.observeGlassTint().collectAsState(initial = "crystal_white")
            val frostAlpha by context.observeGlassFrostAlpha().collectAsState(initial = 0.16f)

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
                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var lastViewedVideoId by remember { mutableStateOf<String?>(null) }
                var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
                var streamUrl by remember { mutableStateOf<String?>(null) }

                val folderListState = remember { LazyListState() }
                val videoListStates = remember { mutableStateMapOf<String, LazyListState>() }
                val activeVideoListState = remember(selectedFolder) {
                    val key = selectedFolder ?: "__ROOT__"
                    videoListStates.getOrPut(key) { LazyListState() }
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
                                    if (screen == Screen.Home && selectedFolder != null) {
                                        IconButton(onClick = { selectedFolder = null }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại thư mục", tint = Color.White)
                                        }
                                    } else if (screen != Screen.Home) {
                                        IconButton(onClick = { screen = Screen.Home }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                                        }
                                    } else {
                                        Spacer(Modifier.width(8.dp))
                                    }

                                    val titleText = when {
                                        screen == Screen.Home && selectedFolder != null -> selectedFolder!!
                                        screen == Screen.Stream -> "Stream Mạng"
                                        screen == Screen.Settings -> "Tùy biến Kính & OTA"
                                        else -> "ProPlayVideo"
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
                                            text = "Apple Liquid Glass • v${BuildConfig.VERSION_NAME}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = styleConfig.palette.primaryGlow
                                        )
                                    }

                                    IconButton(onClick = {
                                        screen = Screen.Home
                                        selectedFolder = null
                                    }) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = "Thư mục gốc",
                                            tint = if (screen == Screen.Home && selectedFolder == null) styleConfig.palette.primaryGlow else Color.White
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
                                        folderListState = folderListState,
                                        videoListState = activeVideoListState,
                                        lastViewedVideoId = lastViewedVideoId,
                                        onSelectFolder = { folder -> selectedFolder = folder },
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
    var statusText by remember { mutableStateOf("Phiên bản đang dùng: v${BuildConfig.VERSION_NAME}") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var isNewerAvailable by remember { mutableStateOf(false) }
    var forceShowReinstall by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var speedInfo by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ================= 1. ĐỘ MỜ SƯƠNG & MÀU PHỦ BỀ MẶT KÍNH =================
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BlurOn, contentDescription = null, tint = palette.primaryGlow)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Chất liệu & Màu sắc Tấm Kính (Glass Material)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                val pct = ((currentFrostAlpha / 0.45f) * 100).roundToInt().coerceIn(8, 100)
                Text(
                    "Độ đục sương mờ của kính (Frost Opacity): $pct%",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                Slider(
                    value = currentFrostAlpha,
                    onValueChange = { newAlpha ->
                        scope.launch { context.saveGlassFrostAlpha(newAlpha) }
                    },
                    valueRange = 0.04f..0.45f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = palette.primaryGlow,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Text(
                    "Chọn màu nhuộm trực tiếp lên mặt kính (Glass Tint):",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
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
                            .clickable {
                                scope.launch { context.saveGlassTint(tintOpt.id) }
                            }
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

        // ================= 2. BẢNG CHỌN TÔNG MÀU ÁNH SÁNG NỀN & GRADIENT =================
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = palette.primaryGlow)
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
                            .clickable {
                                scope.launch { context.saveGlassTheme(preset.id) }
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(preset.primaryGlow, preset.secondaryGlow, preset.tertiaryGlow)
                                        )
                                    )
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

        // ================= 3. TRUNG TÂM CẬP NHẬT OTA NHẬN DIỆN ĐÚNG PHIÊN BẢN =================
        LiquidGlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Trung tâm Cập nhật OTA Siêu tốc",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                if (!FileOperationHelper.hasAllFilesAccess()) {
                    LiquidGlassPill(
                        active = true,
                        onClick = { FileOperationHelper.requestAllFilesAccess(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔓 Cấp quyền Quản lý tất cả tệp (Đổi tên / Copy / Xóa)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("GitHub Repository (owner/repo)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Chọn tuyến máy chủ tải:", style = MaterialTheme.typography.labelMedium, color = Color(0xFFA7B8D8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassPill(
                        active = selectedMirror == 0,
                        onClick = { selectedMirror = 0 }
                    ) { Text("GitHub Gốc", color = Color.White, style = MaterialTheme.typography.labelMedium) }

                    LiquidGlassPill(
                        active = selectedMirror == 1,
                        onClick = { selectedMirror = 1 }
                    ) { Text("CDN 1 (ghfast)", color = Color.White, style = MaterialTheme.typography.labelMedium) }

                    LiquidGlassPill(
                        active = selectedMirror == 2,
                        onClick = { selectedMirror = 2 }
                    ) { Text("CDN 2 (ghproxy)", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                }

                LiquidGlassPill(
                    active = true,
                    onClick = {
                        if (!isDownloading) {
                            scope.launch {
                                context.saveGithubRepo(repoInput)
                                statusText = "Đang kiểm tra phiên bản trên GitHub..."
                                forceShowReinstall = false
                                val result = updateManager.checkLatestRelease(repoInput)
                                result.onSuccess { release ->
                                    latestRelease = release
                                    val newer = updateManager.isRemoteVersionNewer(BuildConfig.VERSION_NAME, release.tag_name)
                                    isNewerAvailable = newer
                                    statusText = if (newer) {
                                        "🎉 Phát hiện phiên bản mới: ${release.tag_name} (Hiện tại: v${BuildConfig.VERSION_NAME})"
                                    } else {
                                        "✅ Bạn đang sử dụng phiên bản mới nhất (v${BuildConfig.VERSION_NAME})!"
                                    }
                                }.onFailure { err ->
                                    statusText = "Lỗi: ${err.message}"
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

                if (latestRelease != null && !isNewerAvailable && !forceShowReinstall) {
                    TextButton(onClick = { forceShowReinstall = true }) {
                        Text("Vẫn hiển thị tùy chọn tải lại bản v${BuildConfig.VERSION_NAME}", color = Color(0xFFA7B8D8))
                    }
                }
            }
        }

        latestRelease?.let { release ->
            if (isNewerAvailable || forceShowReinstall) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
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
                                "📦 ${apkAsset.name} ($sizeMb)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
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
                                        scope.launch {
                                            isDownloading = true
                                            downloadProgress = 0
                                            speedInfo = "Đang kết nối..."
                                            statusText = "Đang mở 8 luồng tải song song..."
                                            val dl = updateManager.downloadApkMultiThread(
                                                rawUrl = apkAsset.browser_download_url,
                                                expectedSize = apkAsset.size,
                                                mirrorIndex = selectedMirror
                                            ) { p, spd ->
                                                downloadProgress = p
                                                speedInfo = spd
                                            }
                                            isDownloading = false
                                            dl.onSuccess { file ->
                                                statusText = "Đã tải xong 100%! Đang mở trình cài đặt..."
                                                updateManager.installApk(file)
                                            }.onFailure { err ->
                                                statusText = "Lỗi: ${err.message}. Hãy thử chọn CDN 1 hoặc nút Trình tải Hệ thống."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (isDownloading) "⏳ Đang tải 8 luồng..." else "⚡ Tải Siêu tốc 8 luồng & Cài đặt",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LiquidGlassPill(
                                active = false,
                                onClick = {
                                    updateManager.downloadWithSystemManager(
                                        rawUrl = apkAsset.browser_download_url,
                                        fileName = apkAsset.name,
                                        useMirror = (selectedMirror != 0)
                                    )
                                    Toast.makeText(context, "Đã chuyển sang Trình tải Hệ thống!", Toast.LENGTH_LONG).show()
                                    statusText = "Đang tải ngầm bằng System DownloadManager. Khi xong máy sẽ tự mở bảng cài đặt!"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🚀 Tải ngầm bằng Hệ thống (System DownloadManager)", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}