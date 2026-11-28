package com.videoplayer.app.network.cloud

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * WebDAV Client sieu nhe (0 thu vien ngoai):
 * - Dung OkHttp PROPFIND (Depth: 1) + XmlPullParser co san cua Android
 * - Ho tro PikPak, Nextcloud, NAS va OpenList/Alist (cau noi Google Drive, OneDrive, Telegram)
 */
object WebDavProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val propfindXml = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:">
            <d:prop>
                <d:displayname/>
                <d:getcontentlength/>
                <d:getcontenttype/>
                <d:resourcetype/>
            </d:prop>
        </d:propfind>
    """.trimIndent()

    private fun buildAuthHeader(source: CloudSourceConfig): String? {
        if (source.username.isBlank() && source.password.isBlank()) return null
        return Credentials.basic(source.username, source.password, Charsets.UTF_8)
    }

    suspend fun listDirectory(source: CloudSourceConfig, folderUrl: String): Result<List<CloudEntry>> = withContext(Dispatchers.IO) {
        try {
            val rawUrl = folderUrl.ifBlank { source.url }.trim()
            val normalizedUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"
            val baseHttpUrl = normalizedUrl.toHttpUrlOrNull()
                ?: return@withContext Result.failure(Exception("URL WebDAV kh\u00F4ng h\u1EE3p l\u1EC7"))

            val reqBuilder = Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", propfindXml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "1")
            buildAuthHeader(source)?.let { reqBuilder.header("Authorization", it) }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 207) {
                    return@withContext Result.failure(Exception("WebDAV l\u1ED7i HTTP ${resp.code} (ki\u1EC3m tra URL/t\u00E0i kho\u1EA3n)"))
                }
                val xmlText = resp.body?.string().orEmpty()
                val entries = parseMultiStatusXml(baseHttpUrl.toString(), baseHttpUrl.encodedPath, xmlText)
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMultiStatusXml(baseUrlStr: String, requestEncodedPath: String, xmlText: String): List<CloudEntry> {
        val baseUrl = baseUrlStr.toHttpUrlOrNull() ?: return emptyList()
        val normReqPath = requestEncodedPath.trimEnd('/')
        val result = mutableListOf<CloudEntry>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xmlText))

        var inResponse = false
        var href = ""
        var displayName = ""
        var contentLength = 0L
        var contentType = ""
        var isCollection = false
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    currentTag = tag
                    if (tag == "response") {
                        inResponse = true
                        href = ""
                        displayName = ""
                        contentLength = 0L
                        contentType = ""
                        isCollection = false
                    } else if (inResponse && tag == "collection") {
                        isCollection = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val txt = parser.text?.trim().orEmpty()
                        if (txt.isNotEmpty()) {
                            when (currentTag) {
                                "href" -> href += txt
                                "displayname" -> displayName += txt
                                "getcontentlength" -> contentLength = txt.toLongOrNull() ?: contentLength
                                "getcontenttype" -> contentType += txt
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (tag == "response" && inResponse) {
                        inResponse = false
                        val resolved = baseUrl.resolve(href)
                        if (resolved != null) {
                            val itemPath = resolved.encodedPath.trimEnd('/')
                            // Bo qua chinh thu muc cha dang duyet
                            if (!itemPath.equals(normReqPath, ignoreCase = true)) {
                                val fallbackName = try {
                                    URLDecoder.decode(itemPath.substringAfterLast('/'), "UTF-8")
                                } catch (_: Exception) {
                                    itemPath.substringAfterLast('/')
                                }
                                val finalName = displayName.ifBlank { fallbackName }.trimEnd('/')
                                if (finalName.isNotBlank() && (isCollection || CloudSourceManager.isVideoFileName(finalName, contentType))) {
                                    result.add(
                                        CloudEntry(
                                            id = resolved.toString(),
                                            name = finalName,
                                            pathOrId = resolved.toString(),
                                            isFolder = isCollection,
                                            size = contentLength,
                                            mimeType = contentType.ifBlank { CloudSourceManager.guessMimeType(finalName) }
                                        )
                                    )
                                }
                            }
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return result.sortedWith(compareByDescending<CloudEntry> { it.isFolder }.thenBy { it.name.lowercase() })
    }

    fun resolvePlayableUrl(source: CloudSourceConfig, entry: CloudEntry): Result<String> {
        val headers = mutableMapOf<String, String>()
        buildAuthHeader(source)?.let { headers["Authorization"] = it }
        val proxyUrl = CloudStreamProxyServer.registerTarget(
            target = ProxyStreamTarget.HttpTarget(
                upstreamUrl = entry.pathOrId,
                headers = headers,
                mimeType = entry.mimeType.ifBlank { CloudSourceManager.guessMimeType(entry.name) }
            ),
            fileName = entry.name
        )
        return Result.success(proxyUrl)
    }
}