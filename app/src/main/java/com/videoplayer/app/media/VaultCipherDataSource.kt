package com.videoplayer.app.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.videoplayer.app.util.VaultManager
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@UnstableApi
class VaultCipherDataSource : BaseDataSource(/* isNetwork = */ false) {

    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var secretKey: SecretKey? = null
    private var totalPlainSize: Long = 0L
    private var currentPlainPosition: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    private var cachedChunkIndex: Long = -1L
    private var cachedPlainChunk: ByteArray? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)

        val filePath = dataSpec.uri.path
            ?: throw IOException("Đường dẫn Vault URI không hợp lệ")
        val file = File(filePath)
        if (!file.exists()) throw IOException("File mã hóa không tồn tại")

        val randomAccessFile = RandomAccessFile(file, "r")
        raf = randomAccessFile

        val header = ByteArray(VaultManager.HEADER_SIZE)
        randomAccessFile.readFully(header)
        val bb = ByteBuffer.wrap(header)
        val magic = ByteArray(4)
        bb.get(magic)
        if (!magic.contentEquals(VaultManager.MAGIC_HEADER)) {
            throw IOException("Header file Vault không hợp lệ")
        }
        totalPlainSize = bb.long
        secretKey = VaultManager.getOrCreateSecretKey()

        currentPlainPosition = dataSpec.position
        if (currentPlainPosition > totalPlainSize) {
            throw IOException("Vị trí đọc vượt quá kích thước video")
        }

        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalPlainSize - currentPlainPosition
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val chunkIndex = currentPlainPosition / VaultManager.PLAIN_CHUNK_SIZE
        val offsetInChunk = (currentPlainPosition % VaultManager.PLAIN_CHUNK_SIZE).toInt()

        val plainChunk = if (cachedChunkIndex == chunkIndex && cachedPlainChunk != null) {
            cachedPlainChunk!!
        } else {
            decryptChunkAtIndex(chunkIndex).also {
                cachedChunkIndex = chunkIndex
                cachedPlainChunk = it
            }
        }

        val availableInChunk = plainChunk.size - offsetInChunk
        if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT

        val bytesToCopy = minOf(length.toLong(), bytesRemaining, availableInChunk.toLong()).toInt()
        System.arraycopy(plainChunk, offsetInChunk, buffer, offset, bytesToCopy)

        currentPlainPosition += bytesToCopy
        bytesRemaining -= bytesToCopy
        bytesTransferred(bytesToCopy)
        return bytesToCopy
    }

    private fun decryptChunkAtIndex(chunkIndex: Long): ByteArray {
        val file = raf ?: throw IOException("File chưa mở")
        val key = secretKey ?: throw IOException("Chưa có khóa giải mã")

        val plainStart = chunkIndex * VaultManager.PLAIN_CHUNK_SIZE
        val expectedPlainLen = minOf(
            VaultManager.PLAIN_CHUNK_SIZE.toLong(),
            (totalPlainSize - plainStart).coerceAtLeast(0L)
        ).toInt()

        val fileOffset = VaultManager.HEADER_SIZE + chunkIndex * VaultManager.ENCRYPTED_CHUNK_TOTAL_SIZE
        file.seek(fileOffset)

        val iv = ByteArray(VaultManager.GCM_IV_LENGTH)
        val cipherBytes = ByteArray(expectedPlainLen + VaultManager.GCM_TAG_LENGTH_BYTES)
        file.readFully(iv)
        file.readFully(cipherBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(VaultManager.GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        cachedPlainChunk = null
        cachedChunkIndex = -1L
        try {
            raf?.close()
        } catch (_: Exception) {
        } finally {
            raf = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    class Factory(private val fallbackFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val defaultDs = fallbackFactory.createDataSource()
            return object : DataSource {
                private var activeDs: DataSource? = null

                override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
                    defaultDs.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    val ds = if (scheme == "vault") {
                        VaultCipherDataSource()
                    } else {
                        defaultDs
                    }
                    activeDs = ds
                    return ds.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return activeDs?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
                }

                override fun getUri(): Uri? = activeDs?.uri

                override fun close() {
                    activeDs?.close()
                    activeDs = null
                }
            }
        }
    }
}