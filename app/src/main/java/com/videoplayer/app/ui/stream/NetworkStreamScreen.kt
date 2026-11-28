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
import androidx.compose.material.icons.filled.Close
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
import com.videoplayer.app.data.local.*
import com.videoplayer.app.network.StreamDownloadManager
import com.videoplayer.app.network.cloud.GoogleDriveProvider
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassStyle
import com.videoplayer.app.util.M3uPlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

private fun formatStreamPos(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun NetworkStreamScreen(
    modifier: Modifier = Modifier,
    onPlayClick: (url: String, title: String?) -> Unit
) {
    val context = LocalContext.current
    val style = LocalGlassStyle.current
    val palette = style.palette
    val scope = rememberCoroutineScope()
    val httpWarnAck by context.observeHttpWarnAcknowledged().collectAsState(initial = false)
    val maxLimit by context.observeStreamHistoryMaxLimit().collectAsState(initial = 1000)

    LaunchedEffect(maxLimit) {
        StreamHistoryManager.ensureInitialized(context)
        StreamHistoryManager.setMaxUnpinnedLimit(context, maxLimit)
    }

    val streamHistory by StreamHistoryManager.items.collectAsState()
    val m3uChannels by StreamHistoryManager.m3uChannels.collectAsState()
    val downloadTasks by StreamDownloadManager.tasks.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) }
    var url by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var historySearchQuery by remember { mutableStateOf("") }
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
                    Toast.makeText(context, "\u0110\u00E3 n\u1EA1p ${list.size} k\u00EAnh IPTV t\u1EEB t\u1EC7p M3U", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "L\u1ED7i: ${err.message}", Toast.LENGTH_LONG).show()
                }
                isImportingM3u = false
            }
        }
    }

    // Sao luu lich su Stream & kenh IPTV ra tep JSON
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val json = StreamHistoryManager.exportToJson(context)
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                            os.flush()
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                Toast.makeText(
                    context,
                    if (ok) "\u0110\u00E3 sao l\u01B0u danh s\u00E1ch Stream th\u00E0nh c\u00F4ng" else "Sao l\u01B0u th\u1EA5t b\u1EA1i",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Khoi phuc lich su Stream & kenh IPTV tu tep JSON
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    try {
                        val json = context.contentResolver.openInputStream(uri)?.use { input ->
                            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                        } ?: return@withContext Result.failure(Exception("Kh\u00F4ng \u0111\u1ECDc \u0111\u01B0\u1EE3c t\u1EC7p"))
                        StreamHistoryManager.importFromJson(context, json)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
                res.onSuccess { (addedHist, addedM3u) ->
                    Toast.makeText(
                        context,
                        "\u0110\u00E3 kh\u00F4i ph\u1EE5c +$addedHist lu\u1ED3ng l\u1ECBch s\u1EED v\u00E0 +$addedM3u k\u00EAnh IPTV",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    Toast.makeText(context, "L\u1ED7i: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun launchStream(targetUrl: String, titleHint: String = "") {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return
        if (GoogleDriveProvider.isGoogleDriveUrl(trimmed)) {
            scope.launch {
                isImportingM3u = true
                GoogleDriveProvider.resolvePublicLink(trimmed).onSuccess { (directUrl, detectedName) ->
                    val resolvedTitle = StreamHistoryManager.recordStreamPlayed(
                        context,
                        trimmed,
                        titleHint.ifBlank { detectedName.orEmpty() }
                    )
                    onPlayClick(directUrl, resolvedTitle)
                }.onFailure { err ->
                    Toast.makeText(context, "Google Drive: ${err.message}", Toast.LENGTH_LONG).show()
                }
                isImportingM3u = false
            }
            return
        }
        val isLocalProxy = trimmed.startsWith("http://127.0.0.1", ignoreCase = true)
        if (trimmed.startsWith("http://", ignoreCase = true) && !isLocalProxy && !httpWarnAck) {
            pendingUrlToPlay = trimmed
            pendingTitleToSave = titleHint
            showHttpWarningDialog = true
        } else {
            val resolvedTitle = StreamHistoryManager.recordStreamPlayed(context, trimmed, titleHint)
            onPlayClick(trimmed, resolvedTitle)
        }
    }

    fun triggerStreamDownload(targetUrl: String, titleHint: String) {
        val trimmed = targetUrl.trim()
        if (trimmed.isBlank()) return
        if (GoogleDriveProvider.isGoogleDriveUrl(trimmed)) {
            scope.launch {
                isImportingM3u = true
                GoogleDriveProvider.resolvePublicLink(trimmed).onSuccess { (directUrl, detectedName) ->
                    val name = titleHint.ifBlank { detectedName ?: StreamHistoryManager.findTitleForUrl(trimmed) ?: "GoogleDrive_Video" }
                    StreamDownloadManager.startDownload(context, directUrl, name.substringBeforeLast('.'))
                    Toast.makeText(context, "\u0110ang t\u1EA3i t\u1EEB Google Drive: $name", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Google Drive: ${err.message}", Toast.LENGTH_LONG).show()
                }
                isImportingM3u = false
            }
            return
        }
        val name = titleHint.ifBlank { StreamHistoryManager.findTitleForUrl(trimmed) ?: "Stream" }
        StreamDownloadManager.startDownload(context, trimmed, name)
        Toast.makeText(context, "\u0110ang t\u1EA3i: $name", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thanh chuyen doi 3 Tab: [Lich su] | [IPTV M3U] | [Kho Mang]
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LiquidGlassPill(
                active = activeTab == 0,
                onClick = { activeTab = 0 },
                modifier = Modifier.weight(1f)
            ) {
                Icon(AppIcons.History, contentDescription = null, tint = if (activeTab == 0) Color.White else style.textPrimary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "L\u1ECBch s\u1EED",
                    color = if (activeTab == 0) Color.White else style.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            LiquidGlassPill(
                active = activeTab == 1,
                onClick = { activeTab = 1 },
                modifier = Modifier.weight(1f)
            ) {
                Icon(AppIcons.PlaylistPlay, contentDescription = null, tint = if (activeTab == 1) Color.White else style.textPrimary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "IPTV",
                    color = if (activeTab == 1) Color.White else style.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            LiquidGlassPill(
                active = activeTab == 2,
                onClick = { activeTab = 2 },
                modifier = Modifier.weight(1.1f)
            ) {
                Icon(AppIcons.Cloud, contentDescription = null, tint = if (activeTab == 2) Color.White else style.textPrimary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Kho M\u1EA1ng",
                    color = if (activeTab == 2) Color.White else style.textPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Khung hien thi tien trinh tai video (khi co tac vu dang tai hoac vua tai xong)
        if (downloadTasks.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(style.cardSurfaceColor)
                    .border(0.9.dp, palette.primaryGlow.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                downloadTasks.take(3).forEach { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = AppIcons.CloudDownload,
                                contentDescription = null,
                                tint = when {
                                    task.isFailed -> Color(0xFFFF5C8A)
                                    task.isCompleted -> Color(0xFF34D399)
                                    else -> palette.primaryGlow
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = task.title,
                                color = style.textPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (task.speedText.isNotBlank()) {
                                Text(
                                    text = task.speedText,
                                    color = palette.primaryGlow,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (task.isRunning) StreamDownloadManager.cancelDownload(task.url)
                                    else StreamDownloadManager.dismissTask(task.url)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "H\u1EE7y", tint = style.textSecondary, modifier = Modifier.size(15.dp))
                            }
                        }

                        if (task.isRunning) {
                            if (task.progressPercent in 0..100) {
                                LinearProgressIndicator(
                                    progress = task.progressPercent / 100f,
                                    color = palette.primaryGlow,
                                    trackColor = style.textSecondary.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = palette.primaryGlow,
                                    trackColor = style.textSecondary.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }

                        Text(
                            text = if (task.isRunning && task.progressPercent >= 0) "${task.statusMessage} (${task.progressPercent}%)" else task.statusMessage,
                            color = if (task.isFailed) Color(0xFFFF5C8A) else style.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (isImportingM3u) {
            LinearProgressIndicator(color = palette.primaryGlow, modifier = Modifier.fillMaxWidth())
        }

        if (activeTab == 0) {
            // ================= TAB 0: NHAP URL + TAI + LICH SU + TIM KIEM + SAO LUU =================
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL video, HLS .m3u8 ho\u1EB7c link Google Drive") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = customTitle,
                onValueChange = { customTitle = it },
                label = { Text("T\u00EAn g\u1EE3i nh\u1EDB (t\u00F9y ch\u1ECDn)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassPill(
                    active = true,
                    onClick = { launchStream(url, customTitle) },
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ph\u00E1t ngay", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }

                LiquidGlassPill(
                    active = false,
                    onClick = {
                        if (url.isNotBlank()) triggerStreamDownload(url, customTitle)
                        else Toast.makeText(context, "H\u00E3y nh\u1EADp URL c\u1EA7n t\u1EA3i", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(AppIcons.CloudDownload, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("T\u1EA3i v\u1EC1", color = style.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
            }

            // Hang dieu khien lich su: Sao luu / Khoi phuc / Don lich su
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sao l\u01B0u",
                        color = palette.primaryGlow,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.primaryGlow.copy(alpha = 0.14f))
                            .clickable { exportBackupLauncher.launch("VideoPlayerPro_Streams.json") }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                    Text(
                        text = "Kh\u00F4i ph\u1EE5c",
                        color = style.textPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(style.textPrimary.copy(alpha = 0.08f))
                            .clickable { importBackupLauncher.launch(arrayOf("application/json", "*/*")) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }

                if (streamHistory.isNotEmpty()) {
                    Text(
                        text = "D\u1ECDn l\u1ECBch s\u1EED",
                        color = Color(0xFFFF5C8A),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                StreamHistoryManager.clearUnpinnedHistory(context)
                                Toast.makeText(context, "\u0110\u00E3 d\u1ECDn l\u1ECBch s\u1EED (gi\u1EEF k\u00EAnh \u0111\u00E3 ghim)", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (streamHistory.isNotEmpty()) {
                // O tim kiem nhanh trong lich su Stream
                OutlinedTextField(
                    value = historySearchQuery,
                    onValueChange = { historySearchQuery = it },
                    placeholder = { Text("T\u00ECm trong l\u1ECBch s\u1EED Stream...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (historySearchQuery.isNotBlank()) {
                        {
                            IconButton(onClick = { historySearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = style.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredHistory = remember(streamHistory, historySearchQuery) {
                    if (historySearchQuery.isBlank()) streamHistory
                    else streamHistory.filter {
                        it.customName.contains(historySearchQuery, ignoreCase = true) ||
                            it.url.contains(historySearchQuery, ignoreCase = true)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredHistory, key = { it.id }) { item ->
                        val hasProgress = item.durationMs > 10_000L && item.lastPositionMs > 2000L
                        val progressFrac = if (hasProgress) {
                            (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val isDl = downloadTasks.any { it.url == item.url && it.isRunning }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 14.dp))
                                .background(
                                    if (item.isPinned) palette.primaryGlow.copy(alpha = 0.14f)
                                    else style.cardSurfaceColor
                                )
                                .border(
                                    width = if (item.isPinned) 1.1.dp else 0.7.dp,
                                    color = if (item.isPinned) palette.primaryGlow.copy(alpha = 0.65f) else style.cardBorderColor,
                                    shape = RoundedCornerShape(if (style.isProMonitor) 6.dp else 14.dp)
                                )
                                .clickable {
                                    url = item.url
                                    customTitle = item.customName
                                    launchStream(item.url, item.customName)
                                }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.customName,
                                        color = if (item.isPinned) palette.primaryGlow else style.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (hasProgress) {
                                            "${formatStreamPos(item.lastPositionMs)} / ${formatStreamPos(item.durationMs)} \u2022 ${item.url}"
                                        } else item.url,
                                        color = style.textSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isDl) StreamDownloadManager.cancelDownload(item.url)
                                        else triggerStreamDownload(item.url, item.customName)
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.CloudDownload,
                                        contentDescription = "T\u1EA3i v\u1EC1",
                                        tint = if (isDl) Color(0xFF34D399) else palette.primaryGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val pinned = StreamHistoryManager.togglePin(context, item.id)
                                        Toast.makeText(context, if (pinned) "\u0110\u00E3 ghim l\u00EAn \u0111\u1EA7u" else "\u0110\u00E3 b\u1ECF ghim", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.PushPin,
                                        contentDescription = "Ghim",
                                        tint = if (item.isPinned) Color(0xFFFFD54F) else style.textSecondary.copy(alpha = 0.55f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { renamingItem = item },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "\u0110\u1ED5i t\u00EAn", tint = style.textSecondary, modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = { StreamHistoryManager.deleteStream(context, item.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "X\u00F3a", tint = Color(0xFFFF5C8A), modifier = Modifier.size(16.dp))
                                }
                            }

                            if (hasProgress) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = progressFrac,
                                    color = palette.primaryGlow,
                                    trackColor = style.textSecondary.copy(alpha = 0.20f),
                                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }
        } else if (activeTab == 2) {
            CloudStorageTab(
                modifier = Modifier.weight(1f),
                onPlayCloudVideo = { proxyUrl, title -> onPlayClick(proxyUrl, title) }
            )
        } else {
            // ================= TAB 1: DANH SACH KENH IPTV (M3U / M3U8) =================
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassPill(
                    active = true,
                    onClick = { showImportM3uUrlDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Nh\u1EADp link M3U", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }

                LiquidGlassPill(
                    active = false,
                    onClick = { m3uFilePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ch\u1ECDn file .M3U", color = style.textPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                    Text("Hi\u1EC3n th\u1ECB ${filteredChannels.size}/${m3uChannels.size} k\u00EAnh", color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { StreamHistoryManager.clearM3uChannels(context) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("X\u00F3a danh s\u00E1ch", color = Color(0xFFFF5C8A), style = MaterialTheme.typography.labelSmall)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(style.cardSurfaceColor)
                                .border(0.7.dp, style.cardBorderColor, RoundedCornerShape(12.dp))
                                .clickable { launchStream(ch.url, ch.name) }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ch.name, color = style.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (ch.group.isNotBlank()) {
                                    Text(ch.group, color = palette.primaryGlow.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            IconButton(
                                onClick = { triggerStreamDownload(ch.url, ch.name) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(AppIcons.CloudDownload, contentDescription = "T\u1EA3i v\u1EC1", tint = style.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Ch\u01B0a c\u00F3 danh s\u00E1ch k\u00EAnh IPTV.\nB\u1EA5m 'Nh\u1EADp link M3U' ho\u1EB7c 'Ch\u1ECDn file .M3U' \u1EDF tr\u00EAn.",
                        color = style.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showImportM3uUrlDialog) {
        var m3uUrlInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportM3uUrlDialog = false },
            containerColor = palette.deepBgMid,
            title = { Text("Nh\u1EADp danh s\u00E1ch IPTV (M3U)", color = style.textPrimary, fontWeight = FontWeight.Bold) },
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
                                Toast.makeText(context, "\u0110\u00E3 n\u1EA1p ${list.size} k\u00EAnh IPTV", Toast.LENGTH_LONG).show()
                            }.onFailure { err ->
                                Toast.makeText(context, "L\u1ED7i: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                            isImportingM3u = false
                        }
                    }
                }) { Text("N\u1EA1p") }
            },
            dismissButton = { TextButton(onClick = { showImportM3uUrlDialog = false }) { Text("H\u1EE7y") } }
        )
    }

    renamingItem?.let { item ->
        var newNameInput by remember(item.id) { mutableStateOf(item.customName) }
        AlertDialog(
            onDismissRequest = { renamingItem = null },
            containerColor = palette.deepBgMid,
            title = { Text("\u0110\u1ED5i t\u00EAn lu\u1ED3ng Stream", color = style.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.url, color = style.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        Toast.makeText(context, "\u0110\u00E3 \u0111\u1ED5i t\u00EAn lu\u1ED3ng Stream", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("L\u01B0u") }
            },
            dismissButton = { TextButton(onClick = { renamingItem = null }) { Text("H\u1EE7y") } }
        )
    }

    if (showHttpWarningDialog) {
        AlertDialog(
            onDismissRequest = { showHttpWarningDialog = false },
            containerColor = palette.deepBgMid,
            title = { Text("C\u1EA3nh b\u00E1o k\u1EBFt n\u1ED1i HTTP", color = style.textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Li\u00EAn k\u1EBFt s\u1EED d\u1EE5ng giao th\u1EE9c http:// kh\u00F4ng m\u00E3 h\u00F3a. Ti\u1EBFp t\u1EE5c ph\u00E1t?", color = style.textPrimary) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        context.saveHttpWarnAcknowledged(true)
                        showHttpWarningDialog = false
                        val target = pendingUrlToPlay ?: url.trim()
                        val resolved = StreamHistoryManager.recordStreamPlayed(context, target, pendingTitleToSave)
                        onPlayClick(target, resolved)
                    }
                }) { Text("Ti\u1EBFp t\u1EE5c") }
            },
            dismissButton = { TextButton(onClick = { showHttpWarningDialog = false }) { Text("H\u1EE7y") } }
        )
    }
}