package com.example.model

import android.util.Base64
import com.example.parser.ItemType
import com.example.parser.M3uItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class EpgProgram(
    val title: String,
    val description: String?,
    val startMs: Long,
    val endMs: Long
)

object EpgRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Xtream live regex:
    // http(s)://host[:port]/live/{username}/{password}/{streamId}.{ext}
    // or http(s)://host[:port]/{username}/{password}/{streamId}
    private val XTREAM_URL_PATTERN = Pattern.compile(
        "^(https?)://([^/]+)(?:/live)?/([^/]+)/([^/]+)/([^/.]+)(?:\\.[a-zA-Z0-9]+)?(?:\\?.*)?$",
        Pattern.CASE_INSENSITIVE
    )

    private data class CacheEntry(
        val now: EpgProgram?,
        val next: EpgProgram?,
        val expiryMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun getNowNext(item: M3uItem): Pair<EpgProgram?, EpgProgram?> = withContext(Dispatchers.IO) {
        if (item.type != ItemType.LIVE) return@withContext Pair(null, null)

        val matcher = XTREAM_URL_PATTERN.matcher(item.url.trim())
        if (!matcher.matches()) return@withContext Pair(null, null)

        val scheme = matcher.group(1) ?: return@withContext Pair(null, null)
        val hostWithPort = matcher.group(2) ?: return@withContext Pair(null, null)
        val username = matcher.group(3) ?: return@withContext Pair(null, null)
        val password = matcher.group(4) ?: return@withContext Pair(null, null)
        val streamId = matcher.group(5) ?: return@withContext Pair(null, null)

        val nowMs = System.currentTimeMillis()
        val cached = cache[streamId]
        if (cached != null && nowMs < cached.expiryMs) {
            return@withContext Pair(cached.now, cached.next)
        }

        try {
            val url = "$scheme://$hostWithPort/player_api.php?username=$username&password=$password&action=get_short_epg&stream_id=$streamId&limit=4"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Pair(null, null)
            }

            val body = response.body?.string() ?: return@withContext Pair(null, null)
            val jsonObj = JSONObject(body)
            val listingsArray = jsonObj.optJSONArray("epg_listings") ?: return@withContext Pair(null, null)

            val programs = mutableListOf<EpgProgram>()
            for (i in 0 until listingsArray.length()) {
                val progObj = listingsArray.optJSONObject(i) ?: continue

                val rawTitle = progObj.optString("title", "")
                val decodedTitle = decodeBase64OrRaw(rawTitle)
                if (decodedTitle.isNullOrBlank()) continue

                val rawDesc = progObj.optString("description", "").takeIf { it.isNotBlank() }
                val decodedDesc = decodeBase64OrRaw(rawDesc)

                var startMs = 0L
                var endMs = 0L

                val startTs = progObj.optLong("start_timestamp", 0L).takeIf { it > 0 }
                    ?: progObj.optString("start_timestamp").toLongOrNull() ?: 0L
                val stopTs = progObj.optLong("stop_timestamp", 0L).takeIf { it > 0 }
                    ?: progObj.optString("stop_timestamp").toLongOrNull() ?: 0L

                if (startTs > 0L) startMs = startTs * 1000L
                if (stopTs > 0L) endMs = stopTs * 1000L

                if (startMs == 0L) {
                    val startStr = progObj.optString("start", "")
                    startMs = parseDateToMs(startStr)
                }
                if (endMs == 0L) {
                    val endStr = progObj.optString("end", "")
                    endMs = parseDateToMs(endStr)
                }

                programs.add(
                    EpgProgram(
                        title = decodedTitle,
                        description = decodedDesc,
                        startMs = startMs,
                        endMs = endMs
                    )
                )
            }

            if (programs.isEmpty()) {
                val expiryMs = nowMs + 5 * 60 * 1000L
                cache[streamId] = CacheEntry(null, null, expiryMs)
                return@withContext Pair(null, null)
            }

            programs.sortBy { it.startMs }

            // Şimdi = başlangıç <= şu an < bitiş; Sonra = bir sonraki program
            val nowProgram = programs.find { it.startMs <= nowMs && nowMs < it.endMs }
            val nextProgram = if (nowProgram != null) {
                val nowIndex = programs.indexOf(nowProgram)
                programs.getOrNull(nowIndex + 1)
            } else {
                programs.firstOrNull { it.startMs > nowMs }
            }

            val expiryMs = if (nowProgram != null && nowProgram.endMs > nowMs) {
                minOf(nowMs + 5 * 60 * 1000L, nowProgram.endMs)
            } else {
                nowMs + 5 * 60 * 1000L
            }

            cache[streamId] = CacheEntry(nowProgram, nextProgram, expiryMs)
            Pair(nowProgram, nextProgram)
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    private fun decodeBase64OrRaw(str: String?): String? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()
        return try {
            val decodedBytes = Base64.decode(trimmed, Base64.DEFAULT)
            val decoded = String(decodedBytes, Charsets.UTF_8)
            val isPrintable = decoded.all { it >= ' ' || it == '\n' || it == '\r' || it == '\t' }
            if (decoded.isNotBlank() && isPrintable) {
                decoded
            } else {
                trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun parseDateToMs(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val formats = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val time = sdf.parse(dateStr)?.time
                if (time != null && time > 0L) return time
            } catch (e: Exception) {
                // Try next format
            }
        }
        return 0L
    }
}
