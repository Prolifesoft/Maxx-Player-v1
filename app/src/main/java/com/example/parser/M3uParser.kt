package com.example.parser

import com.example.model.PlaylistLoadStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

data class ParseResult(val isUnchanged: Boolean, val items: List<M3uItem>?, val bytes: ByteArray?)

object M3uParser {
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            OkHttpClient()
        }
    }
    suspend fun parseFromBytes(
        bytes: ByteArray,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        parse(ByteArrayInputStream(bytes), bytes.size.toLong(), onProgress)
    }

    suspend fun downloadAndParse(
        url: String,
        cachedBytes: ByteArray?,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): ParseResult = withContext(Dispatchers.IO) {
        val client = getUnsafeOkHttpClient()
        val request = Request.Builder().url(url).build()
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    
                    onProgress(PlaylistLoadStep.DOWNLOADING, 10, "")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val outputStream = ByteArrayOutputStream()
                    
                    var lastReportTime = System.currentTimeMillis()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100) {
                            lastReportTime = now
                            val progress = if (contentLength > 0) {
                                10 + ((totalBytesRead.toDouble() / contentLength) * 40).toInt().coerceIn(0, 40)
                            } else {
                                10 + (40 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 2000000.0))).toInt().coerceIn(0, 40)
                            }
                            val mb = String.format("%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                            onProgress(PlaylistLoadStep.DOWNLOADING, progress, mb)
                        }
                    }
                    outputStream.flush()
                    val downloadedBytes = outputStream.toByteArray()
                    
                    // Check if identical to cache using MD5
                    if (cachedBytes != null && downloadedBytes.size == cachedBytes.size) {
                        val md = java.security.MessageDigest.getInstance("MD5")
                        val downloadedHash = md.digest(downloadedBytes).contentEquals(md.digest(cachedBytes))
                        if (downloadedHash) {
                            onProgress(PlaylistLoadStep.COMPLETED, 100, "")
                            return@withContext ParseResult(true, null, null)
                        }
                    }
                    
                    onProgress(PlaylistLoadStep.PROCESSING, 50, "")
                    
                    val items = parse(ByteArrayInputStream(downloadedBytes), downloadedBytes.size.toLong()) { pct, detail ->
                        onProgress(PlaylistLoadStep.PROCESSING, 50 + (pct / 2), detail)
                    }
                    onProgress(PlaylistLoadStep.COMPLETED, 100, "")
                    return@withContext ParseResult(false, items, downloadedBytes)
                }
            } else {
                onProgress(PlaylistLoadStep.ERROR, 0, "HTTP ${response.code}")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            onProgress(PlaylistLoadStep.ERROR, 0, e.message ?: "Bağlantı veya bellek hatası")
        }
        return@withContext ParseResult(false, emptyList(), null)
    }

    suspend fun parseFromUrl(
        url: String,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val client = getUnsafeOkHttpClient()
        val request = Request.Builder().url(url).build()
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    
                    onProgress(PlaylistLoadStep.DOWNLOADING, 10, "")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val outputStream = ByteArrayOutputStream()
                    
                    var lastReportTime = System.currentTimeMillis()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100) {
                            lastReportTime = now
                            val progress = if (contentLength > 0) {
                                10 + ((totalBytesRead.toDouble() / contentLength) * 40).toInt().coerceIn(0, 40)
                            } else {
                                10 + (40 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 2000000.0))).toInt().coerceIn(0, 40)
                            }
                            val mb = String.format("%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                            onProgress(PlaylistLoadStep.DOWNLOADING, progress, mb)
                        }
                    }
                    outputStream.flush()
                    val downloadedBytes = outputStream.toByteArray()
                    onProgress(PlaylistLoadStep.PROCESSING, 50, "")
                    
                    val items = parse(ByteArrayInputStream(downloadedBytes), downloadedBytes.size.toLong()) { pct, detail ->
                        onProgress(PlaylistLoadStep.PROCESSING, 50 + (pct / 2), detail)
                    }
                    onProgress(PlaylistLoadStep.COMPLETED, 100, "")
                    return@withContext items
                }
            } else {
                onProgress(PlaylistLoadStep.ERROR, 0, "HTTP ${response.code}")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            onProgress(PlaylistLoadStep.ERROR, 0, e.message ?: "Bağlantı veya bellek hatası")
        }
        return@withContext emptyList()
    }

    private suspend fun parse(
        inputStream: InputStream,
        totalBytes: Long = 0L,
        onParseProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> {
        val items = ArrayList<M3uItem>(10000)
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        var line: String? = reader.readLine()
        
        var currentTitle = ""
        var currentLogo: String? = null
        var currentGroup: String? = null
        var currentTvgId: String? = null
        var currentTvgName: String? = null
        
        var bytesRead = 0L
        var lineCount = 0
        var lastReportTime = System.currentTimeMillis()
        
        val seriesPatterns = listOf(
            java.util.regex.Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("^(.*?)\\s+(\\d+)\\.?\\s*Sezon\\s+(\\d+)\\.?\\s*B[öo]l[üu]m(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("^(.*?)\\s*Sezon\\s*(\\d+)\\s*B[öo]l[üu]m\\s*(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
        )

        while (line != null) {
            val currentLineLength = line.length
            bytesRead += currentLineLength + 1
            lineCount++
            
            var trimmed = line.trim()
            if (trimmed.startsWith("#EXTM3U#EXTINF:")) {
                trimmed = trimmed.substring(7)
            }
            if (trimmed.startsWith("#EXTINF:")) {
                currentLogo = extractAttribute(trimmed, "tvg-logo")
                if (currentLogo.isNullOrBlank()) currentLogo = null
                currentGroup = extractAttribute(trimmed, "group-title")
                currentTvgId = extractAttribute(trimmed, "tvg-id")
                if (currentTvgId.isNullOrBlank()) currentTvgId = null
                currentTvgName = extractAttribute(trimmed, "tvg-name")
                if (currentTvgName.isNullOrBlank()) currentTvgName = null
                
                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1) {
                    currentTitle = trimmed.substring(commaIndex + 1).trim()
                } else {
                    currentTitle = "Unknown Channel"
                }
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                var type = when {
                    trimmed.contains("/series/") -> ItemType.SERIES
                    trimmed.contains("/movie/") || trimmed.endsWith(".mkv") || trimmed.endsWith(".mp4") || trimmed.endsWith(".avi") -> ItemType.MOVIE
                    else -> ItemType.LIVE
                }
                
                var seriesName: String? = null
                var season: Int? = null
                var episode: Int? = null
                
                var matchedSeriesPattern = false
                for (pattern in seriesPatterns) {
                    val matcher = pattern.matcher(currentTitle)
                    if (matcher.matches()) {
                        seriesName = matcher.group(1)?.trim()?.trimEnd('-', '–', '—', ':', '|', ' ')?.trim()?.ifEmpty { currentTitle }
                        season = matcher.group(2)?.toIntOrNull()
                        episode = matcher.group(3)?.toIntOrNull()
                        matchedSeriesPattern = true
                        break
                    }
                }
                
                if (matchedSeriesPattern) {
                    type = ItemType.SERIES
                } else if (type == ItemType.SERIES) {
                    seriesName = currentTitle.trim().trimEnd('-', '–', '—', ':', '|', ' ').trim().ifEmpty { currentTitle }
                    season = 1
                    episode = 1
                }
                
                items.add(
                    M3uItem(
                        title = currentTitle.ifEmpty { "Channel ${items.size + 1}" },
                        url = trimmed,
                        logo = currentLogo,
                        group = currentGroup,
                        type = type,
                        seriesName = seriesName,
                        season = season,
                        episode = episode,
                        tvgId = currentTvgId,
                        tvgName = currentTvgName
                    )
                )
                currentTitle = ""
                currentLogo = null
                currentGroup = null
                currentTvgId = null
                currentTvgName = null
            }
            
            if (lineCount % 500 == 0) {
                val now = System.currentTimeMillis()
                if (now - lastReportTime > 80) {
                    lastReportTime = now
                    val pct = if (totalBytes > 0) {
                        ((bytesRead.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                    } else {
                        ((lineCount.toDouble() / (lineCount + 5000)) * 100).toInt().coerceIn(0, 99)
                    }
                    onParseProgress(pct, "${items.size} kanal")
                }
            }
            
            line = reader.readLine()
        }
        
        onParseProgress(100, "${items.size} kanal")
        return items
    }

    private fun extractAttribute(line: String, attribute: String): String? {
        val regex = "$attribute=\"([^\"]*)\"".toRegex()
        val matchResult = regex.find(line)
        return matchResult?.groups?.get(1)?.value
    }
}
