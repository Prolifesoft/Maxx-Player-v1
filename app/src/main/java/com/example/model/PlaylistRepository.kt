package com.example.model

import com.example.model.db.AppDatabase
import com.example.parser.M3uItem
import com.example.parser.M3uParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PlaylistLoadStep {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    ERROR
}

object PlaylistRepository {
    private const val PREFS_NAME = "playlist_cache_prefs"
    private const val KEY_LAST_PLAYLIST_URL = "last_playlist_url"

    private val _playlist = MutableStateFlow<List<M3uItem>>(emptyList())
    val playlist: StateFlow<List<M3uItem>> = _playlist
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _loadStep = MutableStateFlow(PlaylistLoadStep.IDLE)
    val loadStep: StateFlow<PlaylistLoadStep> = _loadStep

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress

    private val _loadingDetail = MutableStateFlow("")
    val loadingDetail: StateFlow<String> = _loadingDetail

    private var currentLoadedUrl: String? = null

    fun buildPlaylistUrl(hostUrl: String, username: String, password: String): String {
        val cleanHost = hostUrl.trim()
        return if (username.isEmpty() && password.isEmpty()) {
            cleanHost
        } else {
            "$cleanHost/get.php?username=$username&password=$password&type=m3u_plus&output=mpegts"
        }
    }

    private fun getCacheFile(context: Context, url: String): File {
        val urlHash = MessageDigest.getInstance("MD5").digest(url.trim().toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "playlist_cache_$urlHash.m3u")
    }

    fun hasCacheForUrl(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val file = getCacheFile(context, url)
        return file.exists() && file.length() > 0L
    }

    fun saveLastUsedPlaylistUrl(context: Context, url: String) {
        if (url.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PLAYLIST_URL, url.trim())
            .apply()
    }

    fun getLastUsedPlaylistUrl(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PLAYLIST_URL, null)
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun resolveLastOrFirstPlaylistUrl(context: Context, userId: String? = null): String? {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val uid = userId?.takeIf { it.isNotBlank() } ?: db.iptvDao().getFirstUser()?.id.orEmpty()
            val dbPlaylists = if (uid.isNotBlank()) {
                db.iptvDao().getPlaylistsForUserSync(uid).ifEmpty { db.iptvDao().getAllPlaylistsSync() }
            } else {
                db.iptvDao().getAllPlaylistsSync()
            }
            val dbUrls = dbPlaylists.map { buildPlaylistUrl(it.hostUrl, it.username, it.password) }.filter { it.isNotBlank() }
            val savedLast = getLastUsedPlaylistUrl(context)

            when {
                savedLast != null && (dbUrls.contains(savedLast) || hasCacheForUrl(context, savedLast)) -> savedLast
                dbUrls.isNotEmpty() -> {
                    val cachedDbUrl = dbUrls.firstOrNull { hasCacheForUrl(context, it) }
                    cachedDbUrl ?: dbUrls.first()
                }
                else -> null
            }
        }
    }
    
    suspend fun loadPlaylist(context: Context, url: String, forceRefresh: Boolean = false) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        saveLastUsedPlaylistUrl(context, cleanUrl)

        // If already loaded in memory for the same URL and not forcing refresh, return immediately
        if (!forceRefresh && currentLoadedUrl == cleanUrl && _playlist.value.isNotEmpty()) {
            _error.value = null
            _loadStep.value = PlaylistLoadStep.COMPLETED
            _isLoading.value = false
            return
        }

        if (_isLoading.value) return
        _error.value = null

        val cacheFile = getCacheFile(context, cleanUrl)

        // Stage 2: If cache exists and not forcing refresh, load directly from cache without showing blocking loading dialog
        if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 0L) {
            try {
                val cachedBytes = withContext(Dispatchers.IO) { cacheFile.readBytes() }
                val cachedItems = M3uParser.parseFromBytes(cachedBytes) { _, _ -> }
                if (cachedItems.isNotEmpty()) {
                    _playlist.value = cachedItems
                    currentLoadedUrl = cleanUrl
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = "Önbellekten yüklendi"
                    _isLoading.value = false

                    // Silently refresh cache file in background without showing loading dialog
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val networkResult = M3uParser.downloadAndParse(cleanUrl, cachedBytes) { _, _, _ -> }
                            if (!networkResult.isUnchanged && !networkResult.items.isNullOrEmpty()) {
                                networkResult.bytes?.let { cacheFile.writeBytes(it) }
                                if (currentLoadedUrl == cleanUrl) {
                                    _playlist.value = networkResult.items
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    return
                }
            } catch (_: Throwable) {
                // If cache read/parse failed, fall through to normal network loading dialog
            }
        }

        // No cached data available (or forceRefresh = true): show loading dialog and download
        _isLoading.value = true
        _loadStep.value = PlaylistLoadStep.CONNECTING
        _loadingProgress.value = 5
        _loadingDetail.value = "Sunucuya bağlanılıyor..."

        try {
            val networkResult = M3uParser.downloadAndParse(cleanUrl, null) { step, progress, detail ->
                _loadStep.value = step
                _loadingProgress.value = progress
                _loadingDetail.value = detail
            }
            
            if (networkResult.items != null) {
                if (networkResult.items.isEmpty()) {
                    _error.value = "Listede yayın bulunamadı"
                    _loadStep.value = PlaylistLoadStep.ERROR
                    _loadingProgress.value = 0
                    delay(2000)
                } else {
                    withContext(Dispatchers.IO) {
                        networkResult.bytes?.let { cacheFile.writeBytes(it) }
                    }
                    _playlist.value = networkResult.items
                    currentLoadedUrl = cleanUrl
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = ""
                    delay(600)
                }
            } else {
                _error.value = "Listede yayın bulunamadı"
                _loadStep.value = PlaylistLoadStep.ERROR
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            if (_playlist.value.isEmpty()) {
                _error.value = e.message ?: "Bağlantı veya bellek hatası"
                _loadStep.value = PlaylistLoadStep.ERROR
                _loadingProgress.value = 0
                delay(2000)
            }
        } finally {
            _isLoading.value = false
            if (_loadStep.value != PlaylistLoadStep.COMPLETED) {
                _loadStep.value = PlaylistLoadStep.IDLE
            }
        }
    }
    
    fun getGroups(type: com.example.parser.ItemType? = null): List<String> {
        val hidden = CategoryManager.hiddenCategories.value
        val filtered = if (type == null) _playlist.value else _playlist.value.filter { it.type == type }
        return filtered.mapNotNull { it.group }
            .distinct()
            .filter { !hidden.contains(it) }
            .sorted()
    }
    
    fun getItemsForGroup(group: String?, type: com.example.parser.ItemType? = null): List<M3uItem> {
        val hidden = CategoryManager.hiddenCategories.value
        var items = _playlist.value
        if (type != null) {
            items = items.filter { it.type == type }
        }
        items = items.filter { !hidden.contains(it.group) }
        return if (group == null) {
            items
        } else {
            items.filter { it.group == group }
        }
    }
}
