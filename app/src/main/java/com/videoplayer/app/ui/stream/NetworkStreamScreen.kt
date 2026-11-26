package com.videoplayer.app.ui.stream

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.SavedStreamItem
import com.videoplayer.app.data.local.StreamHistoryManager
import com.videoplayer.app.data.local.observeHttpWarnAcknowledged
import com.videoplayer.app.data.local.saveHttpWarnAcknowledged
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.M3uPlaylistParser
import kotlinx.coroutines.launch

@Composable
fun NetworkStreamScreen(
    modifier: Modifier = Modifier,
    onPlayClick: (url: String, title: String?) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalGlassPalette.current
    val scope = rememberCoroutineScope()
    val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)

    LaunchedEffect(Unit) {
        StreamHistoryManager.ensureInitialized(context)
    }
    val streamHistory by StreamHistoryManager.items.collectAsState()
    val m3uChannels by StreamHistoryManager.m3uChannels.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = URL & Lich su/Ghim, 1 = Kenh IPTV (M3U)
    var url by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var m3uSearchQuery by remember { mutableStateOf("") }
    var isImportingM3u by remember { mutableStateOf(false) }

    var pendingUrlToPlay by remember { mutableStateOf<String?>(null) }
    var pendingTitleToSave by remember { mutableStateOf("") }
    var showHttpWarningDialog by remember { mutableStateOf(false) }
    var showImportM3uUrlDialog by remember { mutableStateOf(false) }
    var renamingItem by remember { mutableStateOf<SavedStreamItem?>(null) }

    val m3uFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isImportingM3u = true
                M3uPlaylistParser.parseFromUri(context, uri).onSuccess { list ->
                    StreamHistoryManager.saveM3uChannels(context, list)
                    activeTab = 1
                    Toast.makeText(context, "\u2705 \u0110\u00E3 n\u1EA1p ${list.size} k\u00EAnh IPTV t\u1EEB t\u1EC7p M3U!", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "\u274C ${err.message}", Toast.LENGTH_LONG).show()
                }
                isImportingM3u = false
            }
        }
    }

    fun launchStream(targetUrl: String, titleHint: String = "") {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return
        if (trimmed.startsWith("http://", ignoreCase = true) && !httpWarnAck) {
            pendingUrlToPlay = trimmed
            pendingTitleToSave = titleHint
            showHttpWarningDialog = true
        } else {
            val resolvedTitle = StreamHistoryManager.recordStreamPlayed(context, trimmed, titleHint)
            onPlayClick(trimmed, resolvedTitle)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thanh chuyen doi 2 Tab: [URL & Lich su] | [Kenh IPTV M3U]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassPill(
                active = activeTab == 0,
                onClick = { activeTab = 0 },
                modifier = Modifier.weight(1f)
            ) {
                Icon(AppIcons.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "URL & L\u1ECBch s\u1EED (${streamHistory.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            LiquidGlassPill(
                active = activeTab == 1,
                onClick = { activeTab = 1 },
                modifier = Modifier.weight(1f)
            ) {
                Icon(AppIcons.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "IPTV M3U (${m3uChannels.size})",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isImportingM3u) {
            LinearProgressIndicator(
                color = palette.primaryGlow,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (activeTab == 0) {
            // ================= TAB 0: PHAT URL TRUC TIEP + LICH SU & GHIM =================
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Nh\u1EADp URL video (https:// ho\u1EB7c http://)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it },
                label = { Text("T\u00EAn g\u1EE3i nh\u1EDB (v\u00ED d\u1EE5: VTV1 HD, Phim l\u1EBB...)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LiquidGlassPill(
                active = true,
                onClick = { launchStream(url, customTitle) },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("B\u1EAFt \u0111\u1EA7u ph\u00E1t Stream", color = Color.White, fontWeight = FontWeight.Bold)
            }

            if (streamHistory.isNotEmpty()) {
                Divider(color = Color.White.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "K\u00EAnh \u0111\u00E3 ghim & L\u1ECBch s\u1EED (${streamHistory.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primaryGlow,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            StreamHistoryManager.clearUnpinnedHistory(context)
                            Toast.makeText(context, "\u0110\u00E3 x\u00F3a l\u1ECBch s\u1EED (gi\u1EEF l\u1EA1i c\u00E1c k\u00EAnh \u0111\u00E3 ghim)", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("D\u1ECDn l\u1ECBch s\u1EED", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(streamHistory, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (item.isPinned) palette.primaryGlow.copy(alpha = 0.14f)
                                    else Color.Black.copy(alpha = 0.30f)
                                )
                                .border(
                                    width = if (item.isPinned) 1.1.dp else 0.8.dp,
                                    color = if (item.isPinned) palette.primaryGlow.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    url = item.url
                                    customTitle = item.customName
                                    launchStream(item.url, item.customName)
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.customName,
                                    color = if (item.isPinned) palette.primaryGlow else Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = item.url,
                                    color = Color(0xFFA7B8D8),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Nut Ghim / Bo ghim kenh yeu thich
                            IconButton(
                                onClick = {
                                    val pinned = StreamHistoryManager.togglePin(context, item.id)
                                    Toast.makeText(
                                        context,
                                        if (pinned) "\uD83D\uDCCC \u0110\u00E3 ghim l\u00EAn \u0111\u1EA7u!" else "\u0110\u00E3 b\u1ECF ghim",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.PushPin,
                                    contentDescription = "Ghim",
                                    tint = if (item.isPinned) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { renamingItem = item },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "\u0110\u1ED5i t\u00EAn",
                                    tint = palette.primaryGlow,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            IconButton(
                                onClick = { StreamHistoryManager.deleteStream(context, item.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "X\u00F3a",
                                    tint = Color(0xFFFF5C8A),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ================= TAB 1: DANH SACH KENH IPTV (M3U / M3U8) =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassPill(
                    active = true,
                    onClick = { showImportM3uUrlDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("\uD83C\uDF10 Nh\u1EADp link M3U", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                LiquidGlassPill(
                    active = false,
                    onClick = { m3uFilePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("\uD83D\uDCC2 Ch\u1ECDn file .M3U", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (m3uChannels.isNotEmpty()) {
                OutlinedTextField(
                    value = m3uSearchQuery,
                    onValueChange = { m3uSearchQuery = it },
                    placeholder = { Text("T\u00ECm t\u00EAn k\u00EAnh ho\u1EB7c nh\u00F3m k\u00EAnh...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primaryGlow) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredChannels = remember(m3uChannels, m3uSearchQuery) {
                    if (m3uSearchQuery.isBlank()) m3uChannels
                    else m3uChannels.filter {
                        it.name.contains(m3uSearchQuery, ignoreCase = true) ||
                            it.group.contains(m3uSearchQuery, ignoreCase = true)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hi\u1EC3n th\u1ECB ${filteredChannels.size}/${m3uChannels.size} k\u00EAnh",
                        color = palette.primaryGlow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { StreamHistoryManager.clearM3uChannels(context) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("X\u00F3a danh s\u00E1ch", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.30f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                .clickable { launchStream(ch.url, ch.name) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = palette.primaryGlow,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ch.group.isNotBlank()) {
                                    Text(
                                        text = ch.group,
                                        color = palette.primaryGlow.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ch\u01B0a c\u00F3 danh s\u00E1ch k\u00EAnh IPTV.\nB\u1EA5m 'Nh\u1EADp link M3U' ho\u1EB7c 'Ch\u1ECDn file .M3U' \u1EDF tr\u00EAn \u0111\u1EC3 n\u1EA1p k\u00EAnh.",
                        color = Color(0xFFA7B8D8),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Hop thoai nhap link M3U truc tuyen
    if (showImportM3uUrlDialog) {
        var m3uUrlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportM3uUrlDialog = false },
            containerColor = palette.deepBgMid,
            title = { Text("Nh\u1EADp danh s\u00E1ch k\u00EAnh IPTV (M3U)", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = m3uUrlInput,
                    onValueChange = { m3uUrlInput = it },
                    label = { Text("D\u00E1n URL t\u1EC7p .m3u ho\u1EB7c .m3u8") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = m3uUrlInput.trim()
                    if (target.isNotBlank()) {
                        showImportM3uUrlDialog = false
                        scope.launch {
                            isImportingM3u = true
                            M3uPlaylistParser.fetchFromUrl(target).onSuccess { list ->
                                StreamHistoryManager.saveM3uChannels(context, list)
                                activeTab = 1
                                Toast.makeText(context, "\u2705 \u0110\u00E3 n\u1EA1p ${list.size} k\u00EAnh IPTV!", Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "\u274C ${err.message}", Toast.LENGTH_LONG).show()
                            }
                            isImportingM3u = false
                        }
                    }
                }) { Text("N\u1EA1p danh s\u00E1ch") }
            },
            dismissButton = {
                TextButton(onClick = { showImportM3uUrlDialog = false }) { Text("H\u1EE7y") }
            }
        )
    }

    // Hop thoai doi ten luong Stream
    renamingItem?.let { item ->
        var newNameInput by remember(item.id) { mutableStateOf(item.customName) }
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            containerColor = palette.deepBgMid,
            title = { Text("\u0110\u1ED5i t\u00EAn lu\u1ED3ng Stream", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.url,
                        color = Color(0xFFA7B8D8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("T\u00EAn g\u1EE3i nh\u1EDB m\u1EDBi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newNameInput.isNotBlank()) {
                        StreamHistoryManager.renameStream(context, item.id, newNameInput)
                        renamingItem = null
                        Toast.makeText(context, "\u0110\u00E3 \u0111\u1ED5i t\u00EAn lu\u1ED3ng Stream!", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("L\u01B0u") }
            },
            dismissButton = {
                TextButton(onClick = { renamingItem = null }) { Text("H\u1EE7y") }
            }
        )
    }

    // Canh bao HTTP Cleartext 1 lan
    if (showHttpWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHttpWarningDialog = false },
            containerColor = palette.deepBgMid,
            title = { Text("\u26A0\uFE0F C\u1EA3nh b\u00E1o k\u1EBFt n\u1ED1i HTTP kh\u00F4ng m\u00E3 h\u00F3a", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Li\u00EAn k\u1EBFt b\u1EA1n nh\u1EADp s\u1EED d\u1EE5ng giao th\u1EE9c http:// (kh\u00F4ng c\u00F3 m\u00E3 h\u00F3a TLS/SSL). B\u1EA1n c\u00F3 ch\u1EAFc ch\u1EAFn mu\u1ED1n ti\u1EBFp t\u1EE5c ph\u00E1t kh\u00F4ng?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        context.saveHttpWarnAcknowledged(true)
                        showHttpWarningDialog = false
                        val target = pendingUrlToPlay ?: url.trim()
                        val resolved = StreamHistoryManager.recordStreamPlayed(context, target, pendingTitleToSave)
                        onPlayClick(target, resolved)
                    }
                }) { Text("Hi\u1EC3u & Ti\u1EBFp t\u1EE5c ph\u00E1t") }
            },
            dismissButton = {
                TextButton(onClick = { showHttpWarningDialog = false }) { Text("H\u1EE7y") }
            }
        )
    }
}