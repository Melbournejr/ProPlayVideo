package com.videoplayer.app.network

import android.content.Context
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.util.VaultManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

data class LanServerStatus(
    val isRunning: Boolean = false,
    val url: String = "",
    val port: Int = 8088,
    val sharedVideoCount: Int = 0
)

/**
 * Phần D.1: Máy chủ HTTP nội bộ (Local LAN Web Server) cho Power-User:
 * - Cho phép các thiết bị khác trong cùng mạng Wi-Fi (Laptop, PC, iPad, Smart TV)
 *   mở trình duyệt tại http://<IP_LAN>:8088 để duyệt và phát trực tiếp toàn bộ video trên máy.
 * - Hỗ trợ đầy đủ HTTP 206 Partial Content (Range Request) để tua (Seek) video 4K tức thì.
 * - Tự động loại trừ 100% các video nằm trong Kho Ẩn (Vault).
 */
object LanVideoHttpServer {
    private const val DEFAULT_PORT = 8088
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(LanServerStatus())
    val status: StateFlow<LanServerStatus> = _status.asStateFlow()

    @Volatile
    private var cachedPublicVideos: List<VideoItem> = emptyList()

    fun getLocalWifiIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun startServer(context: Context, port: Int = DEFAULT_PORT) {
        if (_status.value.isRunning) return
        val appCtx = context.applicationContext

        serverJob = serverScope.launch {
            try {
                refreshVideoCatalog(appCtx)
                val socket = ServerSocket(port)
                serverSocket = socket
                val ip = getLocalWifiIpAddress()
                _status.value = LanServerStatus(
                    isRunning = true,
                    url = "http://$ip:$port",
                    port = port,
                    sharedVideoCount = cachedPublicVideos.size
                )

                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch { handleClientSocket(appCtx, client) }
                }
            } catch (_: Exception) {
                stopServer()
            }
        }
    }

    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        _status.value = LanServerStatus(isRunning = false)
    }

    private suspend fun refreshVideoCatalog(context: Context) {
        try {
            val all = VideoRepository(context).getAllVideos().first()
            val hiddenPaths = VaultManager.getHiddenOriginalPaths(context)
            cachedPublicVideos = all.filter {
                !it.id.startsWith("vault_") && it.path !in hiddenPaths && File(it.path).exists()
            }
        } catch (_: Exception) {}
    }

    private suspend fun handleClientSocket(context: Context, client: Socket) {
        client.use { sock ->
            try {
                sock.soTimeout = 15_000
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val rawPath = URLDecoder.decode(parts[1], "UTF-8")

                var rangeHeader: String? = null
                var line = reader.readLine()
                while (!line.isNullOrEmpty()) {
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                    line = reader.readLine()
                }

                val out = BufferedOutputStream(sock.getOutputStream())
                when {
                    rawPath == "/" || rawPath.startsWith("/index") -> {
                        refreshVideoCatalog(context)
                        val html = buildWebPortalHtml(cachedPublicVideos)
                        val bytes = html.toByteArray(Charsets.UTF_8)
                        val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        out.write(header.toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                    }
                    rawPath.startsWith("/video/") -> {
                        val id = rawPath.removePrefix("/video/").substringBefore("?")
                        val video = cachedPublicVideos.find { it.id == id }
                        val file = video?.let { File(it.path) }
                        if (video == null || file == null || !file.exists()) {
                            val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            out.write(notFound.toByteArray(Charsets.UTF_8))
                            out.flush()
                            return
                        }
                        streamVideoFileWithRange(file, video.mimeType.ifBlank { "video/mp4" }, rangeHeader, out)
                    }
                    else -> {
                        val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        out.write(resp.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun streamVideoFileWithRange(
        file: File,
        mimeType: String,
        rangeHeader: String?,
        out: BufferedOutputStream
    ) {
        val fileLen = file.length()
        var start = 0L
        var end = fileLen - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            isPartial = true
            val rangeVal = rangeHeader.removePrefix("bytes=").substringBefore(",")
            val split = rangeVal.split("-")
            start = split.getOrNull(0)?.toLongOrNull() ?: 0L
            end = split.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fileLen - 1L)
        }

        start = start.coerceIn(0L, (fileLen - 1L).coerceAtLeast(0L))
        end = end.coerceIn(start, (fileLen - 1L).coerceAtLeast(0L))
        val contentLen = (end - start + 1L).coerceAtLeast(0L)

        val header = if (isPartial) {
            "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Content-Range: bytes $start-$end/$fileLen\r\n" +
                "Content-Length: $contentLen\r\n" +
                "Connection: close\r\n\r\n"
        } else {
            "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Content-Length: $fileLen\r\n" +
                "Connection: close\r\n\r\n"
        }

        out.write(header.toByteArray(Charsets.UTF_8))
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(128 * 1024)
            var remaining = contentLen
            while (remaining > 0L) {
                val toRead = minOf(buf.size.toLong(), remaining).toInt()
                val r = raf.read(buf, 0, toRead)
                if (r <= 0) break
                out.write(buf, 0, r)
                remaining -= r
            }
        }
        out.flush()
    }

    private fun buildWebPortalHtml(videos: List<VideoItem>): String {
        val sb = StringBuilder()
        sb.append(
            """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <title>Video Player Pro - LAN Media Server</title>
                <style>
                    body { background:#070D19; color:#fff; font-family:system-ui,-apple-system,sans-serif; margin:0; padding:20px; }
                    h1 { color:#00F0FF; font-size:22px; margin-bottom:4px; }
                    p.sub { color:#98A8C8; font-size:13px; margin-top:0; margin-bottom:18px; }
                    .player-box { background:#0F1A2E; border:1px solid rgba(0,240,255,0.4); border-radius:16px; padding:12px; margin-bottom:20px; display:none; }
                    video { width:100%; max-height:72vh; border-radius:12px; background:#000; }
                    .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:12px; }
                    .card { background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.14); border-radius:14px; padding:12px; cursor:pointer; transition:0.2s; }
                    .card:hover { border-color:#00F0FF; background:rgba(0,240,255,0.12); }
                    .title { font-weight:700; font-size:14px; margin-bottom:6px; word-break:break-word; }
                    .meta { font-size:12px; color:#80FFDB; }
                </style>
            </head>
            <body>
                <h1>📡 Video Player Pro • LAN Streamer</h1>
                <p class="sub">Đang chia sẻ ${videos.size} video trong mạng nội bộ (Hỗ trợ tua trực tiếp HTTP 206 Range)</p>
                <div id="pbox" class="player-box">
                    <div id="ptitle" style="font-weight:bold;margin-bottom:8px;color:#00F0FF;"></div>
                    <video id="vplayer" controls autoplay></video>
                </div>
                <div class="grid">
            """.trimIndent()
        )
        videos.forEach { v ->
            val safeName = v.displayName.replace("\"", "&quot;").replace("<", "&lt;")
            sb.append(
                """
                <div class="card" onclick="playVid('${v.id}', '$safeName')">
                    <div class="title">🎬 $safeName</div>
                    <div class="meta">📁 ${v.folder} • ${v.sizeFormatted} • ${v.durationFormatted}</div>
                </div>
                """.trimIndent()
            )
        }
        sb.append(
            """
                </div>
                <script>
                    function playVid(id, name) {
                        var box = document.getElementById('pbox');
                        var vp = document.getElementById('vplayer');
                        document.getElementById('ptitle').innerText = 'Đang phát: ' + name;
                        box.style.display = 'block';
                        vp.src = '/video/' + id;
                        vp.play();
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                    }
                </script>
            </body>
            </html>
            """.trimIndent()
        )
        return sb.toString()
    }
}