package com.example.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

object XtreamApiClient {
    const val DEFAULT_USER_AGENT = "IPTVSmarters/1.0 (Linux;Android)"

    data class XtreamCredentials(val baseUrl: String, val username: String, val password: String)

    fun parseXtreamUrl(url: String): XtreamCredentials? {
        val clean = url.trim()
        val regex = Regex("^(https?://[^/]+)/get\\.php\\?.*[?&]username=([^&]+).*[?&]password=([^&]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(clean)
        if (match != null) {
            return XtreamCredentials(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        // Fallback for cases where password appears before username or different parameter ordering
        if (clean.contains("/get.php", ignoreCase = true)) {
            val baseMatch = Regex("^(https?://[^/]+)/get\\.php", RegexOption.IGNORE_CASE).find(clean) ?: return null
            val uMatch = Regex("[?&]username=([^&]+)", RegexOption.IGNORE_CASE).find(clean) ?: return null
            val pMatch = Regex("[?&]password=([^&]+)", RegexOption.IGNORE_CASE).find(clean) ?: return null
            return XtreamCredentials(baseMatch.groupValues[1], uMatch.groupValues[1], pMatch.groupValues[1])
        }
        return null
    }

    suspend fun fetchViaApi(
        creds: XtreamCredentials,
        onProgress: ((Int, String) -> Unit)? = null
    ): List<M3uItem>? = withContext(Dispatchers.IO) {
        try {
            val client = M3uParser.getUnsafeOkHttpClient()
            val base = creds.baseUrl.trimEnd('/')
            val u = creds.username
            val p = creds.password

            fun apiGet(action: String): JSONArray? {
                val url = "$base/player_api.php?username=$u&password=$p&action=$action"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "gzip, deflate")
                    .build()
                val resp = try { client.newCall(req).execute() } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                return try { JSONArray(body) } catch (_: Exception) { null }
            }

            onProgress?.invoke(20, "Xtream sunucusuna bağlanılıyor...")

            coroutineScope {
                // 1. Kategoriler ve yayın listeleri paralel olarak eşzamanlı indirilir
                val liveCatsDeferred = async { apiGet("get_live_categories") }
                val vodCatsDeferred = async { apiGet("get_vod_categories") }
                val seriesCatsDeferred = async { apiGet("get_series_categories") }

                val liveStreamsDeferred = async { apiGet("get_live_streams") }
                val vodStreamsDeferred = async { apiGet("get_vod_streams") }
                val seriesListDeferred = async { apiGet("get_series") }

                val liveCategories = liveCatsDeferred.await()
                val vodCategories = vodCatsDeferred.await()
                val seriesCategories = seriesCatsDeferred.await()

                onProgress?.invoke(50, "Kategoriler alındı, yayınlar hazırlanıyor...")

                val liveStreams = liveStreamsDeferred.await()
                val vodStreams = vodStreamsDeferred.await()
                val seriesList = seriesListDeferred.await()

                onProgress?.invoke(80, "Yayın listesi oluşturuluyor...")

                val items = mutableListOf<M3uItem>()

                // 1. Canlı TV (Live)
                val liveCatMap = mutableMapOf<String, String>()
                if (liveCategories != null) {
                    for (i in 0 until liveCategories.length()) {
                        val c = liveCategories.optJSONObject(i) ?: continue
                        liveCatMap[c.optString("category_id")] = c.optString("category_name")
                    }
                }
                if (liveStreams != null) {
                    for (i in 0 until liveStreams.length()) {
                        val s = liveStreams.optJSONObject(i) ?: continue
                        val streamId = s.optInt("stream_id")
                        val ext = s.optString("container_extension", "ts").ifBlank { "ts" }
                        val tvgId = s.optString("epg_channel_id").takeIf { !it.isNullOrBlank() && it != "null" } ?: streamId.toString()
                        val title = s.optString("name")
                        items.add(
                            M3uItem(
                                title = title,
                                url = "$base/live/$u/$p/$streamId.$ext",
                                logo = s.optString("stream_icon").takeIf { !it.isNullOrBlank() && it != "null" },
                                group = liveCatMap[s.optString("category_id")] ?: "",
                                type = ItemType.LIVE,
                                tvgId = tvgId,
                                tvgName = title
                            )
                        )
                    }
                }

                // 2. Filmler (VOD)
                val vodCatMap = mutableMapOf<String, String>()
                if (vodCategories != null) {
                    for (i in 0 until vodCategories.length()) {
                        val c = vodCategories.optJSONObject(i) ?: continue
                        vodCatMap[c.optString("category_id")] = c.optString("category_name")
                    }
                }
                if (vodStreams != null) {
                    for (i in 0 until vodStreams.length()) {
                        val s = vodStreams.optJSONObject(i) ?: continue
                        val streamId = s.optInt("stream_id")
                        val ext = s.optString("container_extension", "mp4").ifBlank { "mp4" }
                        val title = s.optString("name")
                        items.add(
                            M3uItem(
                                title = title,
                                url = "$base/movie/$u/$p/$streamId.$ext",
                                logo = s.optString("stream_icon").takeIf { !it.isNullOrBlank() && it != "null" },
                                group = vodCatMap[s.optString("category_id")] ?: "",
                                type = ItemType.MOVIE,
                                tvgId = streamId.toString(),
                                tvgName = title
                            )
                        )
                    }
                }

                // 3. Diziler (Series) - Diziler on-demand (tıklandığında bölüm çekecek şekilde) anında eklenir
                val seriesCatMap = mutableMapOf<String, String>()
                if (seriesCategories != null) {
                    for (i in 0 until seriesCategories.length()) {
                        val c = seriesCategories.optJSONObject(i) ?: continue
                        seriesCatMap[c.optString("category_id")] = c.optString("category_name")
                    }
                }
                if (seriesList != null) {
                    for (i in 0 until seriesList.length()) {
                        val s = seriesList.optJSONObject(i) ?: continue
                        val seriesId = s.optInt("series_id")
                        val seriesName = s.optString("name")
                        val seriesLogo = s.optString("cover").takeIf { !it.isNullOrBlank() && it != "null" }
                        val groupName = seriesCatMap[s.optString("category_id")] ?: ""

                        items.add(
                            M3uItem(
                                title = seriesName,
                                url = "$base/series_placeholder/$seriesId",
                                logo = seriesLogo,
                                group = groupName,
                                type = ItemType.SERIES,
                                seriesName = seriesName,
                                season = 1,
                                episode = 1,
                                tvgId = seriesId.toString(),
                                tvgName = seriesName
                            )
                        )
                    }
                }

                onProgress?.invoke(100, "${items.size} yayın hazır")
                if (items.isEmpty()) null else items
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
