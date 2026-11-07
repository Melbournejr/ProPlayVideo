package com.videoplayer.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.videoplayer.app.R
import com.videoplayer.app.ui.player.PlayerScreen
import com.videoplayer.app.ui.stream.NetworkStreamScreen
import com.videoplayer.app.ui.theme.VideoPlayerTheme
import com.videoplayer.app.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val requestStoragePermission = registerForActivityResult(RequestMultiplePermissions()) { granted ->
        // Kết quả quyền được xử lý tại các màn hình nếu cần
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionHelper.hasStoragePermission(this)) {
            requestStoragePermission.launch(arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ))
        }

        setContent {
            VideoPlayerTheme {
                var screen by remember { mutableStateOf(Screen.Home) }
                var selectedVideo by remember { mutableStateOf<com.videoplayer.app.data.model.VideoItem?>(null) }
                var streamUrl by remember { mutableStateOf("") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = "Video Player Pro") },
                            colors = TopAppBarDefaults.mediumTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                ) { padding ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            onVideoClick = { video ->
                                selectedVideo = video
                                screen = Screen.Player
                            },
                            onStreamClick = { screen = Screen.Stream },
                            onSettingsClick = { screen = Screen.Settings }
                        )
                        Screen.Player -> selectedVideo?.let { video ->
                            PlayerScreen(
                                video = video,
                                onBack = { screen = Screen.Home; selectedVideo = null }
                            )
                        }
                        Screen.Stream -> NetworkStreamScreen(
                            modifier = Modifier.padding(padding),
                            onPlayClick = { url ->
                                streamUrl = url
                                screen = Screen.Player
                            }
                        )
                        Screen.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }

    private enum class Screen {
        Home, Player, Stream, Settings
    }

    @Composable
    private fun SettingsScreen() = Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Cài đặt", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Chức năng cài đặt sẽ được bổ sung trong phiên bản sau.")
    }
}
