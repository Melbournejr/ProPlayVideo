package com.videoplayer.app.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.Keep
import com.google.gson.Gson
import com.videoplayer.app.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    val storageType: String = "DIRECT_MOVED"
)

@Keep
data class VaultIndexData(
    val entries: MutableList<VaultEntryMeta> = mutableListOf()
)

sealed class VaultHideOutcome {
    data class Success(val meta: VaultEntryMeta) : VaultHideOutcome()
    object NeedsAllFilesPermission : VaultHideOutcome()
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

    fun getDirectVaultDir(): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dir = File(moviesDir, ".ProPlayVault")
        if (!dir.exists()) dir.mkdirs()
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            try { noMedia.createNewFile() } catch (_: Exception) {}
        }
        return dir
    }

    fun getSandboxVaultDir(context: Context): File {
        val extDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(extDir, ".vault")
        if (!dir.exists()) dir.mkdirs()
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

    fun getHiddenOriginalPaths(context: Context): Set<String> {
        return loadVaultEntries(context).map { it.originalPath }.toSet()
    }

    fun resolveVaultFile(context: Context, meta: VaultEntryMeta): File {
        val directFile = File(getDirectVaultDir(), meta.encryptedFileName)
        if (directFile.exists()) return directFile
        val sandboxFile = File(getSandboxVaultDir(context), meta.encryptedFileName)
        if (sandboxFile.exists()) return sandboxFile
        return File(getLegacyInternalVaultDir(context), meta.encryptedFileName)
    }

    fun toVideoItem(context: Context, meta: VaultEntryMeta): VideoItem {
        val targetFile = resolveVaultFile(context, meta)
        val playPath = if (meta.storageType == "AES_GCM") {
            "vault://${targetFile.absolutePath}"
        } else {
            targetFile.absolutePath
        }
        return VideoItem(
            id = "vault_${meta.vaultId}",
            title = meta.originalDisplayName.substringBeforeLast('.'),
            displayName = meta.originalDisplayName,
            path = playPath,
            duration = meta.duration,
            size = meta.originalSize,
            folder = "📁 Lưu trữ nội bộ",
            dateAdded = meta.dateAdded,
            mimeType = meta.mimeType
        )
    }

    /**
     * DI CHUYỂN TRỰC TIẾP 100% BẰNG LỆNH RENAME TRÊN CÙNG PHÂN VÙNG (0.001s - KHÔNG COPY - KHÔNG HỎI XÓA):
     * Yêu cầu quyền All Files (MANAGE_EXTERNAL_STORAGE) một lần duy nhất.
     * Khi có quyền, File.renameTo() đổi thẳng đường dẫn từ Download/Zalo sang .ProPlayVault trong 0.001s
     * và MediaScanner tự động gỡ đường dẫn cũ mà KHÔNG BAO GIỜ hiện bảng hỏi "Allow to delete videos?".
     */
    suspend fun hideVideoToVaultFast(context: Context, video: VideoItem): VaultHideOutcome = withContext(Dispatchers.IO) {
        try {
            if (!FileOperationHelper.hasAllFilesAccess()) {
                return@withContext VaultHideOutcome.NeedsAllFilesPermission
            }

            val srcFile = File(video.path)
            if (!srcFile.exists()) {
                return@withContext VaultHideOutcome.Error("Tệp gốc không tồn tại")
            }

            val vaultId = "${System.currentTimeMillis()}_${video.id}"
            val hiddenFileName = ".vault_$vaultId.vpv"
            val destFile = File(getDirectVaultDir(), hiddenFileName)

            // Đổi trực tiếp con trỏ tệp (Inode Rename) - 0.001 giây, không copy 1 byte nào!
            val renamed = srcFile.renameTo(destFile)
            if (!renamed || !destFile.exists()) {
                return@withContext VaultHideOutcome.Error("Không thể di chuyển trực tiếp tệp này")
            }

            // Báo cho MediaScanner biết đường dẫn cũ đã chuyển đi (Tự động cập nhật MediaStore ngầm mà KHÔNG gọi contentResolver.delete gây popup!)
            MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)

            val meta = VaultEntryMeta(
                vaultId = vaultId,
                originalDisplayName = video.displayName,
                originalPath = video.path,
                originalFolder = video.folder,
                originalSize = destFile.length().coerceAtLeast(video.size),
                duration = video.duration,
                mimeType = video.mimeType.ifBlank { "video/mp4" },
                encryptedFileName = hiddenFileName,
                dateAdded = System.currentTimeMillis() / 1000L,
                storageType = "DIRECT_MOVED"
            )

            val entries = loadVaultEntries(context).toMutableList()
            entries.add(0, meta)
            saveVaultEntries(context, entries)

            VaultHideOutcome.Success(meta)
        } catch (e: Exception) {
            VaultHideOutcome.Error(e.message ?: "Lỗi di chuyển tệp")
        }
    }

    suspend fun moveVideoToVault(context: Context, video: VideoItem): Result<VaultEntryMeta> {
        return when (val out = hideVideoToVaultFast(context, video)) {
            is VaultHideOutcome.Success -> Result.success(out.meta)
            is VaultHideOutcome.NeedsAllFilesPermission -> Result.failure(Exception("Cần cấp quyền Quản lý tất cả tệp"))
            is VaultHideOutcome.Error -> Result.failure(Exception(out.message))
        }
    }

    suspend fun deleteVideoFromVault(context: Context, vaultVideoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = vaultVideoId.removePrefix("vault_")
            val entries = loadVaultEntries(context).toMutableList()
            val meta = entries.find { it.vaultId == cleanId }
                ?: return@withContext Result.failure(Exception("Không tìm thấy video trong Kho"))

            val vaultFile = resolveVaultFile(context, meta)
            if (vaultFile.exists()) vaultFile.delete()

            entries.removeAll { it.vaultId == cleanId }
            saveVaultEntries(context, entries)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreVideoFromVault(context: Context, vaultVideoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanId = vaultVideoId.removePrefix("vault_")
            val entries = loadVaultEntries(context).toMutableList()
            val meta = entries.find { it.vaultId == cleanId }
                ?: return@withContext Result.failure(Exception("Không tìm thấy video trong Kho"))

            val vaultFile = resolveVaultFile(context, meta)
            if (!vaultFile.exists()) return@withContext Result.failure(Exception("Tệp trong Kho không tồn tại"))

            if (meta.storageType != "AES_GCM") {
                val origFile = File(meta.originalPath)
                val origParent = origFile.parentFile
                if (origParent != null && (origParent.exists() || origParent.mkdirs())) {
                    val destRestore = if (origFile.exists()) File(origParent, "Restored_${meta.originalDisplayName}") else origFile
                    if (vaultFile.renameTo(destRestore)) {
                        entries.removeAll { it.vaultId == cleanId }
                        saveVaultEntries(context, entries)
                        MediaScannerConnection.scanFile(context, arrayOf(destRestore.absolutePath), arrayOf(meta.mimeType), null)
                        return@withContext Result.success(destRestore.absolutePath)
                    }
                }

                val fallbackDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoPlayerPro").apply { mkdirs() }
                val fallbackFile = File(fallbackDir, meta.originalDisplayName)
                if (vaultFile.renameTo(fallbackFile)) {
                    entries.removeAll { it.vaultId == cleanId }
                    saveVaultEntries(context, entries)
                    MediaScannerConnection.scanFile(context, arrayOf(fallbackFile.absolutePath), arrayOf(meta.mimeType), null)
                    return@withContext Result.success(fallbackFile.absolutePath)
                }
            }

            val fallbackDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoPlayerPro").apply { mkdirs() }
            val destFile = File(fallbackDir, meta.originalDisplayName)
            FileOutputStream(destFile).use { out ->
                decryptStreamToOutput(vaultFile, getOrCreateSecretKey(), out)
            }
            vaultFile.delete()
            entries.removeAll { it.vaultId == cleanId }
            saveVaultEntries(context, entries)
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(meta.mimeType), null)
            Result.success(destFile.absolutePath)
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