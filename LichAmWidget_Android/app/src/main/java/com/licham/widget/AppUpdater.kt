package com.licham.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdater {
    private const val REPO_OWNER = "trantuanmanh007-byte"
    private const val REPO_NAME = "LichAmWidget"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val rParts = remote.trim().removePrefix("v").removePrefix("V")
            .split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.trim().removePrefix("v").removePrefix("V")
            .split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val cv = cParts.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    fun fetchLatestRelease(): ReleaseInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "LichAmWidget-Android")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "").trim()
            val notes = json.optString("body", "").trim()
            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            if (tag.isEmpty() || apkUrl.isNullOrEmpty()) return null
            ReleaseInfo(
                versionName = tag.removePrefix("v").removePrefix("V"),
                downloadUrl = apkUrl,
                releaseNotes = notes
            )
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File? {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists()) updateDir.mkdirs()
        val outFile = File(updateDir, "LichAmWidget-update.apk")
        if (outFile.exists()) outFile.delete()

        var currentUrl = downloadUrl
        var conn: HttpURLConnection? = null
        return try {
            var redirects = 0
            while (redirects < 6) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LichAmWidget-Android")
                }
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val next = conn.getHeaderField("Location") ?: return null
                    conn.disconnect()
                    currentUrl = next
                    redirects++
                } else {
                    break
                }
            }

            val activeConn = conn ?: return null
            if (activeConn.responseCode != HttpURLConnection.HTTP_OK) return null

            val totalBytes = activeConn.contentLengthLong
            var downloaded = 0L
            var lastPct = -1

            activeConn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (outFile.exists() && outFile.length() > 100_000L) outFile else null
        } catch (_: Exception) {
            if (outFile.exists()) outFile.delete()
            null
        } finally {
            conn?.disconnect()
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
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

    fun launchInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
