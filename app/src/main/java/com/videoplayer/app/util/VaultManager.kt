package com.videoplayer.app.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
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
    val dateAdded: Long
)

@Keep
data class VaultIndexData(
    val entries: MutableList<VaultEntryMeta> = mutableListOf()
)

object VaultManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "VideoPlayerPro_AES256_VaultKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    // Định dạng File Mã Hóa Chunked AES-256-GCM (Hỗ trợ Random-Access Streaming Seek):
    // Header (16 bytes): Magic "VPV1" (4B) + PlainSize (8B) + ChunkPlainSize (4B = 65536)
    // Mỗi Chunk: IV (12B) + Ciphertext (<=65536B) + GCM Auth Tag (16B)
    val MAGIC_HEADER = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    const val HEADER_SIZE = 16
    const val PLAIN_CHUNK_SIZE = 64 * 1024 // 64 KB
    const val GCM_IV_LENGTH = 12
    const val GCM_TAG_LENGTH_BYTES = 16
    const valGCM_TAG_LENGTH_BITS = 128
    const val ENCRYPTED_CHUNK_OVERHEAD = GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES
    const val ENCRYPTED_CHUNK_TOTAL_SIZE = PLAIN_CHUNK_SIZE + ENCRYPTED_CHUNK_OVERHEAD

    private val gson = Gson()

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getIndexFile(context: Context): File = File(getVaultDir(context), "vault_index.json")

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

    fun toVideoItem(context: Context, meta: VaultEntryMeta): VideoItem {
        val encFile = File(getVaultDir(context), meta.encryptedFileName)
        return VideoItem(
            id = "vault_${meta.vaultId}",
            title = meta.originalDisplayName.substringBeforeLast('.'),
            displayName = meta.originalDisplayName,
            path = "vault://${encFile.absolutePath}",
            duration = meta.duration,
            size = meta.originalSize,
            folder = "🔒 Kho Riêng Tư",
            dateAdded = meta.dateAdded,
            mimeType = meta.mimeType
        )
    }

    /**
     * Mã hóa video vào context.filesDir/vault bằng AES-256-GCM, xác minh toàn vẹn rồi xóa bản gốc
     */
    suspend fun moveVideoToVault(context: Context, video: VideoItem): Result<VaultEntryMeta> = withContext(Dispatchers.IO) {
        try {
            val vaultId = "${System.currentTimeMillis()}_${video.id}"
            val encFileName = "vid_$vaultId.vpv"
            val destEncFile = File(getVaultDir(context), encFileName)
            val secretKey = getOrCreateSecretKey()

            val idLong = video.id.toLongOrNull()
            val contentUri = if (idLong != null) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, idLong)
            } else null

            val inputStream = when {
                contentUri != null -> context.contentResolver.openInputStream(contentUri)
                else -> File(video.path).takeIf { it.exists() }?.let { FileInputStream(it) }
            } ?: return@withContext Result.failure(Exception("Không thể mở file gốc để mã hóa"))

            var totalPlainBytes = 0L
            inputStream.use { input ->
                FileOutputStream(destEncFile).use { output ->
                    // Tạm ghi header 16 bytes (sẽ cập nhật lại tổng plainSize chính xác sau khi ghi xong)
                    output.write(ByteArray(HEADER_SIZE))

                    val buffer = ByteArray(PLAIN_CHUNK_SIZE)
                    while (true) {
                        var bytesRead = 0
                        while (bytesRead < PLAIN_CHUNK_SIZE) {
                            val r = input.read(buffer, bytesRead, PLAIN_CHUNK_SIZE - bytesRead)
                            if (r == -1) break
                            bytesRead += r
                        }
                        if (bytesRead <= 0) break
                        totalPlainBytes += bytesRead

                        val cipher = Cipher.getInstance(TRANSFORMATION)
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                        val iv = cipher.iv // 12 bytes
                        val cipherText = cipher.doFinal(buffer, 0, bytesRead)

                        output.write(iv)
                        output.write(cipherText)
                    }
                    output.fd.sync()
                }
            }

            // Ghi Header chính xác vào 16 bytes đầu
            RandomAccessFile(destEncFile, "rw").use { raf ->
                raf.seek(0L)
                val headerBuf = ByteBuffer.allocate(HEADER_SIZE)
                headerBuf.put(MAGIC_HEADER)
                headerBuf.putLong(totalPlainBytes)
                headerBuf.putInt(PLAIN_CHUNK_SIZE)
                raf.write(headerBuf.array())
            }

            // Xác minh giải mã thử Chunk đầu tiên để đảm bảo file mã hóa không lỗi trước khi xóa file gốc!
            if (!verifyEncryptedFile(destEncFile, totalPlainBytes, secretKey)) {
                destEncFile.delete()
                return@withContext Result.failure(Exception("Xác minh mã hóa thất bại, giữ nguyên file gốc"))
            }

            val meta = VaultEntryMeta(
                vaultId = vaultId,
                originalDisplayName = video.displayName,
                originalPath = video.path,
                originalFolder = video.folder,
                originalSize = totalPlainBytes,
                duration = video.duration,
                mimeType = video.mimeType.ifBlank { "video/mp4" },
                encryptedFileName = encFileName,
                dateAdded = System.currentTimeMillis() / 1000L
            )

            val entries = loadVaultEntries(context).toMutableList()
            entries.add(0, meta)
            saveVaultEntries(context, entries)

            // Xóa file gốc khỏi bộ nhớ ngoài sau khi đã xác minh mã hóa thành công
            try {
                if (contentUri != null) {
                    context.contentResolver.delete(contentUri, null, null)
                }
                val origFile = File(video.path)
                if (origFile.exists()) origFile.delete()
                MediaScannerConnection.scanFile(context, arrayOf(video.path), null, null)
            } catch (_: Exception) {}

            Result.success(meta)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun verifyEncryptedFile(file: File, expectedPlainSize: Long, key: SecretKey): Boolean {
        if (!file.exists() || file.length() <= HEADER_SIZE) return false
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(HEADER_SIZE)
                raf.readFully(header)
                val bb = ByteBuffer.wrap(header)
                val magic = ByteArray(4)
                bb.get(magic)
                if (!magic.contentEquals(MAGIC_HEADER)) return false
                val storedSize = bb.long
                if (storedSize != expectedPlainSize || storedSize <= 0L) return false

                // Giải mã thử khối đầu tiên
                val firstChunkPlainLen = minOf(PLAIN_CHUNK_SIZE.toLong(), storedSize).toInt()
                val iv = ByteArray(GCM_IV_LENGTH)
                val cipherBytes = ByteArray(firstChunkPlainLen + GCM_TAG_LENGTH_BYTES)
                raf.readFully(iv)
                raf.readFully(cipherBytes)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                val plain = cipher.doFinal(cipherBytes)
                plain.size == firstChunkPlainLen
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Gỡ khỏi Vault: Giải mã trả về thư mục Movies/ hoặc vị trí gốc và xóa bản mã hóa
     */
    suspend fun restoreVideoFromVault(context: Context, vaultVideoId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanId = vaultVideoId.removePrefix("vault_")
            val entries = loadVaultEntries(context).toMutableList()
            val meta = entries.find { it.vaultId == cleanId }
                ?: return@withContext Result.failure(Exception("Không tìm thấy dữ liệu video trong Vault"))

            val encFile = File(getVaultDir(context), meta.encryptedFileName)
            if (!encFile.exists()) return@withContext Result.failure(Exception("File mã hóa không tồn tại"))

            val secretKey = getOrCreateSecretKey()
            var restoredPathDescription = "Movies/${meta.originalDisplayName}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, meta.originalDisplayName)
                    put(MediaStore.Video.Media.MIME_TYPE, meta.mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoPlayerPro")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Không thể tạo file khôi phục trong MediaStore"))

                resolver.openOutputStream(uri)?.use { out ->
                    decryptStreamToOutput(encFile, secretKey, out)
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                restoredPathDescription = "Movies/VideoPlayerPro/${meta.originalDisplayName}"
            } else {
                val origParent = File(meta.originalPath).parentFile
                val targetDir = if (origParent != null && (origParent.exists() || origParent.mkdirs())) {
                    origParent
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).apply { mkdirs() }
                }
                val destFile = File(targetDir, meta.originalDisplayName)
                FileOutputStream(destFile).use { out ->
                    decryptStreamToOutput(encFile, secretKey, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                restoredPathDescription = destFile.absolutePath
            }

            encFile.delete()
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