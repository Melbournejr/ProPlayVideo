package com.videoplayer.app.ui.main

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                var selectedVideo by remember { mutableStateOf<VideoItem?>(null) }
                var streamUrl by remember { mutableStateOf<String?>(null) }

                Scaffold(
                    topBar = {
                        if (screen != Screen.Player) {
                            TopAppBar(
                                title = { Text("Video Player Pro (v1.0.0)") },
                                actions = {
                                    IconButton(onClick = { screen = Screen.Home }) {
                                        Icon(Icons.Default.Home, contentDescription = "Trang chá»§")
                                    }
                                    IconButton(onClick = { screen = Screen.Settings }) {
                                        Icon(Icons.Default.Settings, contentDescription = "CÃ i Ä‘áº·t & OTA")
                                    }
                                }
                            )
                        }
                    }
                ) { padding ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            onVideoClick = { video ->
                                selectedVideo = video
                                streamUrl = null
                                screen = Screen.Player
                            },
                            onStreamClick = { screen = Screen.Stream },
                            onSettingsClick = { screen = Screen.Settings }
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
    var statusText by remember { mutableStateOf("Nháº­p Ä‘Ãºng 'TÃªn_GitHub/TÃªn_Repo' cá»§a báº¡n rá»“i báº¥m Kiá»ƒm tra cáº­p nháº­t.") }
    var latestRelease by remember { mutableStateOf<GitHubRelease?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Cáº­p nháº­t OTA qua GitHub Releases", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = repoInput,
            onValueChange = { repoInput = it },
            label = { Text("GitHub Repository (owner/repo)") },
            placeholder = { Text("VÃ­ dá»¥: vuvanluong/VideoPlayerApp") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                scope.launch {
                    context.saveGithubRepo(repoInput)
                    statusText = "Äang kiá»ƒm tra báº£n phÃ¡t hÃ nh má»›i trÃªn GitHub..."
                    val result = updateManager.checkLatestRelease(repoInput)
                    result.onSuccess { release ->
                        latestRelease = release
                        statusText = "TÃ¬m tháº¥y báº£n phÃ¡t hÃ nh: ${release.tag_name} (${release.name})"
                    }.onFailure { err ->
                        statusText = "Lá»—i: ${err.message}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Kiá»ƒm tra báº£n cáº­p nháº­t má»›i")
        }

        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

        latestRelease?.let { release ->
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (apkAsset != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File APK: ${apkAsset.name}", style = MaterialTheme.typography.titleMedium)
                        if (downloadProgress in 0..100) {
                            LinearProgressIndicator(progress = downloadProgress / 100f, modifier = Modifier.fillMaxWidth())
                            Text("Äang táº£i xuá»‘ng: $downloadProgress%")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    downloadProgress = 0
                                    val dl = updateManager.downloadApk(apkAsset.browser_download_url) { p ->
                                        downloadProgress = p
                                    }
                                    dl.onSuccess { file ->
                                        statusText = "ÄÃ£ táº£i xong! Äang má»Ÿ trÃ¬nh cÃ i Ä‘áº·t..."
                                        updateManager.installApk(file)
                                    }.onFailure { err ->
                                        statusText = "Lá»—i táº£i APK: ${err.message}"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Táº£i xuá»‘ng & CÃ i Ä‘áº·t ngay (OTA)")
                        }
                    }
                }
            }
        }
    }
}
