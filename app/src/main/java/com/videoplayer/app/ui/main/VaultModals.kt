package com.videoplayer.app.ui.main

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.videoplayer.app.data.local.*
import com.videoplayer.app.ui.components.AppIcons
import com.videoplayer.app.ui.components.LiquidGlassPill
import com.videoplayer.app.ui.components.LocalGlassPalette
import com.videoplayer.app.util.PinSecurityManager
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hộp thoại Mở khóa Kho Ẩn:
 * - Nếu nhập đúng PIN Thật -> Mở REAL_VAULT
 * - Nếu nhập đúng PIN Giả (Decoy PIN) -> Mở DECOY_VAULT (Hoàn toàn không để lộ Kho Thật!)
 */
@Composable
fun VaultUnlockPinDialog(
    activity: FragmentActivity,
    vaultSecState: VaultSecurityState,
    scope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    onUnlocked: (VaultAccessMode) -> Unit
) {
    val palette = LocalGlassPalette.current
    var pinInput by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    val isSettingNewPin = !vaultSecState.isConfigured
    var remainingLockSec by remember { mutableLongStateOf(0L) }

    LaunchedEffect(vaultSecState.lockoutUntilMs) {
        while (true) {
            val rem = ((vaultSecState.lockoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            remainingLockSec = rem
            if (rem <= 0L) break
            delay(1000L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.deepBgMid,
        title = {
            Text(
                if (isSettingNewPin) "🔐 Thiết lập Mã PIN Bảo Mật" else "🔒 Xác thực Lưu trữ nội bộ",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remainingLockSec > 0L) {
                    Text(
                        "⏳ Bạn đã nhập sai quá ${vaultSecState.failedAttempts} lần. Vui lòng thử lại sau ${remainingLockSec} giây.",
                        color = Color(0xFFFF5C8A),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        if (isSettingNewPin) "Nhập mã PIN mới (tối thiểu 6 ký tự):"
                        else "Nhập mã PIN của bạn:",
                        color = Color.White
                    )
                }
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    label = { Text("Mã PIN (>= 6 ký tự)") },
                    enabled = remainingLockSec <= 0L,
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
                if (!isSettingNewPin && vaultSecState.biometricEnabled && PinSecurityManager.canUseBiometric(activity) && remainingLockSec <= 0L) {
                    TextButton(onClick = {
                        onDismiss()
                        PinSecurityManager.showBiometricPrompt(
                            activity = activity,
                            onSuccess = {
                                scope.launch { context.recordVaultPinAttempt(true) }
                                onUnlocked(VaultAccessMode.REAL_VAULT)
                            },
                            onUsePinFallback = {}
                        )
                    }) {
                        Text("👆 Dùng Vân tay / Khuôn mặt", color = palette.primaryGlow)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = remainingLockSec <= 0L,
                onClick = {
                    if (isSettingNewPin) {
                        if (PinSecurityManager.isValidNewPin(pinInput)) {
                            scope.launch {
                                val salt = PinSecurityManager.generateSaltBase64()
                                val hash = PinSecurityManager.hashPinPbkdf2(pinInput, salt)
                                context.saveHashedVaultPin(hash, salt)
                                onUnlocked(VaultAccessMode.REAL_VAULT)
                            }
                        } else {
                            Toast.makeText(context, "Mã PIN bắt buộc tối thiểu 6 ký tự!", Toast.LENGTH_SHORT).show()
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

                        when {
                            isRealMatch -> {
                                scope.launch {
                                    context.recordVaultPinAttempt(true)
                                    onUnlocked(VaultAccessMode.REAL_VAULT)
                                }
                            }
                            isDecoyMatch -> {
                                // Mở Kho Giả (Decoy Vault) mượt mà như thật, không báo bất kỳ dấu hiệu khác biệt nào!
                                scope.launch {
                                    context.recordVaultPinAttempt(true)
                                    onUnlocked(VaultAccessMode.DECOY_VAULT)
                                }
                            }
                            else -> {
                                val nextFail = vaultSecState.failedAttempts + 1
                                val lockMs = PinSecurityManager.computeLockoutDurationMs(nextFail)
                                scope.launch {
                                    context.recordVaultPinAttempt(false, lockMs)
                                }
                                val msg = if (lockMs > 0L) {
                                    "Sai mã PIN ($nextFail lần)! Tạm khóa ${lockMs / 1000} giây."
                                } else {
                                    "Sai mã PIN! (Lần $nextFail/5)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            ) {
                Text(if (isSettingNewPin) "Lưu & Mở" else "Mở")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

/**
 * Cửa sổ Cài đặt riêng của Kho Ẩn (Opaque 100%):
 * - Nếu đang ở REAL_VAULT: Hiển thị đầy đủ Đổi PIN thật, Thiết lập PIN Giả (Decoy PIN), Hẹn giờ Tự động khóa (Auto-Lock), Bật/Tắt Vân tay, Khôi phục tất cả.
 * - Nếu đang ở DECOY_VAULT: ẨN HOÀN TOÀN mục Thiết lập Decoy PIN để người kiểm tra không thể phát hiện đây là Kho giả!
 */
@Composable
fun VaultOnlySettingsModal(
    accessMode: VaultAccessMode,
    vaultSecState: VaultSecurityState,
    scope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit,
    onOpenChangeRealPin: () -> Unit,
    onOpenConfigureDecoyPin: () -> Unit,
    onLockAndExit: () -> Unit
) {
    val palette = LocalGlassPalette.current
    val isDecoyMode = (accessMode == VaultAccessMode.DECOY_VAULT)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = palette.deepBgMid,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.4.dp,
                        brush = Brush.linearGradient(
                            listOf(palette.primaryGlow, Color.White.copy(alpha = 0.4f))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.Shield,
                            contentDescription = null,
                            tint = palette.primaryGlow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Cài đặt Lưu trữ bảo mật",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.12f))

                    // 1. Tự động khóa khi thoát ra nền (Auto-Lock Vault Timeout)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⏱️ Tự động khóa khi ứng dụng vào nền (Auto-Lock)", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Khi chuyển sang ứng dụng khác hoặc tắt màn hình, Kho sẽ tự động khóa sau:",
                            color = Color(0xFFA7B8D8),
                            style = MaterialTheme.typography.labelSmall
                        )
                        val timeoutOptions = listOf(
                            0 to "Khóa ngay (0s)",
                            15 to "15 giây",
                            30 to "30 giây",
                            60 to "1 phút",
                            300 to "5 phút"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            timeoutOptions.forEach { (sec, label) ->
                                LiquidGlassPill(
                                    active = vaultSecState.autoLockTimeoutSec == sec,
                                    onClick = {
                                        scope.launch { context.saveVaultAutoLockTimeoutSec(sec) }
                                    }
                                ) {
                                    Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // 2. Thay đổi Mã PIN chính (Chỉ cho phép khi ở Kho Thật)
                    if (!isDecoyMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenChangeRealPin()
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Key, contentDescription = null, tint = palette.primaryGlow)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Thay đổi Mã PIN chính", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Đặt lại mật khẩu PIN mở Kho thật (>= 6 ký tự)", color = Color(0xFFA7B8D8), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // 3. Thiết lập Mã PIN Giả Ngụy Trang (Decoy PIN) - Tuyệt đối ẩn khi đang ở trong Decoy Mode!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, palette.primaryGlow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .clickable {
                                    onDismiss()
                                    onOpenConfigureDecoyPin()
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.Shield, contentDescription = null, tint = Color(0xFF80FFDB))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mã PIN Giả Ngụy Trang (Decoy PIN)", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (vaultSecState.hasDecoyPin) "• Đang BẬT" else "• Chưa đặt",
                                        color = if (vaultSecState.hasDecoyPin) Color(0xFF80FFDB) else Color(0xFFFFD54F),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "Nhập PIN giả sẽ mở ra một Kho giả riêng biệt để bảo vệ Kho thật khi bị ép mở máy",
                                    color = Color(0xFFA7B8D8),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // 4. Công tắc Bật / Tắt mở khóa bằng Vân tay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = palette.primaryGlow)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mở khóa nhanh bằng Vân tay", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Sử dụng sinh trắc học thay vì gõ PIN", color = Color(0xFFA7B8D8), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(
                            checked = vaultSecState.biometricEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { context.saveVaultBiometricEnabled(enabled) }
                            }
                        )
                    }

                    // 5. Khôi phục toàn bộ video trong kho hiện tại
                    val vaultCount = remember(accessMode) {
                        VaultManager.loadVaultEntries(context, isDecoyMode).size
                    }
                    if (vaultCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.28f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        val allEntries = VaultManager.loadVaultEntries(context, isDecoyMode)
                                        var restored = 0
                                        allEntries.forEach { entry ->
                                            if (VaultManager.restoreVideoFromVault(context, entry.vaultId, isDecoyMode).isSuccess) {
                                                restored++
                                            }
                                        }
                                        onDismiss()
                                        Toast.makeText(context, "🔓 Đã khôi phục toàn bộ $restored video về thư mục gốc!", Toast.LENGTH_LONG).show()
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AppIcons.LockOpen, contentDescription = null, tint = Color(0xFF80FFDB))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Khôi phục tất cả ($vaultCount video)", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Trả toàn bộ video trong kho về lại thư mục gốc", color = Color(0xFFA7B8D8), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Button(
                        onClick = onLockAndExit,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primaryGlow),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF001F29), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Khóa & Thoát Lưu trữ nội bộ", color = Color(0xFF001F29), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/**
 * Hộp thoại Thiết lập / Xóa Mã PIN Giả (Decoy PIN)
 */
@Composable
fun ConfigureDecoyPinDialog(
    vaultSecState: VaultSecurityState,
    scope: CoroutineScope,
    context: Context,
    onDismiss: () -> Unit
) {
    val palette = LocalGlassPalette.current
    var realPinInput by remember { mutableStateOf("") }
    var decoyPinInput by remember { mutableStateOf("") }
    var confirmDecoyInput by remember { mutableStateOf("") }
    var showChars by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.deepBgMid,
        title = {
            Text("🛡️ Thiết lập Mã PIN Giả (Decoy PIN)", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Khi bạn nhập Mã PIN Giả này ở màn hình mở khóa, ứng dụng sẽ mở ra một 'Kho Giả' độc lập (trống hoặc chỉ chứa vài video vô hại) để bảo vệ tuyệt đối Kho Thật của bạn.",
                    color = Color(0xFFA7B8D8),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = realPinInput,
                    onValueChange = { realPinInput = it },
                    label = { Text("Xác nhận Mã PIN Thật hiện tại") },
                    visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showChars = !showChars }) {
                            Icon(
                                imageVector = if (showChars) AppIcons.VisibilityOff else AppIcons.Visibility,
                                contentDescription = "Hiện/Ẩn",
                                tint = Color.White
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = decoyPinInput,
                    onValueChange = { decoyPinInput = it },
                    label = { Text("Mã PIN Giả mới (>= 6 ký tự, khác PIN thật)") },
                    visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmDecoyInput,
                    onValueChange = { confirmDecoyInput = it },
                    label = { Text("Nhập lại Mã PIN Giả mới") },
                    visualTransformation = if (showChars) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (vaultSecState.hasDecoyPin) {
                    TextButton(
                        onClick = {
                            val validReal = PinSecurityManager.verifyPinConstantTime(
                                inputPin = realPinInput,
                                storedHashBase64 = vaultSecState.pinHash ?: "",
                                storedSaltBase64 = vaultSecState.pinSalt ?: ""
                            )
                            if (!validReal) {
                                Toast.makeText(context, "Hãy nhập đúng Mã PIN Thật để xóa PIN Giả!", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            scope.launch {
                                context.clearDecoyPin()
                                onDismiss()
                                Toast.makeText(context, "Đã tắt Mã PIN Giả (Decoy PIN)!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("🗑️ Xóa / Tắt Mã PIN Giả hiện tại", color = Color(0xFFFF5C8A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val validReal = PinSecurityManager.verifyPinConstantTime(
                    inputPin = realPinInput,
                    storedHashBase64 = vaultSecState.pinHash ?: "",
                    storedSaltBase64 = vaultSecState.pinSalt ?: ""
                )
                if (!validReal) {
                    Toast.makeText(context, "Mã PIN Thật không đúng!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!PinSecurityManager.isValidNewPin(decoyPinInput)) {
                    Toast.makeText(context, "Mã PIN Giả phải từ 6 ký tự trở lên!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (decoyPinInput.trim() != confirmDecoyInput.trim()) {
                    Toast.makeText(context, "Hai ô Mã PIN Giả không khớp nhau!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (PinSecurityManager.verifyPinConstantTime(decoyPinInput, vaultSecState.pinHash ?: "", vaultSecState.pinSalt ?: "")) {
                    Toast.makeText(context, "Mã PIN Giả không được trùng với Mã PIN Thật!", Toast.LENGTH_LONG).show()
                    return@Button
                }
                scope.launch {
                    val salt = PinSecurityManager.generateSaltBase64()
                    val hash = PinSecurityManager.hashPinPbkdf2(decoyPinInput, salt)
                    context.saveHashedDecoyPin(hash, salt)
                    onDismiss()
                    Toast.makeText(context, "✅ Đã kích hoạt Mã PIN Giả (Decoy PIN) thành công!", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Lưu PIN Giả")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}