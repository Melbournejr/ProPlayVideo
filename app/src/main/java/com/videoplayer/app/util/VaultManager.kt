package com.videoplayer.app.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.Keep
import com.google.gson.Gson
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Keep
data class VaultEntryMeta(
    val vaultId: String,
    val originalDisplayName: String,
    val originalPath: String,
    val originalFolder: String,
    val originalSize: Long,
    val duration: Long,
    val mimeType: String,
    val encryptedFileName: String,
    val dateAdded: Long,
    val storageType: String = "RAW_SANDBOX" // "RAW_SANDBOX" (Không mã hóa, tốc độ tức thì) hoặc "AES_GCM" (Cũ)
)

@Keep
data class VaultIndexData(
    val entries: MutableList<VaultEntryMeta> = mutableListOf()
)

sealed class VaultHideOutcome {
    data class Success(val meta: VaultEntryMeta) : VaultHideOutcome()
    data class NeedsDeleteConfirmation(val meta: VaultEntryMeta, val pendingIntent: PendingIntent, val originalPath: String) : VaultHideOutcome()
    data class Error(val message: String) : VaultHideOutcome()
}

object VaultManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "VideoPlayerPro_AES256_VaultKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    val MAGIC_HEADER = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    const val HEADER_SIZE = 16
    const val PLAIN_CHUNK_SIZE = 64 * 1024
    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_LENGTH_BYTES = 16
    const val GCM_TAG_LENGTH_BITS = 128
    const val ENCRYPTED_CHUNK_OVERHEAD = GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES
    const val ENCRYPTED_CHUNK_TOTAL_SIZE = PLAIN_CHUNK_SIZE + ENCRYPTED_CHUNK_OVERHEAD

    private val gson = Gson()

    /**
     * Thư mục cô lập trong App Sandbox (/Android/data/com.videoplayer.app/files/.vault):
     * 1. Nằm cùng phân vùng bộ nhớ chia sẻ nên File.renameTo() diễn ra tức thì (0.01 giây cho file 5GB).
     * 2. Được Android 11+ khóa cứng quyền đọc đối với mọi ứng dụng duyệt file và Gallery bên ngoài.
     * 3. Có sẵn file .nomedia và đổi đuôi .vpv để ẩn tuyệt đối khỏi MediaScanner.
     */
    fun getSandboxVaultDir(context: Context): File {
        val extDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(extDir, ".vault")
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
        return dir
    }

    fun getLegacyInternalVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getIndexFile(context: Context): File = File(getLegacyInternalVaultDir(context), "vault_index.json")

    fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun loadVaultEntries(context: Context): List<VaultEntryMeta> {
        val idxFile = getIndexFile(context)
        if (!idxFile.exists()) return emptyList()
        return try {
            val json = idxFile.readText(Charsets.UTF_8)
            gson.fromJson(json, VaultIndexData::class.java)?.entries ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun saveVaultEntries(context: Context, entries: List<VaultEntryMeta>) {
        val idxFile = getIndexFile(context)
        val json = gson.toJson(VaultIndexData(entries.toMutableList()))
        idxFile.writeText(json, Charsets.UTF_8)
    }

    fun getVaultVideosAsVideoItems(context: Context): List<VaultEntryMeta> = loadVaultEntries(context)

    fun resolveVaultFile(context: Context, meta: VaultEntryMeta): File {
        val sandboxFile = File(getSandboxVaultDir(context), meta.encryptedFileName)
        if (sandboxFile.exists()) return sandboxFile
        return File(getLegacyInternalVaultDir(context), meta.encryptedFileName)
    }

    fun toVideoItem(context: Context, meta: VaultEntryMeta): VideoItem {
        val targetFile = resolveVaultFile(context, meta)
        // Nếu là RAW_SANDBOX (không mã hóa), truyền đường dẫn trực tiếp để ExoPlayer phát mượt tốc độ gốc!
        // Nếu là file AES_GCM cũ, vẫn dùng scheme vault:// để giải mã stream.
        val playPath = if (meta.storageType == "RAW_SANDBOX") {
            targetFile.absolutePath
        } else {
            "vault://${targetFile.absolutePath}"
        }
        return VideoItem(
            id = "vault_${meta.vaultId}",
            title = meta.originalDisplayName.substringBeforeLast('.'),
            displayName = meta.originalDisplayName,
            path = playPath,
            duration = meta.duration,
            size = meta.originalSize,
            folder = "🔒 Kho Riêng Tư",
            dateAdded = meta.dateAdded,
            mimeType = meta.mimeType
        )
    }

    /**
     * Đưa video vào Kho Ẩn KHÔNG CẦN MÃ HÓA:
     * - Bước 1: Thử di chuyển tức thì bằng File.renameTo() sang /Android/data/com.videoplayer.app/files/.vault/ (Mất 0.005s)
     * - Bước 2: Nếu khác phân vùng hoặc bị chặn rename trực tiếp, copy luồng thẳng tốc độ cao (không tốn CPU mã hóa)
     * - Bước 3: Xóa sạch bản gốc khỏi MediaStore. Nếu file của app khác (như Zalo) cần xác nhận xóa trên Android 11+,
     *   trả về NeedsDeleteConfirmation để hiển thị bảng xác nhận chính thức của hệ thống Android.
     */
    suspend fun hideVideoToVaultFast(context: Context, video: VideoItem): VaultHideOutcome = withContext(Dispatchers.IO) {
        try {
            val vaultId = "${System.currentTimeMillis()}_${video.id}"
            val hiddenFileName = ".hidden_$vaultId.vpv"
            val destFile = File(getSandboxVaultDir(context), hiddenFileName)
            val srcFile = File(video.path)

            val idLong = video.id.toLongOrNull()
            val contentUri: Uri? = if (idLong != null) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
            } else null

            var movedByRename = false
            // 1. Thử di chuyển tức thì O(1) trên cùng phân vùng (Zero-Copy)
            if (srcFile.exists() && srcFile.renameTo(destFile)) {
                movedByRename = true
            } else {
                // 2. Nếu hệ điều hành chặn rename trực tiếp (ví dụ file trong thư mục Zalo trên Android 11+),
                // thực hiện copy luồng nhị phân thuần túy (nhanh gấp 8 lần so với mã hóa AES)
                val inputStream = when {
                    contentUri != null -> context.contentResolver.openInputStream(contentUri)
                    srcFile.exists() -> FileInputStream(srcFile)
                    else -> null
                } ?: return@withContext VaultHideOutcome.Error("Không thể đọc tệp video gốc")

                inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buf = ByteArray(256 * 1024) // Buffer lớn 256KB copy cực nhanh
                        var r: Int
                        while (input.read(buf).also { r = it } != -1) {
                            output.write(buf, 0, r)
                        }
                        output.fd.sync()
                    }
                }
            }

            if (!destFile.exists() || destFile.length() <= 0L) {
                return@withContext VaultHideOutcome.Error("Không thể tạo tệp trong Kho Ẩn")
            }

            val meta = VaultEntryMeta(
                vaultId = vaultId,
                originalDisplayName = video.displayName,
                originalPath = video.path,
                originalFolder = video.folder,
                originalSize = destFile.length(),
                duration = video.duration,
                mimeType = video.mimeType.ifBlank { "video/mp4" },
                encryptedFileName = hiddenFileName,
                dateAdded = System.currentTimeMillis() / 1000L,
                storageType = "RAW_SANDBOX"
            )

            val entries = loadVaultEntries(context).toMutableList()
            entries.add(0, meta)
            saveVaultEntries(context, entries)

            // 3. Xóa sạch bản gốc khỏi danh sách bên ngoài & MediaStore
            if (movedByRename) {
                try {
                    if (contentUri != null) context.contentResolver.delete(contentUri, null, null)
                } catch (_: Exception) {}
                MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
                return@withContext VaultHideOutcome.Success(meta)
            } else {
                var deletedOriginal = false
                try {
                    if (contentUri != null) {
                        val rows = context.contentResolver.delete(contentUri, null, null)
                        if (rows > 0) deletedOriginal = true
                    }
                } catch (_: SecurityException) {
                    deletedOriginal = false
                } catch (_: Exception) {}

                if (!deletedOriginal && srcFile.exists()) {
                    deletedOriginal = srcFile.delete()
                }

                if (deletedOriginal || !srcFile.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
                    return@withContext VaultHideOutcome.Success(meta)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && contentUri != null) {
                    val pi = MediaStore.createDeleteRequest(context.contentResolver, listOf(contentUri))
                    return@withContext VaultHideOutcome.NeedsDeleteConfirmation(meta, pi, video.path)
                }
                return@withContext VaultHideOutcome.Success(meta)
            }
        } catch (e: Exception) {
            VaultHideOutcome.Error(e.message ?: "Lỗi không xác định")
        }
    }

    /**
     * Tương thích ngược với các lệnh gọi cũ
     */
    suspend fun moveVideoToVault(context: Context, video: VideoItem): Result<VaultEntryMeta> {
        return when (val out = hideVideoToVaultFast(context, video)) {
            is VaultHideOutcome.Success -> Result.success(out.meta)
            is VaultHideOutcome.NeedsDeleteConfirmation -> Result.success(out.meta)
            is VaultHideOutcome.Error -> Result.failure(Exception(out.message))
        }
    }

    /**
     * Khôi phục video từ Kho Ẩn ra lại thư mục gốc (hoặc Movies/VideoPlayerPro)
     */
    suspend fun restoreVideoFromVault(context: Context, vaultVideoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanId = vaultVideoId.removePrefix("vault_")
            val entries = loadVaultEntries(context).toMutableList()
            val meta = entries.find { it.vaultId == cleanId }
                ?: return@withContext Result.failure(Exception("Không tìm thấy video trong Kho"))

            val vaultFile = resolveVaultFile(context, meta)
            if (!vaultFile.exists()) return@withContext Result.failure(Exception("Tệp trong Kho không tồn tại"))

            // Nếu là file RAW_SANDBOX (không mã hóa), thử rename thẳng về vị trí gốc chỉ trong 0.01 giây!
            if (meta.storageType == "RAW_SANDBOX") {
                val origFile = File(meta.originalPath)
                val origParent = origFile.parentFile
                if (origParent != null && (origParent.exists() || origParent.mkdirs())) {
                    val targetRestoreFile = if (origFile.exists()) {
                        File(origParent, "Restored_${meta.originalDisplayName}")
                    } else {
                        origFile
                    }
                    if (vaultFile.renameTo(targetRestoreFile)) {
                        entries.removeAll { it.vaultId == cleanId }
                        saveVaultEntries(context, entries)
                        MediaScannerConnection.scanFile(context, arrayOf(targetRestoreFile.absolutePath), arrayOf(meta.mimeType), null)
                        return@withContext Result.success(targetRestoreFile.absolutePath)
                    }
                }
            }

            // Nếu không rename trực tiếp được hoặc là file AES_GCM cũ -> xuất qua MediaStore Movies
            var restoredPathDescription = "Movies/VideoPlayerPro/${meta.originalDisplayName}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, meta.originalDisplayName)
                    put(MediaStore.Video.Media.MIME_TYPE, meta.mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoPlayerPro")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Không thể tạo tệp khôi phục trong MediaStore"))

                resolver.openOutputStream(uri)?.use { out ->
                    if (meta.storageType == "RAW_SANDBOX") {
                        FileInputStream(vaultFile).use { input -> input.copyTo(out, 256 * 1024) }
                    } else {
                        decryptStreamToOutput(vaultFile, getOrCreateSecretKey(), out)
                    }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).apply { mkdirs() }
                val destFile = File(targetDir, meta.originalDisplayName)
                FileOutputStream(destFile).use { out ->
                    if (meta.storageType == "RAW_SANDBOX") {
                        FileInputStream(vaultFile).use { input -> input.copyTo(out, 256 * 1024) }
                    } else {
                        decryptStreamToOutput(vaultFile, getOrCreateSecretKey(), out)
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                restoredPathDescription = destFile.absolutePath
            }

            vaultFile.delete()
            entries.removeAll { it.vaultId == cleanId }
            saveVaultEntries(context, entries)
            Result.success(restoredPathDescription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decryptStreamToOutput(encFile: File, key: SecretKey, output: java.io.OutputStream) {
        RandomAccessFile(encFile, "r").use { raf ->
            val header = ByteArray(HEADER_SIZE)
            raf.readFully(header)
            val bb = ByteBuffer.wrap(header)
            bb.position(4)
            val totalPlainSize = bb.long
            var remaining = totalPlainSize

            while (remaining > 0L) {
                val chunkPlainSize = minOf(PLAIN_CHUNK_SIZE.toLong(), remaining).toInt()
                val iv = ByteArray(GCM_IV_LENGTH)
                val cipherBytes = ByteArray(chunkPlainSize + GCM_TAG_LENGTH_BYTES)
                raf.readFully(iv)
                raf.readFully(cipherBytes)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val plain = cipher.doFinal(cipherBytes)
                output.write(plain)
                remaining -= plain.size
            }
            output.flush()
        }
    }
}