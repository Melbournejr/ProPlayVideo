package com.videoplayer.app.ui.stream

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NetworkStreamScreen(
    modifier: Modifier = Modifier,
    onPlayClick: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(
            text = "Phát từ đường dẫn mạng",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text(text = "Nhập URL video (MP4, M3U8…)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { if (url.isNotEmpty()) onPlayClick(url) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Phát video")
        }
    }
}
