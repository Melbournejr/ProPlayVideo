package com.videoplayer.app.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.WatchExperienceManager
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassIconButton
import com.videoplayer.app.ui.components.LiquidGlassModalSheet
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette

@Composable
fun BoxScope.PlayerBookmarkModal(state: PlayerStateHolder) {
    val palette = LocalGlassPalette.current
    val currentVid = state.currentVideo ?: return
    var noteInput by remember { mutableStateOf("") }

    LiquidGlassModalSheet(
        cornerRadius = 28.dp,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 560.dp)
            .heightIn(max = 320.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Bookmark, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "\u0110\u00E1nh d\u1EA5u & Ghi ch\u00FA (${state.bookmarks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(onClick = { state.activeModal = ActiveModal.NONE }, size = 32.dp) {
                    Icon(Icons.Default.Close, contentDescription = "\u0110\u00F3ng", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = { Text("Ghi ch\u00FA t\u1EA1i ${formatTimeMs(state.currentPos)}...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassPill(
                    active = true,
                    onClick = {
                        val pos = state.playerManager.getCurrentPosition()
                        state.bookmarks = WatchExperienceManager.addBookmark(
                            context = state.context,
                            videoId = currentVid.id,
                            timestampMs = pos,
                            note = noteInput
                        )
                        noteInput = ""
                        Toast.makeText(state.context, "\u0110\u00E3 l\u01B0u m\u1ED1c ${formatTimeMs(pos)}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("L\u01B0u m\u1ED1c", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Ch\u01B0a c\u00F3 m\u1ED1c \u0111\u00E1nh d\u1EA5u n\u00E0o cho video n\u00E0y.",
                        color = Color.White.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(state.bookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.30f))
                                .border(0.8.dp, palette.primaryGlow.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable {
                                    state.playerManager.seekToFast(bm.timestampMs)
                                    state.currentPos = bm.timestampMs
                                    state.lastSeekCompletedTimeMs = System.currentTimeMillis()
                                    state.activeModal = ActiveModal.NONE
                                    state.showTemporaryHint("${bm.note} (${formatTimeMs(bm.timestampMs)})")
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primaryGlow.copy(alpha = 0.22f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formatTimeMs(bm.timestampMs),
                                    color = palette.primaryGlow,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = bm.note,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    state.bookmarks = WatchExperienceManager.deleteBookmark(
                                        context = state.context,
                                        videoId = currentVid.id,
                                        bookmarkId = bm.id
                                    )
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "X\u00F3a", tint = Color(0xFFFF5C8A), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}