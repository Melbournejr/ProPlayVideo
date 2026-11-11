package com.videoplayer.app.ui.main

import android.app.PendingIntent
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.videoplayer.app.data.local.VaultSecurityState
import com.videoplayer.app.data.local.recordVaultPinAttempt
import com.videoplayer.app.data.local.saveHashedVaultPin
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassCard
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.FileOperationHelper
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.VaultHideOutcome
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

enum class FileDialogType {
    NONE, INFO, RENAME, COPY, MOVE, DELETE, BATCH_COPY, BATCH_MOVE, BATCH_DELETE
}

@Composable
fun VideoItemActionMenuDialog(
    targetVideo: VideoItem,
    onDismiss: () -> Unit,
    onStartMultiSelect: (VideoItem) -> Unit,
    onVaultToggleAction: (VideoItem, Boolean) -> Unit,
    onSelectDialogType: (VideoItem, FileDialogType) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalGlassPalette.current
    val isVaultItem = (targetVideo.id.startsWith("vault_") || targetVideo.path.startsWith("vault://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.deepBgMid.copy(alpha = 0.96f),
        title = { Text(targetVideo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Chia sẻ video...", fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(Icons.Default.Share, null, tint = palette.primaryGlow) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        FileOperationHelper.shareVideo(context, targetVideo)
                    }
                )
                ListItem(
                    headlineContent = { Text("Chọn nhiều video...") },
                    leadingContent = { Icon(AppIcons.Checklist, null, tint = palette.primaryGlow) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onStartMultiSelect(targetVideo)
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            if (isVaultItem) "Khôi phục về thư mục gốc" else "Lưu trữ bảo mật",
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryGlow
                        )
                    },
                    leadingContent = {
                        Icon(if (isVaultItem) AppIcons.LockOpen else Icons.Default.Lock, null, tint = palette.primaryGlow)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onVaultToggleAction(targetVideo, isVaultItem)
                    }
                )
                ListItem(
                    headlineContent = { Text("Xem thông tin chi tiết") },
                    leadingContent = { Icon(Icons.Default.Info, null, tint = palette.primaryGlow) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onSelectDialogType(targetVideo, FileDialogType.INFO)
                    }
                )
                if (!isVaultItem) {
                    ListItem(
                        headlineContent = { Text("Đổi tên video") },
                        leadingContent = { Icon(Icons.Default.Edit, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            onSelectDialogType(targetVideo, FileDialogType.RENAME)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Sao chép (Copy) sang thư mục khác") },
                        leadingContent = { Icon(AppIcons.ContentCopy, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            onSelectDialogType(targetVideo, FileDialogType.COPY)
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Di chuyển (Move) sang thư mục khác") },
                        leadingContent = { Icon(AppIcons.DriveFileMove, null, tint = palette.primaryGlow) },
                        modifier = Modifier.clickable {
                            onDismiss()
                            onSelectDialogType(targetVideo, FileDialogType.MOVE)
                        }
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(
                            if (isVaultItem) "Xóa vĩnh viễn khỏi Lưu trữ" else "Xóa vĩnh viễn",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        onDismiss()
                        onSelectDialogType(targetVideo, FileDialogType.DELETE)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

@Composable
fun ConfirmHidePinDialog(
    vaultSecState: VaultSecurityState,
    scope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    onConfirmed: (isDecoyTarget: Boolean) -> Unit
) {
    val palette = LocalGlassPalette.current
    var pinInput by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    val isSettingNewPin = !vaultSecState.isConfigured

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.deepBgMid,
        title = {
            Text(
                if (isSettingNewPin) "🔐 Tạo Mã PIN Lưu Trữ" else "🔒 Xác nhận Mã PIN",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isSettingNewPin) "Thiết lập mã PIN (tối thiểu 6 ký tự):"
                    else "Nhập mã PIN của bạn để xác nhận:",
                    color = Color.White
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("Mã PIN (>= 6 ký tự)") },
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                imageVector = if (pinVisible) AppIcons.VisibilityOff else AppIcons.Visibility,
                                contentDescription = "Hiện/Ẩn ký tự",
                                tint = Color.White
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (isSettingNewPin) {
                    if (PinSecurityManager.isValidNewPin(pinInput)) {
                        scope.launch {
                            val salt = PinSecurityManager.generateSaltBase64()
                            val hash = PinSecurityManager.hashPinPbkdf2(pinInput, salt)
                            context.saveHashedVaultPin(hash, salt)
                            onConfirmed(false)
                        }
                    } else {
                        Toast.makeText(context, "Mã PIN phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val isRealMatch = PinSecurityManager.verifyPinConstantTime(
                        inputPin = pinInput,
                        storedHashBase64 = vaultSecState.pinHash ?: "",
                        storedSaltBase64 = vaultSecState.pinSalt ?: ""
                    )
                    val isDecoyMatch = vaultSecState.hasDecoyPin && PinSecurityManager.verifyPinConstantTime(
                        inputPin = pinInput,
                        storedHashBase64 = vaultSecState.decoyPinHash ?: "",
                        storedSaltBase64 = vaultSecState.decoyPinSalt ?: ""
                    )
                    if (isRealMatch || isDecoyMatch) {
                        scope.launch { context.recordVaultPinAttempt(true) }
                        onConfirmed(isDecoyMatch && !isRealMatch)
                    } else {
                        Toast.makeText(context, "Sai mã PIN!", Toast.LENGTH_SHORT).show()
                    }
                }
            }) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
fun FileOperationModals(
    dialogType: FileDialogType,
    activeVideo: VideoItem?,
    selectedVideoIds: List<String>,
    displayedVideos: List<VideoItem>,
    rawVideos: List<VideoItem>,
    folders: List<FolderSummary>,
    isVaultMode: Boolean,
    isDecoyVault: Boolean,
    scope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    onExitMultiSelect: () -> Unit,
    onRefresh: () -> Unit,
    onRequestMediaStorePermission: (PendingIntent, String?) -> Unit
) {
    if (dialogType == FileDialogType.NONE) return
    val palette = LocalGlassPalette.current

    when (dialogType) {
        FileDialogType.BATCH_DELETE -> {
            val targets = remember(selectedVideoIds, displayedVideos) {
                displayedVideos.filter { it.id in selectedVideoIds }
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = palette.deepBgMid,
                title = { Text(if (isVaultMode) "Xóa vĩnh viễn ${targets.size} video khỏi Lưu trữ?" else "Xóa ${targets.size} video đã chọn?") },
                text = { Text("Bạn có chắc chắn muốn xóa vĩnh viễn ${targets.size} video này khỏi bộ nhớ thiết bị không? Thao tác này không thể hoàn tác.") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            scope.launch {
                                var count = 0
                                if (isVaultMode) {
                                    targets.forEach { v ->
                                        if (VaultManager.deleteVideoFromVault(context, v.id, isDecoyVault).isSuccess) count++
                                    }
                                    Toast.makeText(context, "🗑️ Đã xóa vĩnh viễn $count video!", Toast.LENGTH_SHORT).show()
                                } else {
                                    targets.forEach { v ->
                                        if (FileOperationHelper.deleteVideo(context, v).isSuccess) count++
                                    }
                                    if (count < targets.size) {
                                        val pi = FileOperationHelper.createDeleteRequestPendingIntent(context, targets)
                                        if (pi != null) onRequestMediaStorePermission(pi, null)
                                    } else {
                                        Toast.makeText(context, "Đã xóa $count video!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                onDismiss()
                                onExitMultiSelect()
                                onRefresh()
                            }
                        }
                    ) { Text("Xóa vĩnh viễn") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                }
            )
        }
        FileDialogType.BATCH_COPY, FileDialogType.BATCH_MOVE -> {
            val isMove = (dialogType == FileDialogType.BATCH_MOVE)
            val targets = remember(selectedVideoIds, rawVideos) {
                rawVideos.filter { it.id in selectedVideoIds }
            }
            var targetPath by remember { mutableStateOf("/storage/emulated/0/Movies/VideoPlayerPro") }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = palette.deepBgMid,
                title = { Text(if (isMove) "Di chuyển ${targets.size} video tới..." else "Sao chép ${targets.size} video tới...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                            items(folders) { f ->
                                LiquidGlassCard(
                                    cornerRadius = 14.dp,
                                    onClick = { targetPath = f.folderPath },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(AppIcons.Folder, null, tint = palette.primaryGlow)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(f.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text(f.folderPath, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7B8D8))
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = targetPath,
                            onValueChange = { targetPath = it },
                            label = { Text("Tên thư mục đích") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            var ok = 0
                            targets.forEach { v ->
                                if (FileOperationHelper.copyOrMoveVideo(context, v, targetPath, isMove).isSuccess) ok++
                            }
                            Toast.makeText(context, "Hoàn tất $ok/${targets.size} video!", Toast.LENGTH_SHORT).show()
                            onDismiss()
                            onExitMultiSelect()
                            onRefresh()
                        }
                    }) { Text(if (isMove) "Di chuyển ngay" else "Sao chép ngay") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                }
            )
        }
        else -> {
            val video = activeVideo ?: return
            when (dialogType) {
                FileDialogType.INFO -> {
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        containerColor = palette.deepBgMid,
                        title = { Text("Thông tin chi tiết Video") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("• Tên tệp: ${video.displayName}")
                                Text("• Thư mục: ${video.folder}")
                                Text("• Nguồn: ${if (video.id.startsWith("vault_")) "Lưu trữ bảo mật" else video.path}")
                                Text("• Dung lượng: ${video.sizeFormatted} (${video.size} bytes)")
                                Text("• Thời lượng: ${video.durationFormatted}")
                                Text("• Định dạng: ${video.mimeType}")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = onDismiss) { Text("Đóng") }
                        }
                    )
                }
                FileDialogType.RENAME -> {
                    var newName by remember(video) { mutableStateOf(video.displayName.substringBeforeLast('.')) }
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        containerColor = palette.deepBgMid,
                        title = { Text("Đổi tên Video") },
                        text = {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("Tên mới") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch {
                                    FileOperationHelper.renameVideo(context, video, newName).onSuccess {
                                        Toast.makeText(context, "Đã đổi tên thành: $it", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        onRefresh()
                                    }.onFailure {
                                        val pi = FileOperationHelper.createWriteRequestPendingIntent(context, listOf(video))
                                        onDismiss()
                                        if (pi != null) onRequestMediaStorePermission(pi, newName)
                                    }
                                }
                            }) { Text("Lưu") }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismiss) { Text("Hủy") }
                        }
                    )
                }
                FileDialogType.COPY, FileDialogType.MOVE -> {
                    val isMove = (dialogType == FileDialogType.MOVE)
                    var targetPath by remember(video) {
                        mutableStateOf(File(video.path).parent ?: "/storage/emulated/0/Movies/VideoPlayerPro")
                    }
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        containerColor = palette.deepBgMid,
                        title = { Text(if (isMove) "Di chuyển Video tới..." else "Sao chép Video tới...") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                                    items(folders) { f ->
                                        LiquidGlassCard(
                                            cornerRadius = 14.dp,
                                            onClick = { targetPath = f.folderPath },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(AppIcons.Folder, null, tint = palette.primaryGlow)
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(f.name, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                    Text(f.folderPath, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA7B8D8))
                                                }
                                            }
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = targetPath,
                                    onValueChange = { targetPath = it },
                                    label = { Text("Thư mục đích") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch {
                                    FileOperationHelper.copyOrMoveVideo(context, video, targetPath, isMove).onSuccess {
                                        Toast.makeText(context, "Đã lưu tới: $it", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                        onRefresh()
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) { Text(if (isMove) "Di chuyển" else "Sao chép") }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismiss) { Text("Hủy") }
                        }
                    )
                }
                FileDialogType.DELETE -> {
                    val isVaultVid = video.id.startsWith("vault_") || video.path.startsWith("vault://")
                    AlertDialog(
                        onDismissRequest = onDismiss,
                        containerColor = palette.deepBgMid,
                        title = { Text(if (isVaultVid) "Xóa vĩnh viễn khỏi Lưu trữ" else "Xác nhận xóa vĩnh viễn") },
                        text = { Text("Bạn có chắc chắn muốn xóa vĩnh viễn '${video.displayName}' khỏi bộ nhớ thiết bị không?") },
                        confirmButton = {
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                onClick = {
                                    scope.launch {
                                        if (isVaultVid) {
                                            VaultManager.deleteVideoFromVault(context, video.id, isDecoyVault).onSuccess {
                                                Toast.makeText(context, "🗑️ Đã xóa vĩnh viễn!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                                onRefresh()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "Lỗi: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            FileOperationHelper.deleteVideo(context, video).onSuccess {
                                                Toast.makeText(context, "Đã xóa tệp!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                                onRefresh()
                                            }.onFailure {
                                                val pi = FileOperationHelper.createDeleteRequestPendingIntent(context, listOf(video))
                                                onDismiss()
                                                if (pi != null) onRequestMediaStorePermission(pi, null)
                                            }
                                        }
                                    }
                                }
                            ) { Text("Xóa vĩnh viễn") }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismiss) { Text("Hủy") }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}