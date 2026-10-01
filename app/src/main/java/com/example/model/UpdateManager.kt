package com.example.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

data class UpdateInfo(
    val status: String,
    val updateAvailable: Boolean,
    val forceUpdate: Boolean,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val ENDPOINT = "https://maxxplayers.com/api/v1/release/latest"

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking

    private val _downloadProgress = MutableStateFlow(0) // 0 to 100
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg: StateFlow<String?> = _errorMsg

    private val _upToDateMessage = MutableStateFlow<String?>(null)
    val upToDateMessage: StateFlow<String?> = _upToDateMessage

    fun clearMessages() {
        _errorMsg.value = null
        _upToDateMessage.value = null
    }

    fun dismissUpdate() {
        val current = _updateInfo.value
        if (current != null && !current.forceUpdate) {
            _updateInfo.value = null
        }
    }

    suspend fun checkForUpdates(context: Context, manual: Boolean = false) = withContext(Dispatchers.IO) {
        _isChecking.value = true
        _errorMsg.value = null
        _upToDateMessage.value = null

        val currentVersionCode = BuildConfig.VERSION_CODE
        val currentVersionName = BuildConfig.VERSION_NAME

        try {
            val url = URL(ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val params = JSONObject().apply {
                put("version_code", currentVersionCode)
                put("version_name", currentVersionName)
            }
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode

            if (responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()

                if (responseStr.isBlank()) {
                    if (manual) {
                        _upToDateMessage.value = "Uygulamanız güncel (v$currentVersionName)"
                    }
                    return@withContext
                }

                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result") ?: rootJson

                val status = resultObj.optString("status", "success")
                val serverUpdateAvailable = resultObj.optBoolean("update_available", false)
                val forceUpdate = resultObj.optBoolean("force_update", false)
                val serverVersionName = resultObj.optString("version_name", currentVersionName)
                val serverVersionCode = resultObj.optInt("version_code", currentVersionCode)
                val rawReleaseNotes = resultObj.optString("release_notes", "")
                val releaseNotes = cleanHtml(rawReleaseNotes)
                val downloadUrl = resultObj.optString("download_url", "").trim()

                // Loop prevention: only consider update available if server version is strictly greater
                val isActuallyNewer = serverVersionCode > currentVersionCode
                val updateAvailable = serverUpdateAvailable && isActuallyNewer && downloadUrl.isNotBlank()

                if (updateAvailable) {
                    val info = UpdateInfo(
                        status = status,
                        updateAvailable = true,
                        forceUpdate = forceUpdate,
                        versionName = serverVersionName,
                        versionCode = serverVersionCode,
                        releaseNotes = releaseNotes.ifBlank { "Yeni sürüm yayınlandı." },
                        downloadUrl = downloadUrl
                    )
                    _updateInfo.value = info
                } else {
                    _updateInfo.value = null
                    if (manual) {
                        _upToDateMessage.value = "Uygulamanız güncel (v$currentVersionName)"
                    }
                }
            } else {
                conn.disconnect()
                if (manual) {
                    val message = when (responseCode) {
                        401 -> "Yetkilendirme hatası (401). Lütfen tekrar giriş yapın."
                        403 -> "Erişim engellendi (403)."
                        404 -> "Güncelleme servisine ulaşılamadı (404)."
                        500 -> "Sunucu hatası oluştu (500). Lütfen daha sonra tekrar deneyin."
                        else -> "Güncelleme kontrolü başarısız oldu (HTTP $responseCode)."
                    }
                    _errorMsg.value = message
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check network error: ${e.message}")
            if (manual) {
                _errorMsg.value = "Güncelleme sunucusuna bağlanılamadı. Lütfen internet bağlantınızı kontrol edin."
            }
        } finally {
            _isChecking.value = false
        }
    }

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
            // Remove Odoo data-oe attributes and custom attributes
            .replace(Regex("""data-oe-[a-zA-Z0-9_-]+="[^"]*""""), "")
            .replace(Regex("""data-[a-zA-Z0-9_-]+="[^"]*""""), "")
            // Convert line breaks and paragraph ends to newlines
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("(?i)</li>"), "\n")
            // Remove remaining HTML tags
            .replace(Regex("<[^>]*>"), "")
            // Clean common HTML entities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&ouml;", "ö").replace("&Ouml;", "Ö")
            .replace("&uuml;", "ü").replace("&Uuml;", "Ü")
            .replace("&ccedil;", "ç").replace("&Ccedil;", "Ç")
            .replace("&icirc;", "î").replace("&Icirc;", "Î")
            .replace("&eacute;", "é").replace("&Eacute;", "É")
            .replace("&aring;", "å")
            .replace("&copy;", "©")
            .replace("&#160;", " ")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n\\s*\n+"), "\n")
            .trim()
        return text
    }

    suspend fun downloadAndInstallApk(context: Context, downloadUrl: String) = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) {
            _errorMsg.value = "Geçersiz indirme bağlantısı."
            return@withContext
        }

        _isDownloading.value = true
        _downloadProgress.value = 0

        try {
            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 25000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 Android MAXX-PLAYER")
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                conn.disconnect()
                _isDownloading.value = false
                _errorMsg.value = "APK indirilemedi (HTTP $responseCode). Bağlantıyı kontrol edin."
                return@withContext
            }

            val contentType = conn.contentType ?: ""
            if (contentType.contains("text/html", ignoreCase = true) || contentType.startsWith("text/", ignoreCase = true)) {
                conn.disconnect()
                _isDownloading.value = false
                _errorMsg.value = "İndirilen dosya APK yerine HTML sayfası döndürdü. Lütfen indirme bağlantısını kontrol edin."
                return@withContext
            }

            val fileLength = conn.contentLength
            val inputStream = conn.inputStream

            val cacheDir = File(context.cacheDir, "updates")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val apkFile = File(cacheDir, "update.apk")
            if (apkFile.exists()) apkFile.delete()

            val outputStream = FileOutputStream(apkFile)
            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (inputStream.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 99)
                    _downloadProgress.value = progress
                }
                outputStream.write(data, 0, count)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            conn.disconnect()

            // Verification of downloaded file
            if (!apkFile.exists() || apkFile.length() < 5000) {
                _errorMsg.value = "İndirilen APK dosyası eksik veya boş."
                _isDownloading.value = false
                return@withContext
            }

            // Verify ZIP magic header (0x50, 0x4B)
            val header = ByteArray(4)
            apkFile.inputStream().use { it.read(header) }
            if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                _errorMsg.value = "İndirilen dosya geçerli bir APK paketi değil (ZIP başlığı eksik)."
                _isDownloading.value = false
                return@withContext
            }

            // Verify AndroidManifest.xml exists inside the APK archive
            var hasManifest = false
            try {
                ZipFile(apkFile).use { zip ->
                    hasManifest = zip.getEntry("AndroidManifest.xml") != null
                }
            } catch (e: Exception) {
                hasManifest = false
            }

            if (!hasManifest) {
                _errorMsg.value = "İndirilen dosya geçerli bir Android APK paketi içermiyor (AndroidManifest.xml bulunamadı)."
                _isDownloading.value = false
                return@withContext
            }

            _downloadProgress.value = 100
            _isDownloading.value = false

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "APK download error: ${e.message}")
            _isDownloading.value = false
            _errorMsg.value = "APK indirilirken hata oluştu: ${e.localizedMessage ?: "Bilinmeyen hata"}"
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = android.content.ClipData.newRawUri("update_apk", uri)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "APK install error: ${e.message}")
            _errorMsg.value = "Kurulum başlatılamadı: ${e.localizedMessage ?: "Paket yükleyici açılamadı"}"
        }
    }
}

