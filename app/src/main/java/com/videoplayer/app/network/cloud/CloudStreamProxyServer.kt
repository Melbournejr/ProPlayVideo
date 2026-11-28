package com.videoplayer.app.network.cloud

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.EnumSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class ProxyStreamTarget {
    data class HttpTarget(
        val upstreamUrl: String,
        val headers: Map<String, String>,
        val mimeType: String
    ) : ProxyStreamTarget()

    data class SmbTarget(
        val host: String,
        val port: Int,
        val shareName: String,
        val smbPath: String,
        val username: String,
        val password: String,
        val domain: String,
        val knownSize: Long,
        val mimeType: String
    ) : ProxyStreamTarget()
}

/**
 * Proxy HTTP noi bo (127.0.0.1) phuc vu 2 nhiem vu:
 * 1. Phat & tua (HTTP 206 Range) muot ma cho Google Drive API, WebDAV (Basic Auth) va SMB2/SMB3
 *    qua ExoPlayer & StreamDownloadManager ma khong can sua PlayerManager.
 * 2. Nhan ma OAuth2 Redirect (/oauth2callback) khi nguoi dung dang nhap Google Drive.
 */
object CloudStreamProxyServer {
    const val DEFAULT_PORT = 18089
    private var server: LocalProxyNanoServer? = null
    private var activePort: Int = DEFAULT_PORT

    private val targets = ConcurrentHashMap<String, ProxyStreamTarget>()
    @Volatile
    private var pendingOAuthCallback: ((code: String?, error: String?) -> Unit)? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Synchronized
    fun ensureStarted(): Int {
        if (server != null) return activePort
        val portsToTry = listOf(DEFAULT_PORT, 18090, 18091)
        for (p in portsToTry) {
            try {
                val s = LocalProxyNanoServer(p)
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                activePort = p
                return p
            } catch (_: Exception) {}
        }
        return DEFAULT_PORT
    }

    @Synchronized
    fun stopServer() {
        try { server?.stop() } catch (_: Exception) {}
        server = null
        targets.clear()
        pendingOAuthCallback = null
    }

    fun getOAuthRedirectUri(): String {
        val p = ensureStarted()
        return "http://127.0.0.1:$p/oauth2callback"
    }

    fun registerOAuthCallback(callback: (code: String?, error: String?) -> Unit) {
        ensureStarted()
        pendingOAuthCallback = callback
    }

    fun registerTarget(target: ProxyStreamTarget, fileName: String): String {
        val port = ensureStarted()
        if (targets.size > 40) targets.clear()
        val token = UUID.randomUUID().toString().replace("-", "").take(12)
        targets[token] = target
        val safeEncodedName = try {
            URLEncoder.encode(fileName.ifBlank { "video.mp4" }, "UTF-8").replace("+", "%20")
        } catch (_: Exception) { "video.mp4" }
        return "http://127.0.0.1:$port/proxy/$token/$safeEncodedName"
    }

    private class LocalProxyNanoServer(port: Int) : NanoHTTPD("127.0.0.1", port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: "/"
            if (uri.startsWith("/oauth2callback")) {
                val code = session.parameters["code"]?.firstOrNull()
                val err = session.parameters["error"]?.firstOrNull()
                val cb = pendingOAuthCallback
                pendingOAuthCallback = null
                cb?.invoke(code, err)

                val html = if (!code.isNullOrBlank()) {
                    "<html><head><meta charset='utf-8'/></head><body style='background:#070A10;color:#fff;font-family:sans-serif;text-align:center;padding:40px;'>" +
                        "<h2 style='color:#38BDF8;'>\u0110\u0103ng nh\u1EADp Google Drive th\u00E0nh c\u00F4ng!</h2>" +
                        "<p>B\u1EA1n c\u00F3 th\u1EC3 \u0111\u00F3ng tab n\u00E0y v\u00E0 quay l\u1EA1i Video Player Pro.</p></body></html>"
                } else {
                    "<html><head><meta charset='utf-8'/></head><body style='background:#070A10;color:#fff;font-family:sans-serif;text-align:center;padding:40px;'>" +
                        "<h2 style='color:#FF5C8A;'>\u0110\u0103ng nh\u1EADp th\u1EA5t b\u1EA1i (${err ?: "H\u1EE7y"})</h2></body></html>"
                }
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            }

            if (uri.startsWith("/proxy/")) {
                val remainder = uri.removePrefix("/proxy/")
                val token = remainder.substringBefore("/")
                val target = targets[token]
                    ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream expired")

                val rangeHeader = session.headers["range"] ?: session.headers["Range"]
                return try {
                    when (target) {
                        is ProxyStreamTarget.HttpTarget -> serveHttpTarget(target, rangeHeader)
                        is ProxyStreamTarget.SmbTarget -> serveSmbTarget(target, rangeHeader)
                    }
                } catch (e: Exception) {
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Proxy error")
                }
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }

        private fun serveHttpTarget(target: ProxyStreamTarget.HttpTarget, rangeHeader: String?): Response {
            val reqBuilder = Request.Builder().url(target.upstreamUrl)
            target.headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!rangeHeader.isNullOrBlank()) {
                reqBuilder.header("Range", rangeHeader)
            }

            val upstreamResp = httpClient.newCall(reqBuilder.build()).execute()
            val body = upstreamResp.body
            if (!upstreamResp.isSuccessful || body == null) {
                val code = upstreamResp.code
                upstreamResp.close()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upstream HTTP $code")
            }

            val contentLen = body.contentLength()
            val mime = upstreamResp.header("Content-Type")?.substringBefore(";")?.takeIf { it.isNotBlank() } ?: target.mimeType
            val status = if (upstreamResp.code == 206) Response.Status.PARTIAL_CONTENT else Response.Status.OK

            val wrappedStream = object : FilterInputStream(body.byteStream()) {
                override fun close() {
                    try { super.close() } finally { upstreamResp.close() }
                }
            }

            val nanoResp = if (contentLen >= 0L) {
                newFixedLengthResponse(status, mime, wrappedStream, contentLen)
            } else {
                newChunkedResponse(status, mime, wrappedStream)
            }
            nanoResp.addHeader("Accept-Ranges", "bytes")
            upstreamResp.header("Content-Range")?.let { nanoResp.addHeader("Content-Range", it) }
            return nanoResp
        }

        private fun serveSmbTarget(target: ProxyStreamTarget.SmbTarget, rangeHeader: String?): Response {
            val smbClient = SMBClient()
            val connection = smbClient.connect(target.host, target.port)
            val auth = if (target.username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(target.username, target.password.toCharArray(), target.domain)
            }
            val session = connection.authenticate(auth)
            val share = session.connectShare(target.shareName) as DiskShare
            val smbFile = share.openFile(
                target.smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val fileLen = try {
                smbFile.fileInformation.standardInformation.endOfFile.coerceAtLeast(target.knownSize)
            } catch (_: Exception) {
                target.knownSize
            }

            var start = 0L
            var end = (fileLen - 1L).coerceAtLeast(0L)
            var isPartial = false

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                isPartial = true
                val rangeVal = rangeHeader.removePrefix("bytes=").substringBefore(",")
                val parts = rangeVal.split("-")
                start = (parts.getOrNull(0)?.toLongOrNull() ?: 0L).coerceIn(0L, (fileLen - 1L).coerceAtLeast(0L))
                end = (parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fileLen - 1L))
                    .coerceIn(start, (fileLen - 1L).coerceAtLeast(0L))
            }

            val contentLength = (end - start + 1L).coerceAtLeast(0L)
            val stream = SmbRangeInputStream(
                smbClient = smbClient,
                connection = connection,
                session = session,
                share = share,
                smbFile = smbFile,
                startOffset = start,
                totalToRead = contentLength
            )

            val status = if (isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK
            return newFixedLengthResponse(status, target.mimeType, stream, contentLength).apply {
                addHeader("Accept-Ranges", "bytes")
                if (isPartial) {
                    addHeader("Content-Range", "bytes $start-$end/$fileLen")
                }
            }
        }
    }

    private class SmbRangeInputStream(
        private val smbClient: SMBClient,
        private val connection: Connection,
        private val session: Session,
        private val share: DiskShare,
        private val smbFile: com.hierynomus.smbj.share.File,
        startOffset: Long,
        totalToRead: Long
    ) : InputStream() {
        private var currentOffset = startOffset
        private var remaining = totalToRead
        private val singleByte = ByteArray(1)
        @Volatile
        private var closed = false

        override fun read(): Int {
            val n = read(singleByte, 0, 1)
            return if (n <= 0) -1 else (singleByte[0].toInt() and 0xFF)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed || remaining <= 0L) return -1
            val toRead = minOf(len.toLong(), remaining, 256L * 1024L).toInt()
            if (toRead <= 0) return -1
            val readCount = smbFile.read(b, currentOffset, off, toRead)
            if (readCount <= 0) return -1
            currentOffset += readCount
            remaining -= readCount
            return readCount
        }

        override fun close() {
            if (closed) return
            closed = true
            try { smbFile.close() } catch (_: Exception) {}
            try { share.close() } catch (_: Exception) {}
            try { session.close() } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}
            try { smbClient.close() } catch (_: Exception) {}
        }
    }
}