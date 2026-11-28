package com.videoplayer.app.network.cloud

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@Keep
private data class DriveTokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val error_description: String? = null
)

@Keep
private data class DriveFileDto(
    val id: String = "",
    val name: String = "",
    val mimeType: String = "",
    val size: String? = null
)

@Keep
private data class DriveFileListResponse(
    val files: List<DriveFileDto>? = null
)

/**
 * Ho tro Google Drive ca 2 che do:
 * 1. Link chia se cong khai: Tu dong tach File ID & vuot trang canh bao quet virus (>100MB)
 * 2. Tai khoan ca nhan (Drive API v3 + OAuth2 Desktop Loopback): Duyet thu muc & phat qua Proxy
 */
object GoogleDriveProvider {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val fileIdRegexes = listOf(
        Regex("""drive\.google\.com/file/d/([a-zA-Z0-9_-]+)"""),
        Regex("""drive\.google\.com/open\?id=([a-zA-Z0-9_-]+)"""),
        Regex("""drive\.google\.com/uc\?.*id=([a-zA-Z0-9_-]+)"""),
        Regex("""drive\.usercontent\.google\.com/download\?.*id=([a-zA-Z0-9_-]+)""")
    )

    fun isGoogleDriveUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("drive.google.com/") || lower.contains("drive.usercontent.google.com/")
    }

    fun extractFileId(url: String): String? {
        for (rx in fileIdRegexes) {
            val id = rx.find(url)?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    /**
     * Giai ma link Google Drive chia se cong khai -> (directPlayUrl, detectedFileName?)
     */
    suspend fun resolvePublicLink(rawUrl: String): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        try {
            val fileId = extractFileId(rawUrl.trim())
                ?: return@withContext Result.failure(Exception("Kh\u00F4ng t\u00ECm th\u1EA5y File ID trong link Google Drive"))

            val baseDownloadUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=t"
            val req = Request.Builder()
                .url(baseDownloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            client.newCall(req).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                val contentDisp = resp.header("Content-Disposition").orEmpty()
                val fileName = Regex("""filename\*?=(?:UTF-8'')?"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
                    .find(contentDisp)?.groupValues?.getOrNull(1)

                if (resp.isSuccessful && !contentType.contains("text/html")) {
                    val finalUrl = resp.request.url.toString()
                    return@withContext Result.success(finalUrl to fileName)
                }

                // Neu Google tra ve trang HTML xac nhan tep lon -> trich xuat uuid/confirm
                val html = resp.body?.string().orEmpty()
                val uuid = Regex("""name="uuid"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1)
                val confirm = Regex("""name="confirm"\s+value="([^"]+)"""").find(html)?.groupValues?.getOrNull(1) ?: "t"
                val htmlTitle = Regex("""<span class="uc-name-size"><a[^>]*>([^<]+)</a>""").find(html)?.groupValues?.getOrNull(1)

                if (!uuid.isNullOrBlank()) {
                    val confirmedUrl = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$confirm&uuid=$uuid"
                    return@withContext Result.success(confirmedUrl to (fileName ?: htmlTitle))
                }

                // Fallback: neu khong phai file cong khai
                if (resp.code == 403 || resp.code == 404 || html.contains("Sign in", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("T\u1EC7p Google Drive ch\u01B0a b\u1EADt chia s\u1EBB c\u00F4ng khai ho\u1EB7c \u0111\u00E3 h\u1EBFt h\u1EA1n m\u1EE9c t\u1EA3i"))
                }
                Result.success(baseDownloadUrl to (fileName ?: htmlTitle))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildOAuthAuthorizeUrl(clientId: String, redirectUri: String): String {
        val encRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val encScope = URLEncoder.encode("https://www.googleapis.com/auth/drive.readonly", "UTF-8")
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${URLEncoder.encode(clientId.trim(), "UTF-8")}" +
            "&redirect_uri=$encRedirect" +
            "&response_type=code" +
            "&scope=$encScope" +
            "&access_type=offline" +
            "&prompt=consent"
    }

    suspend fun exchangeAuthCode(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String
    ): Result<Triple<String, String?, Long>> = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("code", code)
                .add("client_id", clientId.trim())
                .add("client_secret", clientSecret.trim())
                .add("redirect_uri", redirectUri)
                .add("grant_type", "authorization_code")
                .build()
            val req = Request.Builder().url("https://oauth2.googleapis.com/token").post(form).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = gson.fromJson(body, DriveTokenResponse::class.java)
                if (!resp.isSuccessful || parsed?.access_token.isNullOrBlank()) {
                    return@withContext Result.failure(Exception(parsed?.error_description ?: "HTTP ${resp.code}"))
                }
                val expiryMs = System.currentTimeMillis() + ((parsed.expires_in ?: 3500L) - 120L) * 1000L
                Result.success(Triple(parsed.access_token!!, parsed.refresh_token, expiryMs))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun ensureFreshAccessToken(context: Context, source: CloudSourceConfig): Result<CloudSourceConfig> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (source.accessToken.isNotBlank() && (source.tokenExpiryMs == 0L || now < source.tokenExpiryMs)) {
            return@withContext Result.success(source)
        }
        if (source.refreshToken.isBlank() || source.oauthClientId.isBlank()) {
            return@withContext if (source.accessToken.isNotBlank()) Result.success(source)
            else Result.failure(Exception("Ch\u01B0a \u0111\u0103ng nh\u1EADp Google Drive. H\u00E3y ch\u1EC9nh s\u1EEDa kho v\u00E0 b\u1EA5m \u0110\u0103ng nh\u1EADp."))
        }

        try {
            val form = FormBody.Builder()
                .add("client_id", source.oauthClientId.trim())
                .add("client_secret", source.oauthClientSecret.trim())
                .add("refresh_token", source.refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val req = Request.Builder().url("https://oauth2.googleapis.com/token").post(form).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val parsed = gson.fromJson(body, DriveTokenResponse::class.java)
                if (!resp.isSuccessful || parsed?.access_token.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("H\u1EBFt phi\u00EAn Google Drive: ${parsed?.error_description ?: resp.code}"))
                }
                val updated = source.copy(
                    accessToken = parsed.access_token!!,
                    tokenExpiryMs = System.currentTimeMillis() + ((parsed.expires_in ?: 3500L) - 120L) * 1000L
                )
                CloudSourceManager.upsertSource(context, updated)
                Result.success(updated)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDirectory(context: Context, source: CloudSourceConfig, folderId: String): Result<List<CloudEntry>> = withContext(Dispatchers.IO) {
        val freshSource = ensureFreshAccessToken(context, source).getOrElse { return@withContext Result.failure(it) }
        try {
            val parent = folderId.ifBlank { "root" }
            val query = "'$parent' in parents and trashed = false and (mimeType = 'application/vnd.google-apps.folder' or mimeType contains 'video/')"
            val url = Uri.parse("https://www.googleapis.com/drive/v3/files").buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("fields", "files(id,name,mimeType,size)")
                .appendQueryParameter("orderBy", "folder,name")
                .appendQueryParameter("pageSize", "200")
                .build().toString()

            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${freshSource.accessToken}")
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(Exception("Google Drive API l\u1ED7i HTTP ${resp.code}"))
                }
                val listResp = gson.fromJson(body, DriveFileListResponse::class.java)
                val entries = (listResp?.files ?: emptyList()).map { dto ->
                    val isFolder = dto.mimeType == "application/vnd.google-apps.folder"
                    CloudEntry(
                        id = dto.id,
                        name = dto.name,
                        pathOrId = dto.id,
                        isFolder = isFolder,
                        size = dto.size?.toLongOrNull() ?: 0L,
                        mimeType = if (isFolder) dto.mimeType else dto.mimeType.ifBlank { CloudSourceManager.guessMimeType(dto.name) }
                    )
                }.sortedWith(compareByDescending<CloudEntry> { it.isFolder }.thenBy { it.name.lowercase() })
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolvePrivatePlayableUrl(context: Context, source: CloudSourceConfig, entry: CloudEntry): Result<String> {
        val freshSource = ensureFreshAccessToken(context, source).getOrElse { return Result.failure(it) }
        val mediaUrl = "https://www.googleapis.com/drive/v3/files/${entry.pathOrId}?alt=media"
        val proxyUrl = CloudStreamProxyServer.registerTarget(
            target = ProxyStreamTarget.HttpTarget(
                upstreamUrl = mediaUrl,
                headers = mapOf("Authorization" to "Bearer ${freshSource.accessToken}"),
                mimeType = entry.mimeType.ifBlank { CloudSourceManager.guessMimeType(entry.name) }
            ),
            fileName = entry.name
        )
        return Result.success(proxyUrl)
    }
}