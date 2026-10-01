package com.example.ui.screens

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

import androidx.annotation.StringRes
import com.example.R

data class TmdbCastMember(
    val name: String,
    val character: String,
    val photoUrl: String?
)

data class TmdbFullDetail(
    val tmdbId: String,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val releaseDate: String,
    val runtimeMinutes: Int?,      // filmde dakika; dizide ortalama bölüm süresi
    val directors: List<String>,
    val cast: List<TmdbCastMember>,
    val backdropUrls: List<String> = emptyList()
)

private val tmdbDetailCache = mutableMapOf<String, TmdbFullDetail?>()
private var cachedFallbackBackdrops: List<String>? = null

suspend fun fetchTmdbFallbackBackdrops(defaultMovieId: String = "969681"): List<String> {
    cachedFallbackBackdrops?.takeIf { it.isNotEmpty() }?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = "15745c1c46c264d1170db38ea66049e0"
            val urls = LinkedHashSet<String>()
            val imagesUrl = "https://api.themoviedb.org/3/movie/$defaultMovieId/images?api_key=$apiKey"
            val imagesJson = httpGet(imagesUrl)
            if (imagesJson != null) {
                val backdropsArr = JSONObject(imagesJson).optJSONArray("backdrops")
                if (backdropsArr != null) {
                    for (i in 0 until backdropsArr.length()) {
                        val fp = backdropsArr.getJSONObject(i).optString("file_path", "")
                        if (fp.isNotBlank() && fp != "null") {
                            urls.add("https://image.tmdb.org/t/p/w1280$fp")
                        }
                    }
                }
            }
            if (urls.isEmpty()) {
                val trendingUrl = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=tr-TR"
                val trendingJson = httpGet(trendingUrl)
                if (trendingJson != null) {
                    val results = JSONObject(trendingJson).optJSONArray("results")
                    if (results != null) {
                        for (i in 0 until results.length()) {
                            val bp = results.getJSONObject(i).optString("backdrop_path", "")
                            if (bp.isNotBlank() && bp != "null") {
                                urls.add("https://image.tmdb.org/t/p/w1280$bp")
                            }
                        }
                    }
                }
            }
            val resultList = urls.toList()
            if (resultList.isNotEmpty()) {
                cachedFallbackBackdrops = resultList
            }
            resultList
        } catch (e: Throwable) {
            emptyList()
        }
    }
}

fun cleanMediaSearchQuery(rawTitle: String): String {
    return try {
        var cleaned = rawTitle.trim()
        cleaned = cleaned.replace(Regex("^\\[[^\\]]*\\]\\s*"), "")
        cleaned = cleaned.replace(Regex("^[A-Za-z0-9]{2,5}\\s*[|:\\-]\\s*"), "")
        cleaned = cleaned.replace(Regex("\\([^\\)]*\\)"), " ")
        cleaned = cleaned.replace(Regex("\\[[^\\]]*\\]"), " ")
        cleaned = cleaned.replace(
            Regex("(?i)\\b(s\\d{1,2}\\s*e\\d{1,3}|s\\d{1,2}|sezon\\s*\\d+|b[öo]l[üu]m\\s*\\d+|season\\s*\\d+|episode\\s*\\d+)\\b.*"),
            " "
        )
        cleaned = cleaned.replace(
            Regex("(?i)\\b(1080p|720p|480p|360p|2160p|4k|uhd|fhd|hd|sd|hevc|x264|x265|bluray|web-dl|webrip|hdr|dv|dual|tr\\s*dublaj|dublaj|altyaz[ıi]l[ıi]|multi)\\b"),
            " "
        )
        val withoutTrailingYear = cleaned.replace(Regex("^(.+?)\\s+[-–—]?\\s*(19\\d{2}|20\\d{2})\\s*$"), "$1").trim()
        if (withoutTrailingYear.isNotBlank()) {
            cleaned = withoutTrailingYear
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim(' ', '-', '|', ':', '.')
        cleaned.ifBlank { rawTitle.trim() }
    } catch (e: Throwable) {
        rawTitle.trim()
    }
}

suspend fun fetchTmdbFullDetail(
    query: String,
    isSeries: Boolean,
    tmdbId: String? = null
): TmdbFullDetail? {
    return withContext(Dispatchers.IO) {
        try {
            val cleanedQuery = cleanMediaSearchQuery(query)
            val cacheKey = "${if (isSeries) "tv" else "movie"}:${tmdbId?.takeIf { it.isNotBlank() } ?: cleanedQuery.lowercase(Locale.ROOT)}"
            synchronized(tmdbDetailCache) {
                if (tmdbDetailCache.containsKey(cacheKey)) {
                    return@withContext tmdbDetailCache[cacheKey]
                }
            }

            val apiKey = "15745c1c46c264d1170db38ea66049e0"
            var actualMediaType = if (isSeries) "tv" else "movie"

            var resolvedId: Int? = tmdbId?.takeIf { it.isNotBlank() }?.toIntOrNull()
            var searchOverviewFallback = ""

            if (resolvedId == null) {
                // 1) Başlığa göre ara
                val searchUrl = "https://api.themoviedb.org/3/search/$actualMediaType?api_key=$apiKey" +
                    "&query=${URLEncoder.encode(cleanedQuery, "UTF-8")}&language=tr-TR"
                val searchJson = httpGet(searchUrl)
                var results = searchJson?.let { JSONObject(it).optJSONArray("results") }
                if (results == null || results.length() == 0) {
                    val altMediaType = if (isSeries) "movie" else "tv"
                    val altSearchUrl = "https://api.themoviedb.org/3/search/$altMediaType?api_key=$apiKey" +
                        "&query=${URLEncoder.encode(cleanedQuery, "UTF-8")}&language=tr-TR"
                    val altSearchJson = httpGet(altSearchUrl)
                    val altResults = altSearchJson?.let { JSONObject(it).optJSONArray("results") }
                    if (altResults != null && altResults.length() > 0) {
                        results = altResults
                        actualMediaType = altMediaType
                    }
                }
                if (results == null || results.length() == 0) {
                    synchronized(tmdbDetailCache) { tmdbDetailCache[cacheKey] = null }
                    return@withContext null
                }
                val first = results.getJSONObject(0)
                resolvedId = first.optInt("id")
                searchOverviewFallback = first.optString("overview", "")
            }

            if (resolvedId <= 0) {
                synchronized(tmdbDetailCache) { tmdbDetailCache[cacheKey] = null }
                return@withContext null
            }

            // 2) Detay + oyuncu kadrosu tek istekte (append_to_response=credits)
            val detailUrl = "https://api.themoviedb.org/3/$actualMediaType/$resolvedId?api_key=$apiKey" +
                "&language=tr-TR&append_to_response=credits"
            val detailJson = httpGet(detailUrl) ?: return@withContext null
            val root = JSONObject(detailJson)

            val credits = root.optJSONObject("credits")
            val directors = mutableListOf<String>()
            val castList = mutableListOf<TmdbCastMember>()

            val isActualSeries = actualMediaType == "tv"
            if (isActualSeries) {
                // Dizide yönetmen yerine genelde "created_by" kullanılır
                val createdBy = root.optJSONArray("created_by")
                if (createdBy != null) {
                    for (i in 0 until createdBy.length()) {
                        val name = createdBy.getJSONObject(i).optString("name")
                        if (name.isNotBlank()) directors.add(name)
                    }
                }
                if (directors.isEmpty()) {
                    val crew = credits?.optJSONArray("crew")
                    if (crew != null) {
                        for (i in 0 until crew.length()) {
                            val member = crew.getJSONObject(i)
                            if (member.optString("job") == "Director" || member.optString("job") == "Executive Producer") {
                                val name = member.optString("name")
                                if (name.isNotBlank() && !directors.contains(name)) directors.add(name)
                                if (directors.size >= 3) break
                            }
                        }
                    }
                }
            } else {
                val crew = credits?.optJSONArray("crew")
                if (crew != null) {
                    for (i in 0 until crew.length()) {
                        val member = crew.getJSONObject(i)
                        if (member.optString("job") == "Director") {
                            val name = member.optString("name")
                            if (name.isNotBlank() && !directors.contains(name)) directors.add(name)
                        }
                    }
                }
            }

            val castArray = credits?.optJSONArray("cast")
            if (castArray != null) {
                for (i in 0 until minOf(castArray.length(), 12)) {
                    val member = castArray.getJSONObject(i)
                    val profilePath = member.optString("profile_path", "")
                    castList.add(
                        TmdbCastMember(
                            name = member.optString("name"),
                            character = member.optString("character"),
                            photoUrl = if (profilePath.isNotBlank() && profilePath != "null")
                                "https://image.tmdb.org/t/p/w200$profilePath" else null
                        )
                    )
                }
            }

            val runtime = if (isActualSeries) {
                root.optJSONArray("episode_run_time")?.let {
                    if (it.length() > 0) it.optInt(0).takeIf { r -> r > 0 } else null
                } ?: root.optJSONObject("last_episode_to_air")?.optInt("runtime", -1)?.takeIf { it > 0 }
            } else {
                root.optInt("runtime", -1).let { if (it > 0) it else null }
            }

            val posterPath = root.optString("poster_path", "")
            val backdropPath = root.optString("backdrop_path", "")

            // 3) Tüm arka plan (backdrops) kapak resimlerini çek (/images uç noktası dil filtresi olmadan tüm resimleri döndürür)
            val backdropSet = LinkedHashSet<String>()
            val imagesUrl = "https://api.themoviedb.org/3/$actualMediaType/$resolvedId/images?api_key=$apiKey"
            val imagesJson = httpGet(imagesUrl)
            if (imagesJson != null) {
                val backdropsArr = JSONObject(imagesJson).optJSONArray("backdrops")
                if (backdropsArr != null) {
                    for (i in 0 until backdropsArr.length()) {
                        val fp = backdropsArr.getJSONObject(i).optString("file_path", "")
                        if (fp.isNotBlank() && fp != "null") {
                            backdropSet.add("https://image.tmdb.org/t/p/w1280$fp")
                        }
                    }
                }
            }
            if (backdropPath.isNotBlank() && backdropPath != "null") {
                backdropSet.add("https://image.tmdb.org/t/p/w1280$backdropPath")
            }

            val primaryBackdrop = if (backdropPath.isNotBlank() && backdropPath != "null") {
                "https://image.tmdb.org/t/p/w1280$backdropPath"
            } else {
                backdropSet.firstOrNull()
            }

            val detail = TmdbFullDetail(
                tmdbId = resolvedId.toString(),
                title = root.optString("title").ifBlank { root.optString("name", cleanedQuery) },
                overview = root.optString("overview").ifBlank { searchOverviewFallback },
                posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "https://image.tmdb.org/t/p/w500$posterPath" else null,
                backdropUrl = primaryBackdrop,
                rating = root.optDouble("vote_average", 0.0),
                releaseDate = root.optString("release_date").ifBlank { root.optString("first_air_date", "") },
                runtimeMinutes = runtime,
                directors = directors,
                cast = castList,
                backdropUrls = backdropSet.toList()
            )
            synchronized(tmdbDetailCache) { tmdbDetailCache[cacheKey] = detail }
            detail
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}

private fun httpGet(urlStr: String): String? {
    return try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }
}

enum class ImdbTimeFrame(val label: String, @StringRes val labelRes: Int = R.string.timeframe_all) {
    ALL("Tümü", R.string.timeframe_all),
    WEEKLY("Haftalık", R.string.timeframe_weekly),
    THIRTY_DAYS("30 Günlük", R.string.timeframe_30_days),
    SIXTY_DAYS("60 Günlük", R.string.timeframe_60_days)
}

data class ImdbUpcomingItem(
    val id: String,
    val title: String,
    val type: String,
    val imdbRating: String,
    val posterUrl: String,
    val releaseDate: String,
    val timeFrame: ImdbTimeFrame,
    val genres: List<String>,
    val overview: String,
    val tmdbId: String = "",
    val trailerKey: String? = null
)

data class MatchFixture(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeLogo: String,
    val awayLogo: String,
    val league: String,
    val dateText: String,
    val isLive: Boolean,
    val channelKeywords: List<String>,
    val homeScore: String = "",
    val awayScore: String = "",
    val broadcastChannel: String = "beIN Sports 1"
)

fun getDefaultFixtures(): List<MatchFixture> {
    return listOf(
        MatchFixture(
            id = "sl-1",
            homeTeam = "Galatasaray",
            awayTeam = "Fenerbahçe",
            homeLogo = "https://media.api-sports.io/football/teams/600.png",
            awayLogo = "https://media.api-sports.io/football/teams/611.png",
            league = "Trendyol Süper Lig",
            dateText = "Bugün 20:00",
            isLive = false,
            channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor"),
            broadcastChannel = "beIN Sports 1 HD"
        ),
        MatchFixture(
            id = "sl-2",
            homeTeam = "Beşiktaş",
            awayTeam = "Trabzonspor",
            homeLogo = "https://media.api-sports.io/football/teams/603.png",
            awayLogo = "https://media.api-sports.io/football/teams/1010.png",
            league = "Trendyol Süper Lig",
            dateText = "Yarın 19:00",
            isLive = false,
            channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor"),
            broadcastChannel = "beIN Sports 1 HD"
        ),
        MatchFixture(
            id = "sl-3",
            homeTeam = "Başakşehir",
            awayTeam = "Eyüpspor",
            homeLogo = "https://media.api-sports.io/football/teams/607.png",
            awayLogo = "https://media.api-sports.io/football/teams/3576.png",
            league = "Trendyol Süper Lig",
            dateText = "Pazar 16:00",
            isLive = false,
            channelKeywords = listOf("BeIN Sports 2", "BeIN 2", "bein", "Spor"),
            broadcastChannel = "beIN Sports 2 HD"
        ),
        MatchFixture(
            id = "sl-4",
            homeTeam = "Samsunspor",
            awayTeam = "Göztepe",
            homeLogo = "https://media.api-sports.io/football/teams/1004.png",
            awayLogo = "https://media.api-sports.io/football/teams/605.png",
            league = "Trendyol Süper Lig",
            dateText = "Pazar 19:00",
            isLive = false,
            channelKeywords = listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor"),
            broadcastChannel = "beIN Sports 1 HD"
        ),
        MatchFixture(
            id = "ucl-1",
            homeTeam = "Real Madrid",
            awayTeam = "Manchester City",
            homeLogo = "https://media.api-sports.io/football/teams/541.png",
            awayLogo = "https://media.api-sports.io/football/teams/50.png",
            league = "UEFA Şampiyonlar Ligi",
            dateText = "Salı 22:00",
            isLive = false,
            channelKeywords = listOf("TRT 1", "TRT Spor", "Exxen", "Tivibu", "Spor"),
            broadcastChannel = "TRT 1 / Exxen"
        ),
        MatchFixture(
            id = "ucl-2",
            homeTeam = "Bayern München",
            awayTeam = "Barcelona",
            homeLogo = "https://media.api-sports.io/football/teams/157.png",
            awayLogo = "https://media.api-sports.io/football/teams/529.png",
            league = "UEFA Şampiyonlar Ligi",
            dateText = "Çarşamba 22:00",
            isLive = false,
            channelKeywords = listOf("TRT 1", "TRT Spor", "Exxen", "Spor"),
            broadcastChannel = "TRT 1 / Exxen"
        ),
        MatchFixture(
            id = "uel-1",
            homeTeam = "Fenerbahçe",
            awayTeam = "Athletic Bilbao",
            homeLogo = "https://media.api-sports.io/football/teams/611.png",
            awayLogo = "https://media.api-sports.io/football/teams/531.png",
            league = "UEFA Avrupa Ligi",
            dateText = "Perşembe 20:45",
            isLive = false,
            channelKeywords = listOf("TRT 1", "TRT Spor", "Exxen", "Spor"),
            broadcastChannel = "TRT 1 / TRT Spor"
        ),
        MatchFixture(
            id = "uel-2",
            homeTeam = "Galatasaray",
            awayTeam = "Tottenham",
            homeLogo = "https://media.api-sports.io/football/teams/600.png",
            awayLogo = "https://media.api-sports.io/football/teams/47.png",
            league = "UEFA Avrupa Ligi",
            dateText = "Perşembe 20:45",
            isLive = false,
            channelKeywords = listOf("TRT 1", "TRT Spor", "Exxen", "Spor"),
            broadcastChannel = "TRT 1 / TRT Spor"
        )
    )
}

fun getCombinedFixtures(espnFixtures: List<MatchFixture>): List<MatchFixture> {
    val defaults = getDefaultFixtures()
    if (espnFixtures.isEmpty()) return defaults
    val result = espnFixtures.toMutableList()
    for (d in defaults) {
        val alreadyPresent = result.any {
            it.homeTeam.contains(d.homeTeam, ignoreCase = true) || it.awayTeam.contains(d.awayTeam, ignoreCase = true)
        }
        if (!alreadyPresent) {
            result.add(d)
        }
    }
    return result
}

fun fetchTrailerKey(tmdbId: String): String? {
    try {
        val apiKey = "15745c1c46c264d1170db38ea66049e0"
        val url = "https://api.themoviedb.org/3/movie/$tmdbId/videos?api_key=$apiKey"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        if (conn.responseCode == 200) {
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val video = results.optJSONObject(i)
                    if (video != null && video.optString("site").equals("YouTube", ignoreCase = true) && video.optString("type").equals("Trailer", ignoreCase = true)) {
                        return video.optString("key")
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun parseTmdbList(jsonString: String, timeFrame: ImdbTimeFrame, context: android.content.Context? = null): List<ImdbUpcomingItem> {
    val list = mutableListOf<ImdbUpcomingItem>()
    try {
        val root = JSONObject(jsonString)
        val results = root.optJSONArray("results") ?: return emptyList()
        val genreResMap = mapOf(
            28 to R.string.genre_action, 12 to R.string.genre_adventure, 16 to R.string.genre_animation,
            35 to R.string.genre_comedy, 80 to R.string.genre_crime, 99 to R.string.genre_documentary,
            18 to R.string.genre_drama, 10751 to R.string.genre_family, 14 to R.string.genre_fantasy,
            36 to R.string.genre_history, 27 to R.string.genre_horror, 10402 to R.string.genre_music,
            9648 to R.string.genre_mystery, 10749 to R.string.genre_romance, 878 to R.string.genre_scifi,
            10770 to R.string.genre_tv_movie, 53 to R.string.genre_thriller, 10752 to R.string.genre_war,
            37 to R.string.genre_western, 10759 to R.string.genre_action_adventure, 10765 to R.string.genre_scifi_fantasy
        )
        val fallbackGenreMap = mapOf(
            28 to "Aksiyon", 12 to "Macera", 16 to "Animasyon", 35 to "Komedi", 80 to "Suç",
            99 to "Belgesel", 18 to "Dram", 10751 to "Aile", 14 to "Fantezi", 36 to "Tarih",
            27 to "Korku", 10402 to "Müzik", 9648 to "Gizem", 10749 to "Romantik", 878 to "Bilim Kurgu",
            10770 to "TV Film", 53 to "Gerilim", 10752 to "Savaş", 37 to "Vahşi Batı",
            10759 to "Aksiyon & Macera", 10765 to "Bilim Kurgu & Fantezi"
        )
        for (i in 0 until minOf(results.length(), 15)) {
            val item = results.optJSONObject(i) ?: continue
            val posterPath = item.optString("poster_path", "")
            if (posterPath.isEmpty() || posterPath == "null") continue

            val id = item.optLong("id").toString()
            val title = if (item.has("title") && item.optString("title").isNotBlank()) item.optString("title") else item.optString("name", "Bilinmeyen")
            val mediaType = item.optString("media_type", "")
            val isTv = mediaType == "tv" || (!item.has("title") && item.has("name"))
            val typeStr = if (context != null) {
                val loc = com.example.model.AppLanguageManager.localizedContext(context)
                if (isTv) loc.getString(R.string.tab_series) else loc.getString(R.string.tab_movies)
            } else {
                if (isTv) "Dizi" else "Film"
            }
            val voteAvg = item.optDouble("vote_average", 0.0)
            val ratingStr = if (voteAvg > 0) String.format(Locale.US, "%.1f", voteAvg) else "7.5"
            val releaseDate = if (item.has("release_date") && item.optString("release_date").isNotBlank()) {
                item.optString("release_date")
            } else {
                item.optString("first_air_date", "Vizyonda")
            }
            val overview = item.optString("overview", "Açıklama bulunmuyor.")

            val genreIds = item.optJSONArray("genre_ids")
            val genresList = mutableListOf<String>()
            if (genreIds != null) {
                for (g in 0 until genreIds.length()) {
                    val gId = genreIds.optInt(g)
                    val gName = if (context != null) {
                        val resId = genreResMap[gId]
                        if (resId != null) {
                            com.example.model.AppLanguageManager.localizedContext(context).getString(resId)
                        } else fallbackGenreMap[gId]
                    } else fallbackGenreMap[gId]
                    if (gName != null) genresList.add(gName)
                }
            }
            if (genresList.isEmpty()) genresList.add(typeStr)

            val trailerKey = if (i < 3) fetchTrailerKey(id) else null
            list.add(
                ImdbUpcomingItem(
                    id = "tmdb_${timeFrame.name}_$id",
                    title = title,
                    type = typeStr,
                    imdbRating = ratingStr,
                    posterUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                    releaseDate = releaseDate,
                    timeFrame = timeFrame,
                    genres = genresList,
                    overview = if (overview.isNotBlank()) overview else "$title TMDB detayları.",
                    tmdbId = id,
                    trailerKey = trailerKey
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun parseEspnFixtures(jsonString: String, leagueName: String = "Trendyol Süper Lig"): List<MatchFixture> {
    val list = mutableListOf<MatchFixture>()
    try {
        val root = JSONObject(jsonString)
        val events = root.optJSONArray("events") ?: return emptyList()

        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val competitions = event.optJSONArray("competitions") ?: continue
            if (competitions.length() == 0) continue
            val comp = competitions.optJSONObject(0) ?: continue
            val competitors = comp.optJSONArray("competitors") ?: continue
            if (competitors.length() < 2) continue

            var homeTeam = ""
            var awayTeam = ""
            var homeLogo = ""
            var awayLogo = ""
            var homeScore = ""
            var awayScore = ""

            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                val isHome = c.optString("homeAway") == "home"
                val team = c.optJSONObject("team") ?: continue
                val name = team.optString("displayName", team.optString("name"))
                val logo = team.optString("logo")
                val score = c.optString("score", "")

                if (isHome) {
                    homeTeam = name
                    homeLogo = logo
                    homeScore = score
                } else {
                    awayTeam = name
                    awayLogo = logo
                    awayScore = score
                }
            }

            val dateStr = event.optString("date")
            var dateText = dateStr
            try {
                val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
                sdfIn.timeZone = TimeZone.getTimeZone("UTC")
                val dateObj = sdfIn.parse(dateStr)
                if (dateObj != null) {
                    val sdfOut = SimpleDateFormat("dd MMM HH:mm", Locale("tr"))
                    dateText = sdfOut.format(dateObj)
                }
            } catch (e: Exception) {}

            val status = event.optJSONObject("status")
            val type = status?.optJSONObject("type")
            val state = type?.optString("state")
            val isLive = state == "in"
            val isCompleted = state == "post"

            var displayDate = dateText
            if (isLive) {
                val detail = type?.optString("detail")
                displayDate = if (!detail.isNullOrEmpty()) "CANLI - $detail" else "CANLI"
            } else if (isCompleted) {
                displayDate = "MS (Bitti)"
            }

            val channelKeywords = when {
                leagueName.contains("Süper Lig", ignoreCase = true) -> listOf("BeIN Sports 1", "BeIN 1", "bein", "Spor")
                leagueName.contains("Şampiyonlar", ignoreCase = true) -> listOf("TRT 1", "TRT Spor", "Exxen", "Spor")
                leagueName.contains("Avrupa", ignoreCase = true) -> listOf("TRT 1", "TRT Spor", "Exxen", "Spor")
                else -> listOf("Spor", "Sports", "TRT", "S Sport", "BeIN")
            }

            val broadcastChannel = when {
                leagueName.contains("Süper Lig", ignoreCase = true) -> "beIN Sports 1 HD"
                leagueName.contains("Şampiyonlar", ignoreCase = true) -> "TRT 1 / Exxen"
                leagueName.contains("Avrupa", ignoreCase = true) -> "TRT Spor / Exxen"
                else -> "Spor Kanalı"
            }

            list.add(
                MatchFixture(
                    id = event.optString("id", UUID.randomUUID().toString()),
                    homeTeam = homeTeam,
                    awayTeam = awayTeam,
                    homeLogo = homeLogo,
                    awayLogo = awayLogo,
                    league = leagueName,
                    dateText = displayDate,
                    isLive = isLive,
                    channelKeywords = channelKeywords,
                    homeScore = homeScore,
                    awayScore = awayScore,
                    broadcastChannel = broadcastChannel
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
