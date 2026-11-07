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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.BuildConfig
import com.videoplayer.app.data.local.readGithubRepo
import com.videoplayer.app.data.local.saveGithubRepo
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.ui.player.PlayerScreen
import com.videoplayer.app.ui.stream.NetworkStreamScreen
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import com.videoplayer.app.update.GitHubRelease
import com.videoplayer.app.update.UpdateManager
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.PermissionHelper
import kotlinx.coroutines.launch

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

    @OptIn(ExperimentalMaterial3Api::class)
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
            VideoPlayerTheme {
                var screen by remember { mutableStateOf(Screen.Home) }
                var selectedFolder by remember { mutableStateOf<String?>(null) }
                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var currentPlaylist by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
                var streamUrl by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(screen) {
                    isCurrentlyInPlayerScreen = (screen == Screen.Player)
                }

                if (screen == Screen.Player) {
                    PlayerScreen(
                        video = selectedVideo,
                        playlist = currentPlaylist,
                        streamUrl = streamUrl,
                        onBack = {
                            screen = Screen.Home
                            selectedVideo = null
                            streamUrl = null
                        }
                    )
                } else {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                navigationIcon = {
                                    if (screen == Screen.Home && selectedFolder != null) {
                                        IconButton(onClick = { selectedFolder = null }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại thư mục")
                                        }
                                    } else if (screen != Screen.Home) {
                                        IconButton(onClick = { screen = Screen.Home }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                                        }
                                    }
                                },
                                title = {
                                    val titleText = when {
                                        screen == Screen.Home && selectedFolder != null -> selectedFolder!!
                                        screen == Screen.Stream -> "Phát luồng mạng"
                                        screen == Screen.Settings -> "Cài đặt & Cập nhật OTA"
                                        else -> "ProPlayVideo (v${BuildConfig.VERSION_NAME})"
                                    }
                                    Text(titleText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                actions = {
                                    IconButton(onClick = {
                                        screen = Screen.Home
                                        selectedFolder = null
                                    }) {
                                        Icon(Icons.Default.Home, contentDescription = "Thư mục gốc")
                                    }
                                    IconButton(onClick = { screen = Screen.Settings }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt & OTA")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        when (screen) {
                            Screen.Home -> HomeScreen(
                                modifier = Modifier.padding(padding),
                                selectedFolder = selectedFolder,
                                onSelectFolder = { folder -> selectedFolder = folder },
                                onVideoClick = { video, playlist ->
                                    selectedVideo = video
                                    currentPlaylist = playlist
                                    streamUrl = null
                                    screen = Screen.Player
                                },
                                onStreamClick = { screen = Screen.Stream }
                            )
                            Screen.Stream -> NetworkStreamScreen(
                                modifier = Modifier.padding(padding),
                                onPlayClick = { url ->
                                    selectedVideo = null
                                    currentPlaylist = emptyList()
                                    streamUrl = url
                                    screen = Screen.Player
                                }
                            )
                            Screen.Settings -> OtaSettingsScreen(modifier = Modifier.padding(padding))
                            else -> {}
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
    val scope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    val savedRepo by context.readGithubRepo("Melbournejr/ProPlayVideo").collectAsState(initial = "Melbournejr/ProPlayVideo")
    var repoInput by remember(savedRepo) { mutableStateOf(savedRepo) }
    var selectedMirror by remember { mutableIntStateOf(0) } // 0: GitHub gốc, 1: ghfast CDN, 2: ghproxy CDN
    var statusText by remember { mutableStateOf("Phiên bản hiện tại: v${BuildConfig.VERSION_NAME} (Hỗ trợ tải 8 luồng & System DownloadManager)") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var speedInfo by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cập nhật OTA & Quyền Quản lý Tệp", style = MaterialTheme.typography.titleLarge)

        if (!FileOperationHelper.hasAllFilesAccess()) {
            OutlinedButton(
                onClick = { FileOperationHelper.requestAllFilesAccess(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cấp quyền Quản lý tất cả tệp (Đổi tên / Copy / Xóa)")
            }
        }

        OutlinedTextField(
            value = repoInput,
            onValueChange = { repoInput = it },
            label = { Text("GitHub Repository (owner/repo)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Chọn máy chủ tải (Đổi sang CDN nếu mạng nhà mạng bóp băng thông GitHub):", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMirror == 0,
                onClick = { selectedMirror = 0 },
                label = { Text("GitHub Gốc") }
            )
            FilterChip(
                selected = selectedMirror == 1,
                onClick = { selectedMirror = 1 },
                label = { Text("CDN Nhanh 1 (ghfast)") }
            )
            FilterChip(
                selected = selectedMirror == 2,
                onClick = { selectedMirror = 2 },
                label = { Text("CDN 2 (ghproxy)") }
            )
        }

        Button(
            onClick = {
                scope.launch {
                    context.saveGithubRepo(repoInput)
                    statusText = "Đang kiểm tra bản phát hành mới trên GitHub..."
                    val result = updateManager.checkLatestRelease(repoInput)
                    result.onSuccess { release ->
                        latestRelease = release
                        statusText = "Tìm thấy bản phát hành mới nhất: ${release.tag_name} (${release.name})"
                    }.onFailure { err ->
                        statusText = "Lỗi: ${err.message}"
                    }
                }
            },
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kiểm tra bản cập nhật mới")
        }

        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

        latestRelease?.let { release ->
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apkAsset != null) {
                val sizeMb = "%.2f MB".format(apkAsset.size / (1024f * 1024f))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bản cài đặt: ${apkAsset.name} ($sizeMb)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (downloadProgress in 0..100) {
                            LinearProgressIndicator(
                                progress = downloadProgress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Đang tải 8 luồng: $downloadProgress% ($speedInfo)")
                        }
                        Button(
                            onClick = {
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
                                        statusText = "Lỗi: ${err.message}. Hãy chọn thử 'CDN Nhanh 1' hoặc bấm nút Trình tải Hệ thống bên dưới."
                                    }
                                }
                            },
                            enabled = !isDownloading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isDownloading) "Đang tải 8 luồng..." else "⚡ Tải Siêu tốc 8 luồng & Cài đặt")
                        }

                        OutlinedButton(
                            onClick = {
                                updateManager.downloadWithSystemManager(
                                    rawUrl = apkAsset.browser_download_url,
                                    fileName = apkAsset.name,
                                    useMirror = (selectedMirror != 0)
                                )
                                Toast.makeText(context, "Đã chuyển sang Trình tải Hệ thống (xem trên thanh thông báo)!", Toast.LENGTH_LONG).show()
                                statusText = "Đang tải ngầm bằng System DownloadManager. Khi tải xong máy sẽ tự bung bảng cài đặt!"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚀 Tải ngầm bằng Hệ thống (System DownloadManager)")
                        }
                    }
                }
            }
        }
    }
}