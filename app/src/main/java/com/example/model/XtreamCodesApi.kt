package com.example.model

import android.content.Context
import com.example.model.db.AppDatabase
import com.example.parser.FastPreviewResult
import com.example.parser.ItemType
import com.example.parser.M3uItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class XtreamCredentials(
    val hostUrl: String,
    val username: String,
    val password: String
)

data class XtreamCategory(
    val categoryId: String,
    val categoryName: String,
    val type: ItemType,
    val order: Int = 0
)

object XtreamCodesApi {
    private val client: OkHttpClient by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 6
                maxRequestsPerHost = 4
            }
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
        } catch (_: Exception) {
            OkHttpClient()
        }
    }

    @Volatile
    var isRateLimitedUntil: Long = 0L

    fun isCurrentlyRateLimited(): Boolean = System.currentTimeMillis() < isRateLimitedUntil

    fun getCredsHash(creds: XtreamCredentials): String {
        val key = "${creds.hostUrl.trim().lowercase()}:${creds.username.trim().lowercase()}"
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            md.digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.hashCode().toString()
        }
    }

    // In-memory cache for category streams: credsHash_type_categoryName -> List<M3uItem>
    val categoryStreamsCache = ConcurrentHashMap<String, List<M3uItem>>()
    // Map of credsHash_type_categoryName -> categoryId
    val categoryNameToIdMap = ConcurrentHashMap<String, String>()
    // In-memory cache for series episodes: credsHash_seriesId -> List<M3uItem>
    val seriesEpisodesCache = ConcurrentHashMap<String, List<M3uItem>>()
    val playlistExpireDateCache = ConcurrentHashMap<String, String>()

    fun getCategoryId(creds: XtreamCredentials, type: ItemType, categoryName: String): String? {
        val key = "${getCredsHash(creds)}_${type.name}_$categoryName"
        return categoryNameToIdMap[key]
    }

    fun clearAllCaches(context: Context? = null) {
        categoryStreamsCache.clear()
        categoryNameToIdMap.clear()
        seriesEpisodesCache.clear()
        playlistExpireDateCache.clear()
        if (context != null) {
            try {
                context.cacheDir.listFiles()?.filter { it.name.startsWith("xtream_") }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
                context.filesDir.listFiles()?.filter { it.name.startsWith("xtream_") }?.forEach {
                    try { it.delete() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun formatDetailedExpiration(expRaw: String?): String {
        if (expRaw.isNullOrBlank() || expRaw == "null" || expRaw == "0") return "Süresiz"
        val trimmed = expRaw.trim()
        val nowMs = System.currentTimeMillis()

        val tsSeconds = trimmed.toLongOrNull()?.let { num ->
            if (num > 100_000_000_000L) num / 1000L else num
        }

        val targetMs: Long? = if (tsSeconds != null && tsSeconds > 0L) {
            tsSeconds * 1000L
        } else {
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            )
            var parsedDate: java.util.Date? = null
            for (f in formats) {
                try {
                    parsedDate = f.parse(trimmed)
                    if (parsedDate != null) break
                } catch (_: Exception) {}
            }
            parsedDate?.time
        }

        if (targetMs != null) {
            val diffMs = targetMs - nowMs
            val diffSec = diffMs / 1000L
            val dateFmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            val formattedDate = dateFmt.format(java.util.Date(targetMs))

            return when {
                diffSec <= 0 -> "Süresi Doldu ($formattedDate)"
                diffSec < 60 -> "1 dk kaldı ($formattedDate)"
                diffSec < 3600 -> {
                    val mins = diffSec / 60
                    "$mins dk kaldı ($formattedDate)"
                }
                diffSec < 86400 -> {
                    val hours = diffSec / 3600
                    val mins = (diffSec % 3600) / 60
                    if (mins > 0) "$hours sa $mins dk kaldı ($formattedDate)" else "$hours sa kaldı ($formattedDate)"
                }
                diffSec < 86400 * 3 -> {
                    val hours = diffSec / 3600
                    "$hours saat kaldı ($formattedDate)"
                }
                else -> {
                    val days = diffSec / 86400
                    "$days gün kaldı ($formattedDate)"
                }
            }
        }
        return trimmed
    }

    suspend fun getPlaylistExpireDate(hostUrl: String, username: String, password: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "$hostUrl:$username"
        playlistExpireDateCache[cacheKey]?.let { return@withContext it }
        if (hostUrl.isBlank() || username.isBlank() || password.isBlank()) return@withContext null
        if (isCurrentlyRateLimited()) return@withContext null

        try {
            val cleanHost = hostUrl.trim().trimEnd('/')
                .substringBefore("/get.php")
                .substringBefore("/player_api.php")
                .substringBefore("/xmltv.php")
                .substringBefore('?')
            val url = "$cleanHost/player_api.php?username=$username&password=$password"
            val jsonStr = httpGet(url) ?: return@withContext null
            val obj = JSONObject(jsonStr)
            val userInfo = obj.optJSONObject("user_info") ?: return@withContext null
            val expRaw = userInfo.optString("exp_date", "").trim()
            if (expRaw.isNotBlank() && expRaw != "null" && expRaw != "0") {
                val formatted = formatDetailedExpiration(expRaw)
                playlistExpireDateCache[cacheKey] = formatted
                return@withContext formatted
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    fun extractCredentials(rawUrl: String, context: Context? = null): XtreamCredentials? {
        val clean = rawUrl.trim()
        if (clean.isNotBlank() && (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true))) {
            val userMatch = Regex("[?&](?:username|user)=([^&]+)", RegexOption.IGNORE_CASE).find(clean)
            val passMatch = Regex("[?&](?:password|pass)=([^&]+)", RegexOption.IGNORE_CASE).find(clean)
            if (userMatch != null && passMatch != null) {
                val user = userMatch.groupValues[1]
                val pass = passMatch.groupValues[1]
                val host = clean.substringBefore("/get.php")
                    .substringBefore("/player_api.php")
                    .substringBefore("/xmltv.php")
                    .substringBefore('?')
                    .trimEnd('/')
                if (host.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    return XtreamCredentials(host, user, pass)
                }
            }

            val pathRegex = Regex("^(https?://[^/]+)/(?:live|movie|series)/([^/]+)/([^/]+)", RegexOption.IGNORE_CASE)
            val pathMatch = pathRegex.find(clean)
            if (pathMatch != null) {
                return XtreamCredentials(pathMatch.groupValues[1], pathMatch.groupValues[2], pathMatch.groupValues[3])
            }
        }

        if (context != null) {
            try {
                val db = AppDatabase.getDatabase(context.applicationContext)
                val playlists = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    db.iptvDao().getAllPlaylistsSync()
                }
                val matching = playlists.firstOrNull { p ->
                    p.username.isNotBlank() && p.password.isNotBlank() &&
                    (clean.contains(p.username, ignoreCase = true) || clean.contains(p.hostUrl, ignoreCase = true) || p.hostUrl.contains(clean, ignoreCase = true))
                }

                if (matching != null) {
                    val cleanHost = matching.hostUrl.trim().trimEnd('/')
                    val host = cleanHost.substringBefore("/get.php")
                        .substringBefore("/player_api.php")
                        .substringBefore("/xmltv.php")
                        .substringBefore('?')
                        .trimEnd('/')
                    return XtreamCredentials(host, matching.username.trim(), matching.password.trim())
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun getApiBaseUrl(creds: XtreamCredentials): String {
        val host = creds.hostUrl.trim().trimEnd('/')
        return "$host/player_api.php?username=${creds.username}&password=${creds.password}"
    }

    private fun httpGet(url: String): String? {
        if (isCurrentlyRateLimited()) {
            return null
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVSmartersPro/3.1.5 (Linux; Android 12)")
            .header("Accept", "*/*")
            .header("Connection", "keep-alive")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.code == 429 || response.code == 503) {
                // Server rate limit triggered: back off for 4 seconds
                isRateLimitedUntil = System.currentTimeMillis() + 4_000L
                null
            } else if (response.isSuccessful) {
                response.body?.string()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCategories(creds: XtreamCredentials, type: ItemType): List<XtreamCategory> = withContext(Dispatchers.IO) {
        val action = when (type) {
            ItemType.LIVE -> "get_live_categories"
            ItemType.MOVIE -> "get_vod_categories"
            ItemType.SERIES -> "get_series_categories"
        }
        val url = "${getApiBaseUrl(creds)}&action=$action"
        val jsonStr = httpGet(url) ?: return@withContext emptyList()
        val result = mutableListOf<XtreamCategory>()
        var hasExplicitNonZeroOrder = false
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val catId = obj.optString("category_id", obj.optInt("category_id", 0).toString()).trim()
                val catName = obj.optString("category_name").trim()
                val order = when {
                    obj.has("order") -> obj.optInt("order", i)
                    obj.has("category_order") -> obj.optInt("category_order", i)
                    else -> i
                }
                if (catId.isNotBlank() && catName.isNotBlank()) {
                    val key = "${getCredsHash(creds)}_${type.name}_$catName"
                    categoryNameToIdMap[key] = catId
                    result.add(XtreamCategory(catId, catName, type, order))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Sunucunun / buketin gönderdiği orijinal JSON dizi sırasını birebir koru
        return@withContext result
    }

    suspend fun getStreamsForCategory(
        creds: XtreamCredentials,
        categoryId: String,
        categoryName: String,
        type: ItemType,
        context: Context? = null
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val credsHash = getCredsHash(creds)
        val cacheKey = "${credsHash}_${type.name}_$categoryName"
        categoryStreamsCache[cacheKey]?.let { return@withContext it }

        // Check local disk cache if available
        if (context != null) {
            val diskFile = File(context.cacheDir, "xtream_${credsHash}_${type.name}_$categoryId.json")
            if (diskFile.exists() && diskFile.length() > 0L && System.currentTimeMillis() - diskFile.lastModified() < 3600_000 * 12) {
                try {
                    val diskJson = diskFile.readText(Charsets.UTF_8)
                    val parsed = parseStreamsJson(diskJson, creds, categoryName, type)
                    if (parsed.isNotEmpty()) {
                        categoryStreamsCache[cacheKey] = parsed
                        return@withContext parsed
                    }
                } catch (_: Exception) {}
            }
        }

        val action = when (type) {
            ItemType.LIVE -> "get_live_streams"
            ItemType.MOVIE -> "get_vod_streams"
            ItemType.SERIES -> "get_series"
        }
        val url = "${getApiBaseUrl(creds)}&action=$action&category_id=$categoryId"
        val jsonStr = httpGet(url) ?: return@withContext emptyList()

        if (context != null && jsonStr.isNotBlank()) {
            try {
                val diskFile = File(context.cacheDir, "xtream_${credsHash}_${type.name}_$categoryId.json")
                diskFile.writeText(jsonStr, Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        val items = parseStreamsJson(jsonStr, creds, categoryName, type)
        if (items.isNotEmpty()) {
            categoryStreamsCache[cacheKey] = items
        }
        return@withContext items
    }

    private fun parseStreamsJson(
        jsonStr: String,
        creds: XtreamCredentials,
        categoryName: String,
        type: ItemType
    ): List<M3uItem> {
        val result = mutableListOf<M3uItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            val host = creds.hostUrl.trim().trimEnd('/')
            val seriesPatterns = listOf(
                java.util.regex.Pattern.compile("^(.*?)\\s*S(\\d+)\\s*E(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
                java.util.regex.Pattern.compile("^(.*?)\\s+(\\d+)\\.?\\s*Sezon\\s+(\\d+)\\.?\\s*B[öo]l[üu]m(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE),
                java.util.regex.Pattern.compile("^(.*?)\\s*Sezon\\s*(\\d+)\\s*B[öo]l[üu]m\\s*(\\d+)(.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
            )

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                when (type) {
                    ItemType.LIVE -> {
                        val streamId = obj.optString("stream_id", obj.optInt("stream_id", 0).toString())
                        val name = obj.optString("name", "Kanal $streamId").trim()
                        val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                        val tvgId = obj.optString("epg_channel_id").takeIf { it.isNotBlank() } ?: streamId
                        val streamUrl = "$host/live/${creds.username}/${creds.password}/$streamId.ts"
                        result.add(
                            M3uItem(
                                title = name,
                                url = streamUrl,
                                logo = icon,
                                group = categoryName,
                                type = ItemType.LIVE,
                                tvgId = tvgId,
                                tvgName = name
                            )
                        )
                    }
                    ItemType.MOVIE -> {
                        val streamId = obj.optString("stream_id", obj.optInt("stream_id", 0).toString())
                        val name = obj.optString("name", "Film $streamId").trim()
                        val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                        val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                        val streamUrl = "$host/movie/${creds.username}/${creds.password}/$streamId.$ext"
                        result.add(
                            M3uItem(
                                title = name,
                                url = streamUrl,
                                logo = icon,
                                group = categoryName,
                                type = ItemType.MOVIE,
                                tvgId = streamId,
                                tvgName = name
                            )
                        )
                    }
                    ItemType.SERIES -> {
                        val seriesId = obj.optString("series_id", obj.optInt("series_id", 0).toString())
                        val name = obj.optString("name", "Dizi $seriesId").trim()
                        val cover = obj.optString("cover").takeIf { it.isNotBlank() }
                        val seriesUrl = "$host/series_placeholder/$seriesId"
                        result.add(
                            M3uItem(
                                title = name,
                                url = seriesUrl,
                                logo = cover,
                                group = categoryName,
                                type = ItemType.SERIES,
                                seriesName = name,
                                tvgId = seriesId,
                                tvgName = name
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    suspend fun getSeriesEpisodes(
        creds: XtreamCredentials,
        seriesId: String,
        seriesName: String,
        categoryName: String
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val credsHash = getCredsHash(creds)
        val cacheKey = "${credsHash}_$seriesId"
        seriesEpisodesCache[cacheKey]?.let { return@withContext it }
        val url = "${getApiBaseUrl(creds)}&action=get_series_info&series_id=$seriesId"
        val jsonStr = httpGet(url) ?: return@withContext emptyList()
        val result = mutableListOf<M3uItem>()
        val host = creds.hostUrl.trim().trimEnd('/')

        try {
            val rootObj = JSONObject(jsonStr)
            val infoObj = rootObj.optJSONObject("info")
            val cover = infoObj?.optString("cover")?.takeIf { it.isNotBlank() }
            val episodesObj = rootObj.optJSONObject("episodes")
            if (episodesObj != null) {
                val seasonKeys = episodesObj.keys()
                while (seasonKeys.hasNext()) {
                    val seasonKey = seasonKeys.next()
                    val sNum = seasonKey.toIntOrNull() ?: 1
                    val epArray = episodesObj.optJSONArray(seasonKey) ?: continue
                    for (i in 0 until epArray.length()) {
                        val ep = epArray.getJSONObject(i)
                        val epId = ep.optString("id", ep.optInt("id", 0).toString())
                        val epNum = ep.optInt("episode_num", i + 1)
                        val title = ep.optString("title", "$seriesName S${sNum} E$epNum")
                        val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                        val epUrl = "$host/series/${creds.username}/${creds.password}/$epId.$ext"
                        result.add(
                            M3uItem(
                                title = "$seriesName S${sNum} E$epNum - $title",
                                url = epUrl,
                                logo = cover,
                                group = categoryName,
                                type = ItemType.SERIES,
                                seriesName = seriesName,
                                season = sNum,
                                episode = epNum,
                                tvgId = epId,
                                tvgName = title
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (result.isNotEmpty()) {
            seriesEpisodesCache[cacheKey] = result
        }
        return@withContext result
    }

    suspend fun getAllStreams(
        creds: XtreamCredentials,
        type: ItemType,
        categoryMap: Map<String, String>,
        context: Context? = null
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val credsHash = getCredsHash(creds)
        val diskFile = if (context != null) File(context.cacheDir, "xtream_${credsHash}_${type.name}_all.json") else null
        // Check disk cache (valid for 6 hours)
        if (diskFile != null && diskFile.exists() && diskFile.length() > 0L) {
            val age = System.currentTimeMillis() - diskFile.lastModified()
            if (age < 3600_000L * 6) {
                try {
                    val diskJson = diskFile.readText(Charsets.UTF_8)
                    val parsed = parseBulkStreamsJson(diskJson, creds, type, categoryMap)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                } catch (_: Exception) {}
            }
        }

        val action = when (type) {
            ItemType.LIVE -> "get_live_streams"
            ItemType.MOVIE -> "get_vod_streams"
            ItemType.SERIES -> "get_series"
        }
        val url = "${getApiBaseUrl(creds)}&action=$action"
        val jsonStr = httpGet(url)
        if (jsonStr.isNullOrBlank()) {
            // If network failed (e.g. rate limit cooldown or offline), read older disk cache!
            if (diskFile != null && diskFile.exists() && diskFile.length() > 0L) {
                try {
                    val diskJson = diskFile.readText(Charsets.UTF_8)
                    val parsed = parseBulkStreamsJson(diskJson, creds, type, categoryMap)
                    if (parsed.isNotEmpty()) {
                        return@withContext parsed
                    }
                } catch (_: Exception) {}
            }
            return@withContext emptyList()
        }

        if (diskFile != null && jsonStr.isNotBlank()) {
            try {
                diskFile.writeText(jsonStr, Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        return@withContext parseBulkStreamsJson(jsonStr, creds, type, categoryMap)
    }

    private fun parseBulkStreamsJson(
        jsonStr: String,
        creds: XtreamCredentials,
        type: ItemType,
        categoryMap: Map<String, String>
    ): List<M3uItem> {
        val result = mutableListOf<M3uItem>()
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("[")) {
            return emptyList()
        }
        try {
            val jsonArray = JSONArray(trimmed)
            val host = creds.hostUrl.trim().trimEnd('/')

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val catId = obj.optString("category_id", obj.optInt("category_id", 0).toString()).trim()
                val categoryName = categoryMap[catId]?.ifBlank { "Genel" } ?: "Genel"

                when (type) {
                    ItemType.LIVE -> {
                        val streamId = obj.optString("stream_id", obj.optInt("stream_id", 0).toString())
                        val name = obj.optString("name", "Kanal $streamId").trim()
                        val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                        val tvgId = obj.optString("epg_channel_id").takeIf { it.isNotBlank() } ?: streamId
                        val streamUrl = "$host/live/${creds.username}/${creds.password}/$streamId.ts"
                        result.add(
                            M3uItem(
                                title = name,
                                url = streamUrl,
                                logo = icon,
                                group = categoryName,
                                type = ItemType.LIVE,
                                tvgId = tvgId,
                                tvgName = name
                            )
                        )
                    }
                    ItemType.MOVIE -> {
                        val streamId = obj.optString("stream_id", obj.optInt("stream_id", 0).toString())
                        val name = obj.optString("name", "Film $streamId").trim()
                        val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                        val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                        val streamUrl = "$host/movie/${creds.username}/${creds.password}/$streamId.$ext"
                        result.add(
                            M3uItem(
                                title = name,
                                url = streamUrl,
                                logo = icon,
                                group = categoryName,
                                type = ItemType.MOVIE,
                                tvgId = streamId,
                                tvgName = name
                            )
                        )
                    }
                    ItemType.SERIES -> {
                        val seriesId = obj.optString("series_id", obj.optInt("series_id", 0).toString())
                        val name = obj.optString("name", "Dizi $seriesId").trim()
                        val cover = obj.optString("cover").takeIf { it.isNotBlank() }
                        val seriesUrl = "$host/series_placeholder/$seriesId"
                        result.add(
                            M3uItem(
                                title = name,
                                url = seriesUrl,
                                logo = cover,
                                group = categoryName,
                                type = ItemType.SERIES,
                                seriesName = name,
                                tvgId = seriesId,
                                tvgName = name
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    suspend fun initializeCategoriesAndPreview(
        context: Context,
        creds: XtreamCredentials,
        topCount: Int = 10,
        onStreamingBatch: ((categories: Map<ItemType, List<String>>, partialItems: List<M3uItem>, stepDetail: String) -> Unit)? = null
    ): FastPreviewResult = withContext(Dispatchers.IO) {
        // 1. Fetch categories for Live, VOD, and Series sequentially with small pauses to prevent burst bans
        val liveCats = getCategories(creds, ItemType.LIVE)
        kotlinx.coroutines.delay(100)
        val vodCats = getCategories(creds, ItemType.MOVIE)
        kotlinx.coroutines.delay(100)
        val seriesCats = getCategories(creds, ItemType.SERIES)
        kotlinx.coroutines.delay(100)

        val liveCatsMap = liveCats.associate { it.categoryId to it.categoryName }
        val vodCatsMap = vodCats.associate { it.categoryId to it.categoryName }
        val seriesCatsMap = seriesCats.associate { it.categoryId to it.categoryName }

        val map = mapOf(
            ItemType.LIVE to liveCats.map { it.categoryName },
            ItemType.MOVIE to vodCats.map { it.categoryName },
            ItemType.SERIES to seriesCats.map { it.categoryName }
        )

        onStreamingBatch?.invoke(map, emptyList(), "Kategoriler hazır...")

        val previewItems = ArrayList<M3uItem>()

        // 2. Fetch bulk streams: ONE single request per type (total only 3 requests, avoiding 300+ category loops!)
        val liveStreams = getAllStreams(creds, ItemType.LIVE, liveCatsMap, context)
        if (liveStreams.isNotEmpty()) {
            previewItems.addAll(liveStreams)
            onStreamingBatch?.invoke(map, previewItems.toList(), "${liveStreams.size} canlı yayın hazır...")
        }

        kotlinx.coroutines.delay(150)
        val vodStreams = getAllStreams(creds, ItemType.MOVIE, vodCatsMap, context)
        if (vodStreams.isNotEmpty()) {
            previewItems.addAll(vodStreams)
            onStreamingBatch?.invoke(map, previewItems.toList(), "${previewItems.size} yayın aktif...")
        }

        kotlinx.coroutines.delay(150)
        val seriesStreams = getAllStreams(creds, ItemType.SERIES, seriesCatsMap, context)
        if (seriesStreams.isNotEmpty()) {
            previewItems.addAll(seriesStreams)
            onStreamingBatch?.invoke(map, previewItems.toList(), "${previewItems.size} yayın aktif...")
        }

        // Fallback: If bulk endpoints returned empty (unsupported or restricted server),
        // fetch ONLY top 3 categories per type sequentially (max 9 requests total)
        if (previewItems.isEmpty()) {
            for (cat in liveCats.take(3)) {
                val streams = getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.LIVE, context)
                if (streams.isNotEmpty()) {
                    previewItems.addAll(streams)
                    onStreamingBatch?.invoke(map, previewItems.toList(), "${previewItems.size} yayın aktif...")
                }
                kotlinx.coroutines.delay(150)
            }
            for (cat in vodCats.take(3)) {
                val streams = getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.MOVIE, context)
                if (streams.isNotEmpty()) {
                    previewItems.addAll(streams)
                    onStreamingBatch?.invoke(map, previewItems.toList(), "${previewItems.size} yayın aktif...")
                }
                kotlinx.coroutines.delay(150)
            }
            for (cat in seriesCats.take(3)) {
                val streams = getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.SERIES, context)
                if (streams.isNotEmpty()) {
                    previewItems.addAll(streams)
                    onStreamingBatch?.invoke(map, previewItems.toList(), "${previewItems.size} yayın aktif...")
                }
                kotlinx.coroutines.delay(150)
            }
        }

        return@withContext FastPreviewResult(previewItems, map)
    }
}
