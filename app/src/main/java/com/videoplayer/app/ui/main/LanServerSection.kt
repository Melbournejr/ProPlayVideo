package com.videoplayer.app.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.videoplayer.app.network.LanVideoHttpServer
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette

@Composable
fun LanWebServerSettingsCard() {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val lanStatus by LanVideoHttpServer.status.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121722).copy(alpha = 0.88f))
            .border(
                width = 0.9.dp,
                color = if (lanStatus.isRunning) palette.primaryGlow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppIcons.WifiTethering,
                contentDescription = null,
                tint = if (lanStatus.isRunning) palette.primaryGlow else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local LAN Web Server",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Xem video qua tr\u00ECnh duy\u1EC7t c\u00F9ng m\u1EA1ng Wi-Fi",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.60f)
                )
            }
            Switch(
                checked = lanStatus.isRunning,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        LanVideoHttpServer.startServer(context, LanVideoHttpServer.DEFAULT_PORT)
                    } else {
                        LanVideoHttpServer.stopServer()
                    }
                }
            )
        }

        if (lanStatus.isRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(0.8.dp, palette.primaryGlow.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "\u0110\u1ECBa ch\u1EC9 truy c\u1EADp",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.60f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = lanStatus.url,
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryGlow,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LiquidGlassPill(
                        active = true,
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("LAN Server URL", lanStatus.url))
                            Toast.makeText(context, "\u0110\u00E3 sao ch\u00E9p: ${lanStatus.url}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(AppIcons.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Copy", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }

                Text(
                    text = "\u0110ang chia s\u1EBB ${lanStatus.sharedVideoCount} video c\u00F4ng khai (t\u1EF1 t\u1EAFt khi \u0111\u00F3ng \u1EE9ng d\u1EE5ng)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}