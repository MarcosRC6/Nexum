package com.example.testario.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.example.testario.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

data class NexumRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val expectedSha256: String?
)

sealed interface NexumUpdateResult {
    data class Available(val release: NexumRelease) : NexumUpdateResult
    data object UpToDate : NexumUpdateResult
    data object NotPublished : NexumUpdateResult
    data class Error(val explanation: String) : NexumUpdateResult
}

sealed interface NexumDownloadStatus {
    data object Pending : NexumDownloadStatus
    data object Downloading : NexumDownloadStatus
    data object Failed : NexumDownloadStatus
    data object Complete : NexumDownloadStatus
}

/** GitHub Releases is our single official update source; there is no third-party APK mirror. */
object NexumUpdateManager {
    const val RELEASES_URL = "https://github.com/MarcosRC6/Nexum/releases"
    private const val API_URL = "https://api.github.com/repos/MarcosRC6/Nexum/releases/latest"
    private const val PREFS = "nexum_update_status_v1"
    private const val CACHE_KEY = "release_json"
    private const val CHECKED_KEY = "last_checked"
    private const val CHECK_EVERY_MS = 12L * 60L * 60L * 1000L

    suspend fun check(context: Context, force: Boolean = false): NexumUpdateResult =
        withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val cached = prefs.getString(CACHE_KEY, null)
            if (!force && now - prefs.getLong(CHECKED_KEY, 0) in 0 until CHECK_EVERY_MS && cached != null) {
                return@withContext parseRelease(cached)
            }
            try {
                val connection = URL(API_URL).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 7000
                    connection.readTimeout = 9000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "NEXUM-Android-UpdateChecker")
                    val code = connection.responseCode
                    if (code == 404) {
                        prefs.edit().putLong(CHECKED_KEY, now).remove(CACHE_KEY).apply()
                        return@withContext NexumUpdateResult.NotPublished
                    }
                    if (code != 200) {
                        return@withContext NexumUpdateResult.Error(
                            "GitHub no responde correctamente (HTTP $code)."
                        )
                    }
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        // Avoid holding arbitrarily large responses in memory.
                        reader.readText().take(512_000)
                    }
                    val result = parseRelease(body)
                    if (result !is NexumUpdateResult.Error) {
                        prefs.edit().putLong(CHECKED_KEY, now).putString(CACHE_KEY, body).apply()
                    }
                    result
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                NexumUpdateResult.Error("No se pudo consultar GitHub. Comprueba la conexión.")
            }
        }

    fun parseRelease(body: String): NexumUpdateResult {
        return try {
            val root = JSONObject(body)
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
                return NexumUpdateResult.NotPublished
            }
            val tag = root.optString("tag_name").trim()
            val version = tag.removePrefix("v")
            if (!version.matches(Regex("\\d+\\.\\d+(\\.\\d+)?"))) {
                return NexumUpdateResult.Error("La versión publicada no tiene un formato válido.")
            }
            if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) {
                return NexumUpdateResult.UpToDate
            }
            val assets = root.optJSONArray("assets") ?: return NexumUpdateResult.NotPublished
            var release: NexumRelease? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                // Explicit official account, repository, tag and extension: never trust a release body URL.
                if (name == "NEXUM-$tag.apk" &&
                    url == "https://github.com/MarcosRC6/Nexum/releases/download/$tag/$name" &&
                    asset.optString("state") == "uploaded"
                ) {
                    val digest = asset.optString("digest").removePrefix("sha256:").lowercase(Locale.ROOT)
                    release = NexumRelease(
                        version = version,
                        tag = tag,
                        notes = root.optString("body").take(1800),
                        apkUrl = url,
                        apkName = name,
                        expectedSha256 = digest.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                    )
                    break
                }
            }
            if (release == null) NexumUpdateResult.NotPublished
            else NexumUpdateResult.Available(release)
        } catch (_: Exception) {
            NexumUpdateResult.Error("La respuesta de GitHub no pudo interpretarse.")
        }
    }

    fun startDownload(context: Context, release: NexumRelease): Long {
        require(release.apkUrl.startsWith("https://github.com/MarcosRC6/Nexum/releases/download/"))
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("NEXUM ${release.version}")
            .setDescription("Actualización oficial firmada")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS,
                "NEXUM-${release.version}-${System.currentTimeMillis()}.apk"
            )
        return manager.enqueue(request)
    }

    fun downloadStatus(context: Context, id: Long): NexumDownloadStatus {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return NexumDownloadStatus.Failed
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> NexumDownloadStatus.Complete
                DownloadManager.STATUS_FAILED -> NexumDownloadStatus.Failed
                DownloadManager.STATUS_RUNNING -> NexumDownloadStatus.Downloading
                else -> NexumDownloadStatus.Pending
            }
        }
    }

    suspend fun verifyDownloadedApk(context: Context, id: Long, release: NexumRelease): Boolean =
        withContext(Dispatchers.IO) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = manager.getUriForDownloadedFile(id) ?: return@withContext false
            val expected = release.expectedSha256 ?: return@withContext true
            val actual = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    actual.update(buffer, 0, read)
                }
            } ?: return@withContext false
            actual.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } == expected
        }

    /** Android still requires user approval for APK installation. */
    fun openInstaller(context: Context, id: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = manager.getUriForDownloadedFile(id)
            ?: throw IOException("La descarga ya no está disponible.")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}

/** Semantic numeric version comparison (not lexicographic: 1.10 > 1.9). */
internal fun isNewerVersion(remote: String, installed: String): Boolean {
    val a = remote.removePrefix("v").split('.').map { it.toIntOrNull() ?: return false }
    val b = installed.removePrefix("v").split('.').map { it.toIntOrNull() ?: return false }
    for (index in 0 until maxOf(a.size, b.size)) {
        val diff = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
        if (diff != 0) return diff > 0
    }
    return false
}
