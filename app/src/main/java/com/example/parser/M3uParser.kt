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

data class FastPreviewResult(
    val previewItems: List<M3uItem>,
    val categoriesByType: Map<ItemType, List<String>>
)

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

    suspend fun downloadToFile(
        url: String,
        targetFile: java.io.File,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val client = getUnsafeOkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .build()
        val tempFile = java.io.File(targetFile.parentFile, "${targetFile.name}.download")
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "Sunucuya bağlanılıyor...")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress(PlaylistLoadStep.ERROR, 0, "HTTP ${response.code}")
                return@withContext false
            }
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val fileOut = java.io.FileOutputStream(tempFile).buffered(65536)

            onProgress(PlaylistLoadStep.DOWNLOADING, 10, "İndiriliyor...")
            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastReportTime = System.currentTimeMillis()

            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fileOut.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 150) {
                        lastReportTime = now
                        val mb = String.format(java.util.Locale.US, "%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                        val pct = if (contentLength > 0) {
                            ((totalBytesRead.toDouble() / contentLength) * 100).toInt().coerceIn(10, 95)
                        } else {
                            (10 + (80 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 5000000.0)))).toInt().coerceIn(10, 90)
                        }
                        onProgress(PlaylistLoadStep.DOWNLOADING, pct, "$mb indirildi")
                    }
                }
                fileOut.flush()
            } finally {
                try { fileOut.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                return@withContext true
            }
            return@withContext false
        } catch (e: Throwable) {
            e.printStackTrace()
            try { tempFile.delete() } catch (_: Exception) {}
            onProgress(PlaylistLoadStep.ERROR, 0, e.message ?: "İndirme hatası")
            return@withContext false
        }
    }

    suspend fun downloadAndStreamParse(
        url: String,
        targetFile: java.io.File,
        onFirstPartReady: (suspend (List<M3uItem>, Map<ItemType, List<String>>) -> Unit)? = null,
        onBatchParsed: (suspend (List<M3uItem>) -> Unit)? = null,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val client = getUnsafeOkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .build()
        val tempFile = java.io.File(targetFile.parentFile, "${targetFile.name}.download")
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "Sunucuya bağlanılıyor...")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress(PlaylistLoadStep.ERROR, 0, "HTTP ${response.code}")
                return@withContext emptyList()
            }
            val body = response.body ?: return@withContext emptyList()
            val contentLength = body.contentLength()
            val rawInputStream = body.byteStream()
            val fileOut = java.io.FileOutputStream(tempFile).buffered(65536)

            onProgress(PlaylistLoadStep.DOWNLOADING, 10, "İndiriliyor...")
            var totalBytesRead = 0L

            val teeStream = object : InputStream() {
                override fun read(): Int {
                    val b = rawInputStream.read()
                    if (b != -1) {
                        fileOut.write(b)
                        totalBytesRead++
                    }
                    return b
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val n = rawInputStream.read(b, off, len)
                    if (n != -1) {
                        fileOut.write(b, off, n)
                        totalBytesRead += n
                    }
                    return n
                }
                override fun close() {
                    try { rawInputStream.close() } finally { fileOut.close() }
                }
            }

            val items = ArrayList<M3uItem>(10000)
            val liveGroups = LinkedHashSet<String>()
            val movieGroups = LinkedHashSet<String>()
            val seriesGroups = LinkedHashSet<String>()
            var firstPartEmitted = false
            var lastReportTime = System.currentTimeMillis()

            val seriesPatterns = listOf(
                java.util.regex.Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
                java.util.regex.Pattern.compile("^(.*?)\\s+(\\d+)\\.?\\s*Sezon\\s+(\\d+)\\.?\\s*B[öo]l[üu]m(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
                java.util.regex.Pattern.compile("^(.*?)\\s*Sezon\\s*(\\d+)\\s*B[öo]l[üu]m\\s*(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
            )

            BufferedReader(InputStreamReader(teeStream, Charsets.UTF_8), 65536).use { reader ->
                var line = reader.readLine()
                var currentTitle = ""
                var currentLogo: String? = null
                var currentGroup: String? = null
                var currentTvgId: String? = null
                var currentTvgName: String? = null

                while (line != null) {
                    var trimmed = line.trim()
                    if (trimmed.startsWith("#EXTM3U#EXTINF:")) {
                        trimmed = trimmed.substring(7)
                    }
                    if (trimmed.startsWith("#EXTINF:")) {
                        currentLogo = extractAttribute(trimmed, "tvg-logo")?.ifBlank { null }
                        currentGroup = extractAttribute(trimmed, "group-title")
                        currentTvgId = extractAttribute(trimmed, "tvg-id")?.ifBlank { null }
                        currentTvgName = extractAttribute(trimmed, "tvg-name")?.ifBlank { null }

                        val commaIndex = trimmed.lastIndexOf(',')
                        currentTitle = if (commaIndex != -1) {
                            trimmed.substring(commaIndex + 1).trim()
                        } else {
                            "Unknown Channel"
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

                        val mightBeSeries = type == ItemType.SERIES ||
                                currentTitle.contains(" S", ignoreCase = true) ||
                                currentTitle.contains("Sezon", ignoreCase = true) ||
                                currentTitle.contains("Bölüm", ignoreCase = true) ||
                                currentTitle.contains("Bolum", ignoreCase = true)

                        if (mightBeSeries) {
                            for (pattern in seriesPatterns) {
                                val matcher = pattern.matcher(currentTitle)
                                if (matcher.matches()) {
                                    seriesName = matcher.group(1)?.trim()?.trimEnd('-', '–', '—', ':', '|', ' ')?.trim()?.ifEmpty { currentTitle }
                                    season = matcher.group(2)?.toIntOrNull()
                                    episode = matcher.group(3)?.toIntOrNull()
                                    type = ItemType.SERIES
                                    break
                                }
                            }
                        }

                        if (type == ItemType.SERIES && seriesName == null) {
                            seriesName = currentTitle.trim().trimEnd('-', '–', '—', ':', '|', ' ').trim().ifEmpty { currentTitle }
                            season = 1
                            episode = 1
                        }

                        val groupKey = currentGroup ?: when (type) {
                            ItemType.LIVE -> "CANLI YAYINLAR"
                            ItemType.MOVIE -> "FİLMLER"
                            ItemType.SERIES -> "DİZİLER"
                        }

                        when (type) {
                            ItemType.LIVE -> liveGroups.add(groupKey)
                            ItemType.MOVIE -> movieGroups.add(groupKey)
                            ItemType.SERIES -> seriesGroups.add(groupKey)
                        }

                        items.add(
                            M3uItem(
                                title = currentTitle.ifEmpty { "Kanal ${items.size + 1}" },
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

                        // ILK PART ÇEKİLDİĞİNDE DİREK ANASAYFA GELİR (İlk 80 yayın)
                        if (!firstPartEmitted && items.size >= 80) {
                            firstPartEmitted = true
                            val initialMap = mapOf(
                                ItemType.LIVE to liveGroups.toList(),
                                ItemType.MOVIE to movieGroups.toList(),
                                ItemType.SERIES to seriesGroups.toList()
                            )
                            onFirstPartReady?.invoke(ArrayList(items), initialMap)
                        } else if (items.size % 1200 == 0) {
                            onBatchParsed?.invoke(ArrayList(items))
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 250) {
                            lastReportTime = now
                            val mb = String.format(java.util.Locale.US, "%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                            val pct = if (contentLength > 0) {
                                ((totalBytesRead.toDouble() / contentLength) * 100).toInt().coerceIn(10, 95)
                            } else {
                                (10 + (80 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 5000000.0)))).toInt().coerceIn(10, 90)
                            }
                            onProgress(PlaylistLoadStep.DOWNLOADING, pct, "$mb indirildi (${items.size} kanal)")
                        }

                        currentTitle = ""
                        currentLogo = null
                        currentGroup = null
                        currentTvgId = null
                        currentTvgName = null
                    }
                    line = reader.readLine()
                }
            }

            try { fileOut.flush() } catch (_: Exception) {}
            try { fileOut.close() } catch (_: Exception) {}

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            if (!firstPartEmitted && items.isNotEmpty()) {
                val initialMap = mapOf(
                    ItemType.LIVE to liveGroups.toList(),
                    ItemType.MOVIE to movieGroups.toList(),
                    ItemType.SERIES to seriesGroups.toList()
                )
                onFirstPartReady?.invoke(ArrayList(items), initialMap)
            }

            return@withContext items
        } catch (e: Throwable) {
            e.printStackTrace()
            try { tempFile.delete() } catch (_: Exception) {}
            onProgress(PlaylistLoadStep.ERROR, 0, e.message ?: "İndirme ve yükleme hatası")
            return@withContext emptyList()
        }
    }

    suspend fun scanPreviewAndCategories(
        file: java.io.File,
        maxPerCategory: Int = 15
    ): FastPreviewResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext FastPreviewResult(emptyList(), emptyMap())
        }

        val liveGroups = LinkedHashSet<String>()
        val movieGroups = LinkedHashSet<String>()
        val seriesGroups = LinkedHashSet<String>()
        val previewItems = ArrayList<M3uItem>(1500)
        val categoryCounts = HashMap<String, Int>()

        val seriesPatterns = listOf(
            java.util.regex.Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("^(.*?)\\s+(\\d+)\\.?\\s*Sezon\\s+(\\d+)\\.?\\s*B[öo]l[üu]m(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
            java.util.regex.Pattern.compile("^(.*?)\\s*Sezon\\s*(\\d+)\\s*B[öo]l[üu]m\\s*(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
        )

        try {
            BufferedReader(InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8), 65536).use { reader ->
                var line = reader.readLine()
                var currentTitle = ""
                var currentLogo: String? = null
                var currentGroup: String? = null
                var currentTvgId: String? = null
                var currentTvgName: String? = null

                while (line != null) {
                    var trimmed = line.trim()
                    if (trimmed.startsWith("#EXTM3U#EXTINF:")) {
                        trimmed = trimmed.substring(7)
                    }
                    if (trimmed.startsWith("#EXTINF:")) {
                        currentLogo = extractAttribute(trimmed, "tvg-logo")?.ifBlank { null }
                        currentGroup = extractAttribute(trimmed, "group-title")
                        currentTvgId = extractAttribute(trimmed, "tvg-id")?.ifBlank { null }
                        currentTvgName = extractAttribute(trimmed, "tvg-name")?.ifBlank { null }

                        val commaIndex = trimmed.lastIndexOf(',')
                        currentTitle = if (commaIndex != -1) {
                            trimmed.substring(commaIndex + 1).trim()
                        } else {
                            "Unknown Channel"
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

                        val mightBeSeries = type == ItemType.SERIES ||
                                currentTitle.contains(" S", ignoreCase = true) ||
                                currentTitle.contains("Sezon", ignoreCase = true) ||
                                currentTitle.contains("Bölüm", ignoreCase = true) ||
                                currentTitle.contains("Bolum", ignoreCase = true)

                        if (mightBeSeries) {
                            for (pattern in seriesPatterns) {
                                val matcher = pattern.matcher(currentTitle)
                                if (matcher.matches()) {
                                    seriesName = matcher.group(1)?.trim()?.trimEnd('-', '–', '—', ':', '|', ' ')?.trim()?.ifEmpty { currentTitle }
                                    season = matcher.group(2)?.toIntOrNull()
                                    episode = matcher.group(3)?.toIntOrNull()
                                    type = ItemType.SERIES
                                    break
                                }
                            }
                        }

                        if (type == ItemType.SERIES && seriesName == null) {
                            seriesName = currentTitle.trim().trimEnd('-', '–', '—', ':', '|', ' ').trim().ifEmpty { currentTitle }
                            season = 1
                            episode = 1
                        }

                        val groupKey = currentGroup ?: when (type) {
                            ItemType.LIVE -> "CANLI YAYINLAR"
                            ItemType.MOVIE -> "FİLMLER"
                            ItemType.SERIES -> "DİZİLER"
                        }

                        when (type) {
                            ItemType.LIVE -> liveGroups.add(groupKey)
                            ItemType.MOVIE -> movieGroups.add(groupKey)
                            ItemType.SERIES -> seriesGroups.add(groupKey)
                        }

                        val countForGroup = categoryCounts.getOrDefault(groupKey, 0)
                        if (countForGroup < maxPerCategory) {
                            categoryCounts[groupKey] = countForGroup + 1
                            previewItems.add(
                                M3uItem(
                                    title = currentTitle.ifEmpty { "Kanal ${previewItems.size + 1}" },
                                    url = trimmed,
                                    logo = currentLogo,
                                    group = currentGroup ?: groupKey,
                                    type = type,
                                    seriesName = seriesName,
                                    season = season,
                                    episode = episode,
                                    tvgId = currentTvgId,
                                    tvgName = currentTvgName
                                )
                            )
                        }

                        currentTitle = ""
                        currentLogo = null
                        currentGroup = null
                        currentTvgId = null
                        currentTvgName = null
                    }
                    line = reader.readLine()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val map = mapOf(
            ItemType.LIVE to liveGroups.toList(),
            ItemType.MOVIE to movieGroups.toList(),
            ItemType.SERIES to seriesGroups.toList()
        )
        return@withContext FastPreviewResult(previewItems, map)
    }

    suspend fun parseFromFileProgressive(
        file: java.io.File,
        onBatchParsed: (suspend (List<M3uItem>) -> Unit)? = null,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext emptyList()
        java.io.FileInputStream(file).buffered(65536).use { input ->
            parse(
                inputStream = input,
                totalBytes = file.length(),
                onParseProgress = onProgress,
                onPartialBatch = onBatchParsed
            )
        }
    }
    suspend fun parseFromBytes(
        bytes: ByteArray,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        parse(ByteArrayInputStream(bytes), bytes.size.toLong(), onProgress)
    }

    suspend fun parseFromFile(
        file: java.io.File,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext emptyList()
        java.io.FileInputStream(file).buffered(65536).use { input ->
            parse(input, file.length(), onProgress)
        }
    }

    suspend fun downloadAndParse(
        url: String,
        cachedBytes: ByteArray?,
        targetCacheFile: java.io.File? = null,
        onPartialBatch: (suspend (List<M3uItem>) -> Unit)? = null,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): ParseResult = withContext(Dispatchers.IO) {
        val client = getUnsafeOkHttpClient()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .build()
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()

                    val tempFile = if (targetCacheFile != null) {
                        java.io.File(targetCacheFile.parentFile, "${targetCacheFile.name}.download")
                    } else null

                    val fileOut = tempFile?.let { java.io.FileOutputStream(it).buffered(65536) }

                    var totalBytesRead = 0L
                    var lastReportTime = System.currentTimeMillis()

                    val wrappedStream = if (fileOut != null) {
                        object : InputStream() {
                            override fun read(): Int {
                                val b = inputStream.read()
                                if (b != -1) {
                                    fileOut.write(b)
                                    totalBytesRead++
                                }
                                return b
                            }
                            override fun read(b: ByteArray, off: Int, len: Int): Int {
                                val n = inputStream.read(b, off, len)
                                if (n != -1) {
                                    fileOut.write(b, off, n)
                                    totalBytesRead += n
                                    val now = System.currentTimeMillis()
                                    if (now - lastReportTime > 150) {
                                        lastReportTime = now
                                        val mb = String.format("%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                                        val pct = if (contentLength > 0) {
                                            ((totalBytesRead.toDouble() / contentLength) * 100).toInt().coerceIn(10, 95)
                                        } else {
                                            (10 + (80 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 5000000.0)))).toInt().coerceIn(10, 90)
                                        }
                                        // onProgress can be called during stream
                                    }
                                }
                                return n
                            }
                            override fun close() {
                                try { inputStream.close() } finally { fileOut.close() }
                            }
                        }
                    } else {
                        inputStream
                    }

                    onProgress(PlaylistLoadStep.DOWNLOADING, 10, "")

                    val items = parse(
                        inputStream = wrappedStream,
                        totalBytes = contentLength,
                        onParseProgress = { pct, detail ->
                            onProgress(PlaylistLoadStep.PROCESSING, pct, detail)
                        },
                        onPartialBatch = onPartialBatch
                    )

                    try { fileOut?.flush() } catch (_: Exception) {}
                    try { fileOut?.close() } catch (_: Exception) {}

                    if (tempFile != null && targetCacheFile != null && tempFile.exists() && tempFile.length() > 0L) {
                        try {
                            if (targetCacheFile.exists()) targetCacheFile.delete()
                            tempFile.renameTo(targetCacheFile)
                        } catch (_: Exception) {
                            try { tempFile.copyTo(targetCacheFile, overwrite = true) } catch (_: Exception) {}
                        }
                    }

                    onProgress(PlaylistLoadStep.COMPLETED, 100, "${items.size} kanal")
                    return@withContext ParseResult(false, items, null)
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
                    
                    val items = parse(
                        inputStream = ByteArrayInputStream(downloadedBytes),
                        totalBytes = downloadedBytes.size.toLong(),
                        onParseProgress = { pct, detail ->
                            onProgress(PlaylistLoadStep.PROCESSING, 50 + (pct / 2), detail)
                        }
                    )
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
        onParseProgress: suspend (Int, String) -> Unit = { _, _ -> },
        onPartialBatch: (suspend (List<M3uItem>) -> Unit)? = null
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
                val mightBeSeries = type == ItemType.SERIES ||
                        currentTitle.contains(" S", ignoreCase = true) ||
                        currentTitle.contains("Sezon", ignoreCase = true) ||
                        currentTitle.contains("Bölüm", ignoreCase = true) ||
                        currentTitle.contains("Bolum", ignoreCase = true)

                if (mightBeSeries) {
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

                if (onPartialBatch != null) {
                    val count = items.size
                    if (count == 400 || (count > 400 && count % 1500 == 0)) {
                        onPartialBatch(ArrayList(items))
                    }
                }

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
        val keyDouble = "$attribute=\""
        val startDouble = line.indexOf(keyDouble)
        if (startDouble != -1) {
            val valStart = startDouble + keyDouble.length
            val end = line.indexOf('"', valStart)
            if (end != -1) {
                return line.substring(valStart, end)
            }
        }
        val keySingle = "$attribute=\'"
        val startSingle = line.indexOf(keySingle)
        if (startSingle != -1) {
            val valStart = startSingle + keySingle.length
            val end = line.indexOf('\'', valStart)
            if (end != -1) {
                return line.substring(valStart, end)
            }
        }
        return null
    }
}
