package com.videoplayer.app.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.videoplayer.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestStoragePermission = registerForActivityResult(RequestMultiplePermissions()) { }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionHelper.hasStoragePermission(this)) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestStoragePermission.launch(perms)
        }

        setContent {
            VideoPlayerTheme {
                var screen by remember { mutableStateOf(Screen.Home) }
                var selectedFolder by remember { mutableStateOf<String?>(null) }
                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var streamUrl by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    topBar = {
                        if (screen != Screen.Player) {
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
                    }
                ) { padding ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            selectedFolder = selectedFolder,
                            onSelectFolder = { folder -> selectedFolder = folder },
                            onVideoClick = { video ->
                                selectedVideo = video
                                streamUrl = null
                                screen = Screen.Player
                            },
                            onStreamClick = { screen = Screen.Stream }
                        )
                        Screen.Player -> PlayerScreen(
                            video = selectedVideo,
                            streamUrl = streamUrl,
                            onBack = {
                                screen = Screen.Home
                                selectedVideo = null
                                streamUrl = null
                            }
                        )
                        Screen.Stream -> NetworkStreamScreen(
                            modifier = Modifier.padding(padding),
                            onPlayClick = { url ->
                                selectedVideo = null
                                streamUrl = url
                                screen = Screen.Player
                            }
                        )
                        Screen.Settings -> OtaSettingsScreen(modifier = Modifier.padding(padding))
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
    var statusText by remember { mutableStateOf("Phiên bản hiện tại: v${BuildConfig.VERSION_NAME}. Bấm kiểm tra để tìm bản cập nhật mới nhất.") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cập nhật OTA qua GitHub Releases", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = repoInput,
            onValueChange = { repoInput = it },
            label = { Text("GitHub Repository (owner/repo)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    context.saveGithubRepo(repoInput)
                    statusText = "Đang kiểm tra bản phát hành mới trên GitHub..."
                    val result = updateManager.checkLatestRelease(repoInput)
                    result.onSuccess { release ->
                        latestRelease = release
                        statusText = "Tìm thấy bản phát hành: ${release.tag_name} (${release.name})"
                    }.onFailure { err ->
                        statusText = "Lỗi: ${err.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kiểm tra bản cập nhật mới")
        }

        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

        latestRelease?.let { release ->
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apkAsset != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bản cài đặt: ${apkAsset.name}", style = MaterialTheme.typography.titleMedium)
                        if (downloadProgress in 0..100) {
                            LinearProgressIndicator(progress = downloadProgress / 100f, modifier = Modifier.fillMaxWidth())
                            Text("Đang tải xuống: $downloadProgress%")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    downloadProgress = 0
                                    val dl = updateManager.downloadApk(apkAsset.browser_download_url) { p ->
                                        downloadProgress = p
                                    }
                                    dl.onSuccess { file ->
                                        statusText = "Đã tải xong! Đang mở trình cài đặt..."
                                        updateManager.installApk(file)
                                    }.onFailure { err ->
                                        statusText = "Lỗi tải APK: ${err.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tải xuống & Cài đặt ngay (OTA)")
                        }
                    }
                }
            }
        }
    }
}