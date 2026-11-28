package com.videoplayer.app.network.cloud

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SMB2/SMB3 Client ket noi thu muc chia se Windows / NAS trong mang LAN
 */
object SmbProvider {
    private const val ATTR_DIRECTORY = 0x10L

    suspend fun listDirectory(source: CloudSourceConfig, relativePath: String): Result<List<CloudEntry>> = withContext(Dispatchers.IO) {
        val host = source.host.trim()
        val shareName = source.shareName.trim().trim('/', '\\')
        if (host.isBlank() || shareName.isBlank()) {
            return@withContext Result.failure(Exception("Thi\u1EBFu IP m\u00E1y ch\u1EE7 ho\u1EB7c t\u00EAn th\u01B0 m\u1EE5c chia s\u1EBB SMB"))
        }

        val smbClient = SMBClient()
        try {
            smbClient.connect(host, source.port.takeIf { it > 0 } ?: 445).use { connection ->
                val auth = if (source.username.isBlank()) {
                    AuthenticationContext.anonymous()
                } else {
                    AuthenticationContext(source.username, source.password.toCharArray(), source.domain)
                }
                connection.authenticate(auth).use { session ->
                    (session.connectShare(shareName) as DiskShare).use { share ->
                        val cleanPath = relativePath.trim('/', '\\').replace('/', '\\')
                        val list = share.list(cleanPath)
                        val entries = list.mapNotNull { info ->
                            val name = info.fileName
                            if (name == "." || name == ".." || name.isBlank()) return@mapNotNull null
                            val isDir = (info.fileAttributes and ATTR_DIRECTORY) != 0L
                            if (!isDir && !CloudSourceManager.isVideoFileName(name)) return@mapNotNull null
                            val childPath = if (cleanPath.isBlank()) name else "$cleanPath\\$name"
                            CloudEntry(
                                id = childPath,
                                name = name,
                                pathOrId = childPath,
                                isFolder = isDir,
                                size = if (isDir) 0L else info.endOfFile,
                                mimeType = if (isDir) "inode/directory" else CloudSourceManager.guessMimeType(name)
                            )
                        }.sortedWith(compareByDescending<CloudEntry> { it.isFolder }.thenBy { it.name.lowercase() })
                        Result.success(entries)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("L\u1ED7i k\u1EBFt n\u1ED1i SMB: ${e.message}"))
        } finally {
            try { smbClient.close() } catch (_: Exception) {}
        }
    }

    fun resolvePlayableUrl(source: CloudSourceConfig, entry: CloudEntry): Result<String> {
        val proxyUrl = CloudStreamProxyServer.registerTarget(
            target = ProxyStreamTarget.SmbTarget(
                host = source.host.trim(),
                port = source.port.takeIf { it > 0 } ?: 445,
                shareName = source.shareName.trim().trim('/', '\\'),
                smbPath = entry.pathOrId.trim('/', '\\').replace('/', '\\'),
                username = source.username,
                password = source.password,
                domain = source.domain,
                knownSize = entry.size,
                mimeType = entry.mimeType.ifBlank { CloudSourceManager.guessMimeType(entry.name) }
            ),
            fileName = entry.name
        )
        return Result.success(proxyUrl)
    }
}