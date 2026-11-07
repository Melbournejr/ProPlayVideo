package com.videoplayer.app.ui.stream

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill

@Composable
fun NetworkStreamScreen(
    modifier: Modifier = Modifier,
    onPlayClick: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    Box(
        modifier = modifier.fillMaxSize().padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            tintColor = Color(0xFF00E5FF),
            surfaceAlpha = 0.12f,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Phát Luồng Mạng Trực Tuyến",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hỗ trợ liên kết trực tiếp MP4, MKV, HLS (.m3u8), DASH",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA7B8D8)
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Nhập URL video (https://...)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                LiquidGlassPill(
                    active = true,
                    onClick = { if (url.isNotBlank()) onPlayClick(url.trim()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Bắt đầu phát Stream", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}