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
    val type: ItemType
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
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        } catch (_: Exception) {
            OkHttpClient()
        }
    }

    // In-memory cache for category streams: (ItemType to categoryName) -> List<M3uItem>
    val categoryStreamsCache = ConcurrentHashMap<Pair<ItemType, String>, List<M3uItem>>()
    // Map of (ItemType to categoryName) -> categoryId
    val categoryNameToIdMap = ConcurrentHashMap<Pair<ItemType, String>, String>()
    // In-memory cache for series episodes: seriesId -> List<M3uItem>
    val seriesEpisodesCache = ConcurrentHashMap<String, List<M3uItem>>()

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
                } ?: playlists.firstOrNull { it.username.isNotBlank() && it.password.isNotBlank() && (it.hostUrl.startsWith("http://", ignoreCase = true) || it.hostUrl.startsWith("https://", ignoreCase = true)) }

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
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate")
            .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else null
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
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val catId = obj.optString("category_id", obj.optInt("category_id", 0).toString()).trim()
                val catName = obj.optString("category_name").trim()
                if (catId.isNotBlank() && catName.isNotBlank()) {
                    categoryNameToIdMap[type to catName] = catId
                    result.add(XtreamCategory(catId, catName, type))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }

    suspend fun getStreamsForCategory(
        creds: XtreamCredentials,
        categoryId: String,
        categoryName: String,
        type: ItemType,
        context: Context? = null
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        val cacheKey = type to categoryName
        categoryStreamsCache[cacheKey]?.let { return@withContext it }

        // Check local disk cache if available
        if (context != null) {
            val diskFile = File(context.cacheDir, "xtream_${type.name}_$categoryId.json")
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
                val diskFile = File(context.cacheDir, "xtream_${type.name}_$categoryId.json")
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
        seriesEpisodesCache[seriesId]?.let { return@withContext it }
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
            seriesEpisodesCache[seriesId] = result
        }
        return@withContext result
    }

    suspend fun initializeCategoriesAndPreview(
        context: Context,
        creds: XtreamCredentials,
        topCount: Int = 10
    ): FastPreviewResult = withContext(Dispatchers.IO) {
        // 1. Fetch categories for Live, VOD, and Series in parallel (takes ~1-2 sec)
        val liveDeferred = async { getCategories(creds, ItemType.LIVE) }
        val vodDeferred = async { getCategories(creds, ItemType.MOVIE) }
        val seriesDeferred = async { getCategories(creds, ItemType.SERIES) }

        val liveCats = liveDeferred.await()
        val vodCats = vodDeferred.await()
        val seriesCats = seriesDeferred.await()

        val map = mapOf(
            ItemType.LIVE to liveCats.map { it.categoryName },
            ItemType.MOVIE to vodCats.map { it.categoryName },
            ItemType.SERIES to seriesCats.map { it.categoryName }
        )

        // 2. Fetch initial streams for the top categories (ilk 10 liste per type) in parallel
        // Live, Movies, and Series are immediately populated so user never sees empty screens!
        val previewItems = ArrayList<M3uItem>()
        val streamJobs = mutableListOf<kotlinx.coroutines.Deferred<List<M3uItem>>>()

        liveCats.take(topCount).forEach { cat ->
            streamJobs.add(async { getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.LIVE, context) })
        }
        vodCats.take(topCount).forEach { cat ->
            streamJobs.add(async { getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.MOVIE, context) })
        }
        seriesCats.take(topCount).forEach { cat ->
            streamJobs.add(async { getStreamsForCategory(creds, cat.categoryId, cat.categoryName, ItemType.SERIES, context) })
        }

        streamJobs.forEach { job ->
            try {
                previewItems.addAll(job.await())
            } catch (_: Exception) {}
        }

        return@withContext FastPreviewResult(previewItems, map)
    }
}
