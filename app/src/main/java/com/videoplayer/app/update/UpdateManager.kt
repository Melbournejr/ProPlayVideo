package com.videoplayer.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.Keep
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Keep
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Keep
data class GitHubAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L
)

@Keep
data class VersionMetadata(
    val versionCode: Int = 0,
    val versionName: String = "",
    val sha256: String = "",
    val apkName: String = ""
)

class UpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false) // Không cho phép hạ cấp từ HTTPS xuống HTTP
        .build()

    private val gson = Gson()
    private val validRepoRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

    private val mirrors = listOf(
        "",
        "https://ghfast.top/",
        "https://mirror.ghproxy.com/"
    )

    fun isValidRepoFormat(repoFullName: String): Boolean {
        return validRepoRegex.matches(repoFullName.trim())
    }

    /**
     * Chỉ gọi trực tiếp api.github.com qua HTTPS để lấy thông tin Release & Hash gốc
     */
    suspend fun checkLatestRelease(repoFullName: String): Result<Pair<GitHubRelease, VersionMetadata>> = withContext(Dispatchers.IO) {
        try {
            val cleanRepo = repoFullName.trim()
            if (!isValidRepoFormat(cleanRepo)) {
                return@withContext Result.failure(Exception("Định dạng Repo không hợp lệ (chỉ chấp nhận owner/repo)"))
            }

            val url = "https://api.github.com/repos/$cleanRepo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ProPlayVideo-OTA")
                .build()

            val release = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}: Không tìm thấy bản Release"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Dữ liệu trống"))
                gson.fromJson(body, GitHubRelease::class.java)
            }

            val metadata = fetchVersionAndHashMetadata(release)
                ?: return@withContext Result.failure(Exception("Bản Release thiếu dữ liệu xác thực versionCode hoặc SHA-256"))

            Result.success(release to metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đọc versionCode và mã băm SHA-256 trực tiếp từ GitHub (ưu tiên version.json, .sha256 asset hoặc dòng SHA-256 trong body)
     * Tuyệt đối KHÔNG qua Mirror khi lấy Hash!
     */
    private fun fetchVersionAndHashMetadata(release: GitHubRelease): VersionMetadata? {
        // 1. Tìm file version.json trong assets (chứa versionCode + sha256)
        val versionAsset = release.assets.firstOrNull { it.name.equals("version.json", ignoreCase = true) }
        if (versionAsset != null && versionAsset.browser_download_url.startsWith("https://github.com/")) {
            try {
                val req = Request.Builder().url(versionAsset.browser_download_url).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = resp.body?.string()
                        if (!json.isNullOrBlank()) {
                            val parsed = gson.fromJson(json, VersionMetadata::class.java)
                            if (parsed.versionCode > 0 && parsed.sha256.length == 64) {
                                return parsed
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Tìm file .sha256 trong assets + trích xuất versionCode từ body hoặc tag
        var sha256Hex = ""
        val shaAsset = release.assets.firstOrNull { it.name.endsWith(".sha256", ignoreCase = true) }
        if (shaAsset != null && shaAsset.browser_download_url.startsWith("https://github.com/")) {
            try {
                val req = Request.Builder().url(shaAsset.browser_download_url).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string()?.trim() ?: ""
                        val token = text.split(Regex("\\s+")).firstOrNull() ?: ""
                        if (token.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                            sha256Hex = token.lowercase()
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Nếu chưa có .sha256 asset, tìm dòng cố định `SHA-256: <64-hex>` trong release.body
        if (sha256Hex.isEmpty()) {
            val match = Regex("SHA-256:\\s*([a-fA-F0-9]{64})", RegexOption.IGNORE_CASE).find(release.body)
            if (match != null) {
                sha256Hex = match.groupValues[1].lowercase()
            }
        }

        // Trích xuất versionCode từ dòng `VersionCode: <number>` trong body
        val vcMatch = Regex("VersionCode:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(release.body)
        val parsedVc = vcMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (parsedVc <= 0 || sha256Hex.length != 64) return null
        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        return VersionMetadata(
            versionCode = parsedVc,
            versionName = release.tag_name.removePrefix("v"),
            sha256 = sha256Hex,
            apkName = apkAsset?.name ?: ""
        )
    }

    fun isRemoteVersionCodeNewer(currentVersionCode: Int, remoteMetadata: VersionMetadata): Boolean {
        return remoteMetadata.versionCode > currentVersionCode
    }

    /**
     * Tải APK qua HTTPS (cho phép dùng Mirror đối với file APK), sau đó:
     * 1. Tính SHA-256 của file APK và so khớp với expectedSha256 lấy từ GitHub gốc.
     * 2. Kiểm tra Package Name và Signing Certificate của APK phải trùng khít với App đang chạy.
     * Sai bất kỳ bước nào -> Xóa ngay file APK và báo lỗi!
     */
    suspend fun downloadAndVerifyApk(
        rawUrl: String,
        expectedSize: Long = 0L,
        expectedSha256: String,
        mirrorIndex: Int = 0,
        onProgress: (percent: Int, speedText: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val apkFile = File(context.cacheDir, "update_ota_verified.apk")
        try {
            if (!rawUrl.startsWith("https://")) {
                return@withContext Result.failure(Exception("Từ chối tải: Chỉ chấp nhận giao thức HTTPS"))
            }
            if (!expectedSha256.matches(Regex("^[a-fA-F0-9]{64}$"))) {
                return@withContext Result.failure(Exception("Không có mã băm SHA-256 hợp lệ từ GitHub. Hủy cài đặt!"))
            }

            cleanupOldApks()

            val prefix = mirrors.getOrElse(mirrorIndex) { "" }
            val downloadUrl = if (prefix.isNotEmpty() && rawUrl.startsWith("https://github.com/")) {
                "$prefix$rawUrl"
            } else {
                rawUrl
            }

            if (!downloadUrl.startsWith("https://")) {
                return@withContext Result.failure(Exception("Mirror URL phải dùng HTTPS"))
            }

            val totalSize = if (expectedSize > 0L) expectedSize else fetchContentLength(downloadUrl)
            val startTimeMs = System.currentTimeMillis()

            if (totalSize > 1_000_000L) {
                RandomAccessFile(apkFile, "rw").use { raf -> raf.setLength(totalSize) }
                val threadCount = 8
                val chunkSize = totalSize / threadCount
                val totalDownloaded = AtomicLong(0L)
                val lastReportedPercent = AtomicInteger(-1)

                coroutineScope {
                    val jobs = (0 until threadCount).map { index ->
                        val start = index * chunkSize
                        val end = if (index == threadCount - 1) totalSize - 1 else (start + chunkSize - 1)
                        async(Dispatchers.IO) {
                            downloadChunk(downloadUrl, apkFile, start, end) { bytesRead ->
                                val currentTotal = totalDownloaded.addAndGet(bytesRead)
                                val percent = ((currentTotal * 100L) / totalSize).toInt().coerceIn(0, 100)
                                val prev = lastReportedPercent.get()
                                if (percent > prev && lastReportedPercent.compareAndSet(prev, percent)) {
                                    val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000f).coerceAtLeast(0.2f)
                                    val speedMb = (currentTotal / (1024f * 1024f)) / elapsedSec
                                    onProgress(percent, "%.2f MB/s".format(speedMb))
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            } else {
                downloadSingleStream(downloadUrl, apkFile, totalSize, startTimeMs, onProgress)
            }

            // BƯỚC KIỂM TRA 1: TÍNH SHA-256 VÀ SO KHỚP
            val actualSha256 = computeFileSha256(apkFile)
            if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                apkFile.delete()
                return@withContext Result.failure(
                    SecurityException("CẢNH BÁO BẢO MẬT: Mã băm SHA-256 của APK không khớp! Đã xóa file tải về.")
                )
            }

            // BƯỚC KIỂM TRA 2: XÁC MINH PACKAGE NAME & CHỮ KÝ SỐ (SIGNING CERTIFICATE)
            val certError = verifyApkPackageAndSignature(apkFile)
            if (certError != null) {
                apkFile.delete()
                return@withContext Result.failure(SecurityException(certError))
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            if (apkFile.exists()) apkFile.delete()
            Result.failure(e)
        }
    }

    fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (input.read(buf).also { r = it } != -1) {
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Kiểm tra packageName và chứng chỉ ký (Signing Certificate) của file APK tải về phải trùng khớp với ứng dụng đang chạy
     */
    @Suppress("DEPRECATION")
    fun verifyApkPackageAndSignature(apkFile: File): String? {
        return try {
            val pm = context.packageManager
            val archivePath = apkFile.absolutePath

            val archivePkgName: String?
            val archiveCertDigests: Set<String>
            val installedCertDigests: Set<String>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val archiveInfo = pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
                    ?: return "File APK tải về bị hỏng hoặc không đọc được cấu trúc Package."
                archivePkgName = archiveInfo.packageName
                val signingInfo = archiveInfo.signingInfo
                    ?: return "File APK tải về không có chữ ký số (Unsigned APK)."
                val sigs = if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
                archiveCertDigests = sigs?.map { sha256Bytes(it.toByteArray()) }?.toSet() ?: emptySet()

                val installedInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val instSigning = installedInfo.signingInfo
                val instSigs = if (instSigning?.hasMultipleSigners() == true) instSigning.apkContentsSigners else instSigning?.signingCertificateHistory
                installedCertDigests = instSigs?.map { sha256Bytes(it.toByteArray()) }?.toSet() ?: emptySet()
            } else {
                val archiveInfo = pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
                    ?: return "File APK tải về bị hỏng."
                archivePkgName = archiveInfo.packageName
                archiveCertDigests = archiveInfo.signatures?.map { sha256Bytes(it.toByteArray()) }?.toSet() ?: emptySet()

                val installedInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                installedCertDigests = installedInfo.signatures?.map { sha256Bytes(it.toByteArray()) }?.toSet() ?: emptySet()
            }

            if (archivePkgName != context.packageName) {
                return "Package Name của APK ($archivePkgName) không khớp với ứng dụng hiện tại (${context.packageName})!"
            }

            if (archiveCertDigests.isEmpty() || installedCertDigests.isEmpty() || archiveCertDigests.intersect(installedCertDigests).isEmpty()) {
                return "Chứng chỉ ký số (Signing Certificate) của APK không trùng khớp với ứng dụng đang chạy!"
            }
            null
        } catch (e: Exception) {
            "Lỗi xác minh chữ ký APK: ${e.message}"
        }
    }

    private fun sha256Bytes(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openManageUnknownAppSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installVerifiedApk(apkFile: File): Boolean {
        if (!canRequestPackageInstalls()) {
            openManageUnknownAppSourcesSettings()
            return false
        }
        val apkUri = FileProvider.getUriForFile(context, "com.videoplayer.app.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return true
    }

    fun cleanupOldApks(): Long {
        var freedBytes = 0L
        val dirsToClean = listOfNotNull(
            context.cacheDir,
            context.externalCacheDir,
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        )
        dirsToClean.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                    val len = file.length()
                    if (file.delete()) freedBytes += len
                }
            }
        }
        return freedBytes
    }

    private fun fetchContentLength(url: String): Long {
        return try {
            val req = Request.Builder().url(url).head().header("User-Agent", "ProPlayVideo-OTA").build()
            client.newCall(req).execute().use { resp ->
                resp.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        } catch (_: Exception) { -1L }
    }

    private fun downloadChunk(url: String, file: File, start: Long, end: Long, onBytes: (Long) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .header("User-Agent", "ProPlayVideo-OTA")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("Rỗng")
            body.byteStream().use { input ->
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(128 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        }
    }

    private fun downloadSingleStream(url: String, file: File, totalSize: Long, startTimeMs: Long, onProgress: (Int, String) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", "ProPlayVideo-OTA").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            val body = response.body ?: throw Exception("File rỗng")
            val contentLen = if (totalSize > 0) totalSize else body.contentLength()
            var downloaded = 0L
            var lastPercent = -1

            body.byteStream().use { input ->
                file.outputStream().buffered(128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLen > 0) {
                            val percent = ((downloaded * 100L) / contentLen).toInt().coerceIn(0, 100)
                            if (percent > lastPercent) {
                                lastPercent = percent
                                val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000f).coerceAtLeast(0.2f)
                                val speedMb = (downloaded / (1024f * 1024f)) / elapsedSec
                                onProgress(percent, "%.2f MB/s".format(speedMb))
                            }
                        }
                    }
                    output.flush()
                }
            }
        }
    }
}