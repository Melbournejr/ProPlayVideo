package com.videoplayer.app.ui.stream

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.observeHttpWarnAcknowledged
import com.videoplayer.app.data.local.saveHttpWarnAcknowledged
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import kotlinx.coroutines.launch

@Composable
fun NetworkStreamScreen(
    modifier: Modifier = Modifier,
    onPlayClick: (String) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)

    var url by remember { mutableStateOf("") }
    var showHttpWarningDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize().padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            cornerRadius = 26.dp,
            tintColor = palette.primaryGlow,
            surfaceAlpha = 0.12f,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = AppIcons.Public,
                    contentDescription = null,
                    tint = palette.primaryGlow,
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
                    text = "Hỗ trợ liên kết trực tiếp MP4, MKV, HLS (.m3u8), DASH (Ưu tiên HTTPS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA7B8D8)
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Nhập URL video (https:// hoặc http://)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                LiquidGlassPill(
                    active = true,
                    onClick = {
                        val trimmed = url.trim()
                        if (trimmed.isNotBlank()) {
                            if (trimmed.startsWith("http://", ignoreCase = true) && !httpWarnAck) {
                                showHttpWarningDialog = true
                            } else {
                                onPlayClick(trimmed)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Bắt đầu phát Stream", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showHttpWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHttpWarningDialog = false },
            containerColor = palette.deepBgMid,
            title = { Text("⚠️ Cảnh báo kết nối HTTP không mã hóa", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Liên kết bạn nhập sử dụng giao thức http:// (không có mã hóa TLS/SSL). Dữ liệu luồng phát có thể bị xem lén trên mạng công cộng. Bạn có chắc chắn muốn tiếp tục phát không?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        context.saveHttpWarnAcknowledged(true)
                        showHttpWarningDialog = false
                        onPlayClick(url.trim())
                    }
                }) {
                    Text("Hiểu & Tiếp tục phát")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHttpWarningDialog = false }) { Text("Hủy") }
            }
        )
    }
}