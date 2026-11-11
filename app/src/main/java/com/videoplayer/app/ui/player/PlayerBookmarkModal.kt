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
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette

/**
 * Modal Quản lý Bookmark có ghi chú (Phần C.2):
 * - Đánh dấu mốc thời gian đang phát kèm ghi chú văn bản
 * - Hiển thị danh sách Bookmark của video hiện tại
 * - Chạm vào Bookmark để nhảy (Seek) tới đúng thời điểm đó ngay lập tức
 */
@Composable
fun BoxScope.PlayerBookmarkModal(state: PlayerStateHolder) {
    val palette = LocalGlassPalette.current
    val currentVid = state.currentVideo ?: return
    var noteInput by remember { mutableStateOf("") }

    LiquidGlassCard(
        cornerRadius = 26.dp,
        highlighted = true,
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 500.dp)
            .heightIn(max = 380.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Bookmark, contentDescription = null, tint = palette.primaryGlow)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Đánh dấu & Ghi chú (${state.bookmarks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassPill(onClick = { state.activeModal = ActiveModal.NONE }) {
                    Text("Đóng ✕", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Ô nhập ghi chú mới tại mốc thời gian hiện tại
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = {
                        Text("Ghi chú tại ${formatTimeMs(state.currentPos)}...", style = MaterialTheme.typography.bodySmall)
                    },
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
                        Toast.makeText(state.context, "🔖 Đã lưu mốc ${formatTimeMs(pos)}!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lưu mốc", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            if (state.bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Chưa có mốc đánh dấu nào cho video này.\nNhập ghi chú và bấm 'Lưu mốc' để đánh dấu cảnh quan trọng.",
                        color = Color(0xFFA7B8D8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.bookmarks, key = { it.id }) { bm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black.copy(alpha = 0.32f))
                                .border(1.dp, palette.primaryGlow.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable {
                                    state.playerManager.seekToFast(bm.timestampMs)
                                    state.currentPos = bm.timestampMs
                                    state.lastSeekCompletedTimeMs = System.currentTimeMillis()
                                    state.activeModal = ActiveModal.NONE
                                    state.showTemporaryHint("🔖 Nhảy tới: ${bm.note} (${formatTimeMs(bm.timestampMs)})")
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = bm.note,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
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
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Xóa mốc",
                                    tint = Color(0xFFFF5C8A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}