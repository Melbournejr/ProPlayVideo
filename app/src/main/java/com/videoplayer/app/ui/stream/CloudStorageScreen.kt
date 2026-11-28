package com.videoplayer.app.ui.stream

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.network.DuplicateDownloadMatch
import com.videoplayer.app.network.StreamDownloadManager
import com.videoplayer.app.network.cloud.*
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassIconButton
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassStyle
import kotlinx.coroutines.launch

/**
 * Lop giao dien hop nhat cho 3 chuan Kho Mang nhe nhat:
 * - Google Drive (OAuth2 API v3)
 * - WebDAV (PikPak, Nextcloud, NAS, OpenList/Alist)
 * - SMB (Chia se Windows / NAS trong mang LAN)
 */
@Composable
fun CloudStorageTab(
    modifier: Modifier = Modifier,
    onPlayCloudVideo: (proxyUrl: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val style = LocalGlassStyle.current
    val palette = style.palette
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        CloudSourceManager.ensureInitialized(context)
        StreamDownloadManager.ensureInitialized(context)
    }
    val downloadedRecords by StreamDownloadManager.downloadedRecords.collectAsState()
    var pendingCloudDuplicate by remember { mutableStateOf<Triple<CloudSourceConfig, CloudEntry, DuplicateDownloadMatch>?>(null) }
    val sources by CloudSourceManager.sources.collectAsState()

    var activeSourceId by remember { mutableStateOf<String?>(null) }
    val activeSource = remember(sources, activeSourceId) { sources.find { it.id == activeSourceId } }

    // Ngan xep dieu huong thu muc: Pair(pathOrId, displayName)
    val folderStack = remember { mutableStateListOf<Pair<String, String>>() }
    var entries by remember { mutableStateOf<List<CloudEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showEditorDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<CloudSourceConfig?>(null) }

    fun loadCurrentFolder(source: CloudSourceConfig) {
        val currentPath = folderStack.lastOrNull()?.first.orEmpty()
        scope.launch {
            isLoading = true
            errorMessage = null
            CloudRepository.listDirectory(context, source, currentPath)
                .onSuccess { list -> entries = list }
                .onFailure { err ->
                    entries = emptyList()
                    errorMessage = err.message ?: "Kh\u00F4ng th\u1EC3 t\u1EA3i danh s\u00E1ch th\u01B0 m\u1EE5c"
                }
            isLoading = false
        }
    }

    LaunchedEffect(activeSourceId, folderStack.size) {
        val src = activeSource
        if (src != null) {
            loadCurrentFolder(src)
        }
    }

    BackHandler(enabled = activeSource != null) {
        if (folderStack.size > 1) {
            folderStack.removeAt(folderStack.lastIndex)
        } else {
            folderStack.clear()
            activeSourceId = null
            entries = emptyList()
            errorMessage = null
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (activeSource == null) {
            // ================= DANH SACH KHO MANG DA LUU =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kho M\u1EA1ng (${sources.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = style.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                LiquidGlassPill(
                    active = true,
                    onClick = {
                        editingSource = null
                        showEditorDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Th\u00EAm kho", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (sources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(style.cardSurfaceColor)
                        .border(0.8.dp, style.cardBorderColor, RoundedCornerShape(16.dp))
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(AppIcons.Cloud, contentDescription = null, tint = palette.primaryGlow, modifier = Modifier.size(40.dp))
                        Text(
                            text = "K\u1EBFt n\u1ED1i Google Drive, WebDAV ho\u1EB7c th\u01B0 m\u1EE5c chia s\u1EBB SMB (Windows/NAS) \u0111\u1EC3 xem v\u00E0 t\u1EA3i video tr\u1EF1c ti\u1EBFp.",
                            color = style.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources, key = { it.id }) { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 14.dp))
                                .background(style.cardSurfaceColor)
                                .border(0.8.dp, style.cardBorderColor, RoundedCornerShape(if (style.isProMonitor) 6.dp else 14.dp))
                                .clickable {
                                    folderStack.clear()
                                    val rootPath = when (src.type) {
                                        CloudSourceType.GOOGLE_DRIVE -> "root"
                                        CloudSourceType.WEBDAV -> src.url
                                        CloudSourceType.SMB -> ""
                                    }
                                    folderStack.add(rootPath to src.name)
                                    activeSourceId = src.id
                                }
                                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primaryGlow.copy(alpha = 0.18f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = src.type.badge,
                                    color = palette.primaryGlow,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = src.name,
                                    color = style.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = when (src.type) {
                                    CloudSourceType.GOOGLE_DRIVE -> if (src.accessToken.isNotBlank() || src.refreshToken.isNotBlank()) "\u0110\u00E3 k\u1EBFt n\u1ED1i Google Drive API" else "Ch\u01B0a \u0111\u0103ng nh\u1EADp"
                                    CloudSourceType.WEBDAV -> src.url
                                    CloudSourceType.SMB -> "smb://${src.host}/${src.shareName}"
                                }
                                Text(
                                    text = subtitle,
                                    color = style.textSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingSource = src
                                    showEditorDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "S\u1EEDa", tint = style.textSecondary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { CloudSourceManager.deleteSource(context, src.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "X\u00F3a", tint = Color(0xFFFF5C8A), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // ================= TRINH DUYET THU MUC HOP NHAT (GDRIVE / WEBDAV / SMB) =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiquidGlassIconButton(
                    onClick = {
                        if (folderStack.size > 1) {
                            folderStack.removeAt(folderStack.lastIndex)
                        } else {
                            folderStack.clear()
                            activeSourceId = null
                            entries = emptyList()
                            errorMessage = null
                        }
                    },
                    size = 34.dp
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Quay l\u1EA1i", tint = style.textPrimary, modifier = Modifier.size(17.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderStack.lastOrNull()?.second ?: activeSource.name,
                        color = style.textPrimary,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeSource.type.badge} \u2022 ${entries.size} m\u1EE5c",
                        color = palette.primaryGlow,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                LiquidGlassIconButton(onClick = { loadCurrentFolder(activeSource) }, size = 34.dp) {
                    Icon(Icons.Default.Refresh, contentDescription = "L\u00E0m m\u1EDBi", tint = style.textPrimary, modifier = Modifier.size(17.dp))
                }
            }

            if (isLoading) {
                LinearProgressIndicator(color = palette.primaryGlow, modifier = Modifier.fillMaxWidth())
            }

            errorMessage?.let { err ->
                Text(
                    text = err,
                    color = Color(0xFFFF5C8A),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF5C8A).copy(alpha = 0.12f))
                        .padding(12.dp)
                )
            }

            if (!isLoading && errorMessage == null && entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    Text("Kh\u00F4ng c\u00F3 video ho\u1EB7c th\u01B0 m\u1EE5c con n\u00E0o \u1EDF \u0111\u00E2y.", color = style.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(entries, key = { it.id }) { item ->
                        val entryKey = remember(activeSource.id, item.pathOrId) { "cloud_${activeSource.id}_${item.pathOrId}" }
                        val entryTitle = remember(item.name) { item.name.substringBeforeLast('.') }
                        val alreadySaved = remember(downloadedRecords, entryKey, entryTitle) {
                            !item.isFolder && StreamDownloadManager.isRecordedDownloaded(entryKey, entryTitle)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (style.isProMonitor) 6.dp else 12.dp))
                                .background(style.cardSurfaceColor)
                                .border(0.7.dp, style.cardBorderColor, RoundedCornerShape(if (style.isProMonitor) 6.dp else 12.dp))
                                .clickable {
                                    if (item.isFolder) {
                                        folderStack.add(item.pathOrId to item.name)
                                    } else {
                                        scope.launch {
                                            isLoading = true
                                            CloudRepository.resolvePlayableUrl(context, activeSource, item)
                                                .onSuccess { proxyUrl -> onPlayCloudVideo(proxyUrl, item.name) }
                                                .onFailure { e -> Toast.makeText(context, "L\u1ED7i ph\u00E1t: ${e.message}", Toast.LENGTH_LONG).show() }
                                            isLoading = false
                                        }
                                    }
                                }
                                .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isFolder) AppIcons.Folder else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = palette.primaryGlow,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = item.name,
                                        color = style.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (item.isFolder) FontWeight.SemiBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (alreadySaved) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF34D399).copy(alpha = 0.20f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "\u0110\u00E3 t\u1EA3i",
                                                color = Color(0xFF34D399),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                if (!item.isFolder && item.sizeFormatted.isNotBlank()) {
                                    Text(
                                        text = item.sizeFormatted,
                                        color = style.textSecondary,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            if (!item.isFolder) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val match = StreamDownloadManager.checkAlreadyDownloaded(context, entryKey, entryTitle)
                                            if (match != null) {
                                                pendingCloudDuplicate = Triple(activeSource, item, match)
                                            } else {
                                                CloudRepository.resolvePlayableUrl(context, activeSource, item)
                                                    .onSuccess { proxyUrl ->
                                                        StreamDownloadManager.startDownload(
                                                            context = context,
                                                            rawUrl = proxyUrl,
                                                            suggestedTitle = entryTitle,
                                                            sourceKey = entryKey,
                                                            isRedownload = false
                                                        )
                                                        Toast.makeText(context, "\u0110ang t\u1EA3i: ${item.name}", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .onFailure { e -> Toast.makeText(context, "L\u1ED7i t\u1EA3i: ${e.message}", Toast.LENGTH_LONG).show() }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        AppIcons.CloudDownload,
                                        contentDescription = "T\u1EA3i v\u1EC1",
                                        tint = if (alreadySaved) Color(0xFF34D399) else palette.primaryGlow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingCloudDuplicate?.let { (src, item, match) ->
        val sizePart = match.sizeFormatted.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val timePart = match.timeFormatted.takeIf { it.isNotBlank() }?.let { "\nTh\u1EDDi \u0111i\u1EC3m t\u1EA3i: $it" }.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingCloudDuplicate = null },
            containerColor = palette.deepBgMid,
            title = { Text("Video \u0111\u00E3 \u0111\u01B0\u1EE3c t\u1EA3i tr\u01B0\u1EDBc \u0111\u00F3", color = style.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "T\u1EC7p '${match.fileName}'$sizePart \u0111\u00E3 c\u00F3 trong Movies/VideoPlayerPro.$timePart\n\nB\u1EA1n c\u00F3 mu\u1ED1n t\u1EA3i th\u00EAm m\u1ED9t b\u1EA3n n\u1EEFa kh\u00F4ng?",
                    color = style.textPrimary,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingCloudDuplicate = null
                    val entryKey = "cloud_${src.id}_${item.pathOrId}"
                    val entryTitle = item.name.substringBeforeLast('.')
                    scope.launch {
                        CloudRepository.resolvePlayableUrl(context, src, item)
                            .onSuccess { proxyUrl ->
                                StreamDownloadManager.startDownload(
                                    context = context,
                                    rawUrl = proxyUrl,
                                    suggestedTitle = entryTitle,
                                    sourceKey = entryKey,
                                    isRedownload = true
                                )
                                Toast.makeText(context, "\u0110ang t\u1EA3i l\u1EA1i: ${item.name}", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { e -> Toast.makeText(context, "L\u1ED7i t\u1EA3i: ${e.message}", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("V\u1EABn t\u1EA3i l\u1EA1i") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCloudDuplicate = null }) { Text("H\u1EE7y") }
            }
        )
    }

    if (showEditorDialog) {
        CloudSourceEditorDialog(
            initial = editingSource,
            onDismiss = { showEditorDialog = false },
            onSave = { cfg ->
                CloudSourceManager.upsertSource(context, cfg)
                showEditorDialog = false
            }
        )
    }
}

@Composable
private fun CloudSourceEditorDialog(
    initial: CloudSourceConfig?,
    onDismiss: () -> Unit,
    onSave: (CloudSourceConfig) -> Unit
) {
    val context = LocalContext.current
    val style = LocalGlassStyle.current
    val palette = style.palette
    val scope = rememberCoroutineScope()

    var selectedType by remember { mutableStateOf(initial?.type ?: CloudSourceType.WEBDAV) }
    var name by remember { mutableStateOf(initial?.name ?: "") }

    // WebDAV
    var webDavUrl by remember { mutableStateOf(initial?.url ?: "https://") }
    // SMB
    var smbHost by remember { mutableStateOf(initial?.host ?: "192.168.1.") }
    var smbPort by remember { mutableStateOf((initial?.port ?: 445).toString()) }
    var smbShare by remember { mutableStateOf(initial?.shareName ?: "") }
    var smbDomain by remember { mutableStateOf(initial?.domain ?: "") }
    // Shared Auth
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    // Google Drive OAuth2
    var gdClientId by remember { mutableStateOf(initial?.oauthClientId ?: "") }
    var gdClientSecret by remember { mutableStateOf(initial?.oauthClientSecret ?: "") }
    var gdAccessToken by remember { mutableStateOf(initial?.accessToken ?: "") }
    var gdRefreshToken by remember { mutableStateOf(initial?.refreshToken ?: "") }
    var gdExpiryMs by remember { mutableLongStateOf(initial?.tokenExpiryMs ?: 0L) }
    var gdAuthStatus by remember {
        mutableStateOf(if (!initial?.accessToken.isNullOrBlank() || !initial?.refreshToken.isNullOrBlank()) "\u0110\u00E3 k\u1EBFt n\u1ED1i t\u00E0i kho\u1EA3n" else "Ch\u01B0a \u0111\u0103ng nh\u1EADp")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.deepBgMid,
        title = {
            Text(
                text = if (initial == null) "Th\u00EAm Kho M\u1EA1ng" else "Ch\u1EC9nh s\u1EEDa Kho M\u1EA1ng",
                color = style.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CloudSourceType.values().forEach { type ->
                        val selected = (selectedType == type)
                        LiquidGlassPill(
                            active = selected,
                            onClick = { selectedType = type },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = type.badge,
                                color = if (selected) Color.White else style.textPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("T\u00EAn g\u1EE3i nh\u1EDB (vd: Drive C\u00E1 nh\u00E2n, NAS Nh\u00E0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (selectedType) {
                    CloudSourceType.WEBDAV -> {
                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = { webDavUrl = it },
                            label = { Text("URL WebDAV (https://.../dav)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("T\u00E0i kho\u1EA3n (b\u1ECF tr\u1ED1ng n\u1EBFu kh\u00F4ng c\u1EA7n)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("M\u1EADt kh\u1EA9u / App Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CloudSourceType.SMB -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = smbHost,
                                onValueChange = { smbHost = it },
                                label = { Text("IP m\u00E1y t\u00EDnh / NAS") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = smbPort,
                                onValueChange = { smbPort = it },
                                label = { Text("C\u1ED5ng") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = smbShare,
                            onValueChange = { smbShare = it },
                            label = { Text("T\u00EAn th\u01B0 m\u1EE5c chia s\u1EBB (vd: Movies)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("User Windows/NAS (tr\u1ED1ng = Guest)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("M\u1EADt kh\u1EA9u") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CloudSourceType.GOOGLE_DRIVE -> {
                        Text(
                            text = "T\u1EA1o OAuth Client lo\u1EA1i 'Desktop app' tr\u00EAn Google Cloud Console, b\u1EADt Google Drive API v\u00E0 d\u00E1n Client ID + Secret:",
                            color = style.textSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        OutlinedTextField(
                            value = gdClientId,
                            onValueChange = { gdClientId = it },
                            label = { Text("OAuth Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = gdClientSecret,
                            onValueChange = { gdClientSecret = it },
                            label = { Text("OAuth Client Secret") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LiquidGlassPill(
                            active = true,
                            onClick = {
                                if (gdClientId.isBlank() || gdClientSecret.isBlank()) {
                                    Toast.makeText(context, "Nh\u1EADp \u0111\u1EE7 Client ID v\u00E0 Client Secret tr\u01B0\u1EDBc", Toast.LENGTH_SHORT).show()
                                    return@LiquidGlassPill
                                }
                                val redirectUri = CloudStreamProxyServer.getOAuthRedirectUri()
                                gdAuthStatus = "\u0110ang ch\u1EDD x\u00E1c nh\u1EADn t\u1EEB tr\u00ECnh duy\u1EC7t..."
                                CloudStreamProxyServer.registerOAuthCallback { code, err ->
                                    if (!code.isNullOrBlank()) {
                                        scope.launch {
                                            GoogleDriveProvider.exchangeAuthCode(gdClientId, gdClientSecret, code, redirectUri)
                                                .onSuccess { (acc, ref, exp) ->
                                                    gdAccessToken = acc
                                                    if (!ref.isNullOrBlank()) gdRefreshToken = ref
                                                    gdExpiryMs = exp
                                                    gdAuthStatus = "\u0110\u0103ng nh\u1EADp th\u00E0nh c\u00F4ng! B\u1EA5m L\u01B0u."
                                                }
                                                .onFailure { e -> gdAuthStatus = "L\u1ED7i l\u1EA5y token: ${e.message}" }
                                        }
                                    } else {
                                        gdAuthStatus = "B\u1ECB h\u1EE7y (${err ?: "Cancel"})"
                                    }
                                }
                                val authUrl = GoogleDriveProvider.buildOAuthAuthorizeUrl(gdClientId, redirectUri)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                } catch (e: Exception) {
                                    gdAuthStatus = "Kh\u00F4ng m\u1EDF \u0111\u01B0\u1EE3c tr\u00ECnh duy\u1EC7t: ${e.message}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("\u0110\u0103ng nh\u1EADp Google Drive", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(text = gdAuthStatus, color = palette.primaryGlow, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val resolvedName = name.trim().ifBlank { selectedType.label }
                val cfg = CloudSourceConfig(
                    id = initial?.id ?: "cloud_${System.currentTimeMillis()}",
                    name = resolvedName,
                    type = selectedType,
                    url = webDavUrl.trim(),
                    host = smbHost.trim(),
                    port = smbPort.toIntOrNull() ?: 445,
                    shareName = smbShare.trim(),
                    username = username.trim(),
                    password = password,
                    domain = smbDomain.trim(),
                    oauthClientId = gdClientId.trim(),
                    oauthClientSecret = gdClientSecret.trim(),
                    accessToken = gdAccessToken,
                    refreshToken = gdRefreshToken,
                    tokenExpiryMs = gdExpiryMs
                )
                onSave(cfg)
            }) { Text("L\u01B0u") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("H\u1EE7y") } }
    )
}