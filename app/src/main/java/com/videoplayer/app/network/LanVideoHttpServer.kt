package com.videoplayer.app.network

import android.content.Context
import com.videoplayer.app.data.model.VideoItem
import com.videoplayer.app.data.repository.VideoRepository
import com.videoplayer.app.util.VaultManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface

data class LanServerStatus(
    val isRunning: Boolean = false,
    val url: String = "",
    val port: Int = 8080,
    val sharedVideoCount: Int = 0
)

/**
 * Local LAN Web Server su dung thu vien nhe NanoHTTPD (org.nanohttpd:nanohttpd:2.3.1):
 * - Chay tren cong 8080 (http://192.168.x.x:8080)
 * - Liet ke toan bo video cong khai trong thu vien va phat truc tiep qua trinh duyet
 * - Ho tro day du HTTP 206 Partial Content (Range Request) de tua (Seek) video muot ma
 * - Tu dong tat khi app bi kill (onDestroy) hoac nguoi dung tat thu cong
 */
object LanVideoHttpServer {
    const val DEFAULT_PORT = 8080
    private var nanoServer: InternalNanoVideoServer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    @Synchronized
    fun startServer(context: Context, port: Int = DEFAULT_PORT) {
        if (_status.value.isRunning) return
        val appCtx = context.applicationContext
        scope.launch {
            try {
                refreshVideoCatalog(appCtx)
                val server = InternalNanoVideoServer(appCtx, port)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                nanoServer = server
                val ip = getLocalWifiIpAddress()
                _status.value = LanServerStatus(
                    isRunning = true,
                    url = "http://$ip:$port",
                    port = port,
                    sharedVideoCount = cachedPublicVideos.size
                )
            } catch (_: Exception) {
                stopServer()
            }
        }
    }

    @Synchronized
    fun stopServer() {
        try {
            nanoServer?.stop()
        } catch (_: Exception) {}
        nanoServer = null
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

    private class InternalNanoVideoServer(
        private val appContext: Context,
        port: Int
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri ?: "/"
            return when {
                uri == "/" || uri.startsWith("/index") -> {
                    runBlocking { refreshVideoCatalog(appContext) }
                    val html = buildWebPortalHtml(cachedPublicVideos)
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
                }
                uri.startsWith("/video/") -> {
                    val id = uri.removePrefix("/video/").substringBefore("?")
                    val video = cachedPublicVideos.find { it.id == id }
                    val file = video?.let { File(it.path) }
                    if (video == null || file == null || !file.exists()) {
                        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
                    }
                    serveVideoFileWithRange(file, video.mimeType.ifBlank { "video/mp4" }, session.headers)
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }

        private fun serveVideoFileWithRange(
            file: File,
            mimeType: String,
            headers: Map<String, String>
        ): Response {
            val fileLen = file.length()
            val rangeHeader = headers["range"] ?: headers["Range"]

            if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                val rangeValue = rangeHeader.removePrefix("bytes=").substringBefore(",")
                val parts = rangeValue.split("-")
                val start = (parts.getOrNull(0)?.toLongOrNull() ?: 0L).coerceIn(0L, (fileLen - 1L).coerceAtLeast(0L))
                val end = (parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (fileLen - 1L))
                    .coerceIn(start, (fileLen - 1L).coerceAtLeast(0L))
                val contentLength = (end - start + 1L).coerceAtLeast(0L)

                val fis = FileInputStream(file)
                fis.skip(start)
                return newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    mimeType,
                    fis,
                    contentLength
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Range", "bytes $start-$end/$fileLen")
                    addHeader("Content-Length", contentLength.toString())
                }
            } else {
                val fis = FileInputStream(file)
                return newFixedLengthResponse(
                    Response.Status.OK,
                    mimeType,
                    fis,
                    fileLen
                ).apply {
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Content-Length", fileLen.toString())
                }
            }
        }
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
                <title>Video Player Pro - LAN Web Server</title>
                <style>
                    body { background:#060B16; color:#fff; font-family:system-ui,-apple-system,sans-serif; margin:0; padding:20px; }
                    .top { display:flex; flex-wrap:wrap; justify-content:space-between; align-items:center; gap:12px; margin-bottom:18px; }
                    h1 { color:#00F0FF; font-size:22px; margin:0; }
                    p.sub { color:#98A8C8; font-size:13px; margin:4px 0 0 0; }
                    input.search { background:rgba(255,255,255,0.08); border:1px solid rgba(255,255,255,0.2); color:#fff; border-radius:20px; padding:9px 16px; width:260px; outline:none; }
                    .player-box { background:#0D172A; border:1px solid rgba(0,240,255,0.45); border-radius:18px; padding:14px; margin-bottom:22px; display:none; }
                    video { width:100%; max-height:74vh; border-radius:12px; background:#000; }
                    .grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:12px; }
                    .card { background:rgba(255,255,255,0.06); border:1px solid rgba(255,255,255,0.14); border-radius:14px; padding:13px; cursor:pointer; transition:0.2s; }
                    .card:hover { border-color:#00F0FF; background:rgba(0,240,255,0.12); transform:translateY(-2px); }
                    .title { font-weight:700; font-size:14px; margin-bottom:6px; word-break:break-word; }
                    .meta { font-size:12px; color:#80FFDB; }
                </style>
            </head>
            <body>
                <div class="top">
                    <div>
                        <h1>&#128225; Video Player Pro &bull; Local LAN Server</h1>
                        <p class="sub">&#272;ang chia s&#7867; ${videos.size} video qua Wi-Fi n&#7897;i b&#7897;</p>
                    </div>
                    <input class="search" id="q" type="text" placeholder="T&#236;m t&#234;n video ho&#7863;c th&#432; m&#7909;c..." oninput="filterCards()"/>
                </div>
                <div id="pbox" class="player-box">
                    <div id="ptitle" style="font-weight:bold;margin-bottom:8px;color:#00F0FF;"></div>
                    <video id="vplayer" controls autoplay></video>
                </div>
                <div class="grid" id="vgrid">
            """.trimIndent()
        )
        videos.forEach { v ->
            val safeName = v.displayName.replace("\"", "&quot;").replace("<", "&lt;")
            val safeFolder = v.folder.replace("\"", "&quot;").replace("<", "&lt;")
            sb.append(
                """
                <div class="card" data-name="${safeName.lowercase()} ${safeFolder.lowercase()}" onclick="playVid('${v.id}', '$safeName')">
                    <div class="title">&#127916; $safeName</div>
                    <div class="meta">&#128193; $safeFolder &bull; ${v.sizeFormatted} &bull; ${v.durationFormatted}</div>
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
                        document.getElementById('ptitle').innerText = '▶ ' + name;
                        box.style.display = 'block';
                        vp.src = '/video/' + id;
                        vp.play();
                        window.scrollTo({ top: 0, behavior: 'smooth' });
                    }
                    function filterCards() {
                        var q = document.getElementById('q').value.toLowerCase();
                        var cards = document.querySelectorAll('.card');
                        cards.forEach(function(c) {
                            var match = c.getAttribute('data-name').indexOf(q) !== -1;
                            c.style.display = match ? 'block' : 'none';
                        });
                    }
                </script>
            </body>
            </html>
            """.trimIndent()
        )
        return sb.toString()
    }
}