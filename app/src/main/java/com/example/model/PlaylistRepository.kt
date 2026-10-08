package com.example.model

import com.example.model.db.AppDatabase
import com.example.parser.M3uItem
import com.example.parser.M3uParser
import kotlinx.coroutines.async
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private val _knownGroups = MutableStateFlow<Map<com.example.parser.ItemType, List<String>>>(emptyMap())
    val knownGroups: StateFlow<Map<com.example.parser.ItemType, List<String>>> = _knownGroups

    private val _categoryLoadingStates = MutableStateFlow<Set<Pair<com.example.parser.ItemType, String>>>(emptySet())
    val categoryLoadingStates: StateFlow<Set<Pair<com.example.parser.ItemType, String>>> = _categoryLoadingStates

    private val _typeLoadingStates = MutableStateFlow<Map<com.example.parser.ItemType, Boolean>>(emptyMap())
    val typeLoadingStates: StateFlow<Map<com.example.parser.ItemType, Boolean>> = _typeLoadingStates

    private val _isDialogDismissed = MutableStateFlow(false)
    val isDialogDismissed: StateFlow<Boolean> = _isDialogDismissed

    fun dismissLoadingDialog() {
        _isDialogDismissed.value = true
    }

    private var currentLoadedUrl: String? = null
    var currentXtreamCredentials: XtreamCredentials? = null
    private val loadingCategories = java.util.concurrent.ConcurrentHashMap<Pair<com.example.parser.ItemType, String>, Boolean>()
    private val loadMutex = Mutex()

    fun buildPlaylistUrl(hostUrl: String, username: String, password: String): String {
        val cleanHost = hostUrl.trim().trimEnd('/')
        if (cleanHost.contains("/get.php") || cleanHost.contains("output=") || cleanHost.endsWith(".m3u") || cleanHost.endsWith(".m3u8")) {
            return cleanHost
        }
        return if (username.isEmpty() && password.isEmpty()) {
            cleanHost
        } else {
            "$cleanHost/get.php?username=$username&password=$password&type=m3u_plus&output=mpegts"
        }
    }

    private fun getCacheFile(context: Context, url: String): File {
        val clean = url.trim().lowercase()
        val urlHash = MessageDigest.getInstance("MD5").digest(clean.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "playlist_cache_$urlHash.m3u")
    }

    fun hasCacheForUrl(context: Context, url: String): Boolean {
        if (url.isBlank()) {
            val anyCache = context.filesDir.listFiles { f -> f.name.startsWith("playlist_cache_") && f.name.endsWith(".m3u") && f.length() > 0L }
            return !anyCache.isNullOrEmpty()
        }
        val file = getCacheFile(context, url)
        if (file.exists() && file.length() > 0L) return true
        val anyCache = context.filesDir.listFiles { f -> f.name.startsWith("playlist_cache_") && f.name.endsWith(".m3u") && f.length() > 0L }
        return !anyCache.isNullOrEmpty()
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

        loadMutex.withLock {
            if (!forceRefresh && currentLoadedUrl == cleanUrl && _playlist.value.isNotEmpty()) {
                _error.value = null
                _loadStep.value = PlaylistLoadStep.COMPLETED
                _isLoading.value = false
                return@withLock
            }

            if (_isLoading.value && !forceRefresh) return@withLock
            _error.value = null

            // Xtream Codes API Mode (400,000 kanallı büyük listelerde 100 MB indirme yapmadan direkt API ile hızlı yükleme)
            val xtreamCreds = XtreamCodesApi.extractCredentials(cleanUrl, context)
            if (xtreamCreds != null) {
                currentXtreamCredentials = xtreamCreds
                _isDialogDismissed.value = false
                _isLoading.value = true
                _loadStep.value = PlaylistLoadStep.PROCESSING
                _loadingDetail.value = "Kategoriler ve ilk yayınlar hazırlanıyor..."
                _loadingProgress.value = 35

                try {
                    // İlk part: Canlı, Film ve Dizilerden ilk 10'ar kategori (topCount = 10)
                    val preview = XtreamCodesApi.initializeCategoriesAndPreview(context, xtreamCreds, topCount = 10)
                    if (preview.categoriesByType.isNotEmpty()) {
                        _knownGroups.value = preview.categoriesByType
                        _playlist.value = preview.previewItems
                        currentLoadedUrl = cleanUrl
                        // 100 MB indirme YOK! İlk part (Canlı, Film, Dizi ilk 10 kategori) çekildi ve anasayfa anında açıldı!
                        _isDialogDismissed.value = true
                        _loadStep.value = PlaylistLoadStep.COMPLETED
                        _loadingProgress.value = 100
                        _loadingDetail.value = "${preview.previewItems.size} yayın hazır"
                        _isLoading.value = false

                        // Arka planda kalan kategorileri peyderpey çekmeye devam et
                        startBackgroundCategorySync(context, xtreamCreds)
                        return@withLock
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            val cacheFile = getCacheFile(context, cleanUrl)
            val hasLocalCache = !forceRefresh && (cacheFile.exists() && cacheFile.length() > 0L)
            val targetCacheFile = if (hasLocalCache) {
                cacheFile
            } else if (!forceRefresh) {
                val anyCache = context.filesDir.listFiles { f -> f.name.startsWith("playlist_cache_") && f.name.endsWith(".m3u") && f.length() > 0L }
                anyCache?.maxByOrNull { it.lastModified() } ?: cacheFile
            } else {
                cacheFile
            }

            // Stage 1 (Cache Mode): If cache exists on disk, load preview and categories instantly (<100ms)
            if (!forceRefresh && targetCacheFile.exists() && targetCacheFile.length() > 0L) {
                try {
                    val preview = M3uParser.scanPreviewAndCategories(targetCacheFile)
                    if (preview.previewItems.isNotEmpty()) {
                        _knownGroups.value = preview.categoriesByType
                        _playlist.value = preview.previewItems
                        currentLoadedUrl = cleanUrl
                        _isDialogDismissed.value = true
                        _loadStep.value = PlaylistLoadStep.PROCESSING
                        _loadingProgress.value = 50
                        _loadingDetail.value = "${preview.previewItems.size} yayın hazır..."
                    }

                    // Background full parsing
                    val fullList = M3uParser.parseFromFileProgressive(
                        file = targetCacheFile,
                        onBatchParsed = { batch ->
                            if (_playlist.value.size < batch.size) {
                                _playlist.value = batch
                                _loadingDetail.value = "${batch.size} kanal aktif..."
                            }
                        },
                        onProgress = { pct, detail ->
                            _loadingProgress.value = pct
                            _loadingDetail.value = detail
                        }
                    )

                    if (fullList.isNotEmpty()) {
                        _playlist.value = fullList
                        currentLoadedUrl = cleanUrl
                        _loadStep.value = PlaylistLoadStep.COMPLETED
                        _loadingProgress.value = 100
                        _loadingDetail.value = "${fullList.size} kanal hazır"
                        _isLoading.value = false

                        if (targetCacheFile != cacheFile) {
                            try { targetCacheFile.copyTo(cacheFile, overwrite = true) } catch (_: Throwable) {}
                        }
                        return@withLock
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Stage 2 (Network Download Mode):
            // 1. Download file fast to cacheFile with real-time download progress
            // 2. The moment download finishes, immediately scan balanced preview (first 10 items for EVERY Live, Movie, Series category)
            // 3. Immediately dismiss loading dialog and open Anasayfa (Live, Movies, and Series are all populated and NOT empty!)
            // 4. Background parser completes all remaining items from disk without blocking!
            _isDialogDismissed.value = false
            _isLoading.value = true
            _loadStep.value = PlaylistLoadStep.CONNECTING
            _loadingProgress.value = 5
            _loadingDetail.value = "Sunucuya bağlanılıyor..."

            try {
                val downloaded = M3uParser.downloadToFile(
                    url = cleanUrl,
                    targetFile = cacheFile,
                    onProgress = { step, progress, detail ->
                        _loadStep.value = step
                        _loadingProgress.value = progress
                        _loadingDetail.value = detail
                    }
                )

                if (!downloaded || !cacheFile.exists() || cacheFile.length() == 0L) {
                    _error.value = "Çalma listesi indirilemedi"
                    _loadStep.value = PlaylistLoadStep.ERROR
                    _loadingProgress.value = 0
                    delay(1500)
                    return@withLock
                }

                // Initial fast stage: extract category names & initial representative batch (first 10 items for Live, Movies, Series per category)
                _loadStep.value = PlaylistLoadStep.PROCESSING
                _loadingDetail.value = "Kategoriler ve yayınlar hazırlanıyor..."
                val preview = M3uParser.scanPreviewAndCategories(cacheFile, maxPerCategory = 10)
                if (preview.previewItems.isNotEmpty()) {
                    _knownGroups.value = preview.categoriesByType
                    _playlist.value = preview.previewItems
                    currentLoadedUrl = cleanUrl
                    // ILK PART ÇEKİLDİ (Canlı, Film ve Diziler dolu): DİREK ANASAYFA GELİR, YÜKLENİYOR KISMI GİZLENİR!
                    _isDialogDismissed.value = true
                    _loadStep.value = PlaylistLoadStep.PROCESSING
                    _loadingProgress.value = 50
                    _loadingDetail.value = "${preview.previewItems.size} yayın hazır..."
                }

                // Background full loading continues without blocking the user
                val fullList = M3uParser.parseFromFileProgressive(
                    file = cacheFile,
                    onBatchParsed = { batch ->
                        if (_playlist.value.size < batch.size) {
                            _playlist.value = batch
                            _loadingDetail.value = "${batch.size} kanal aktif..."
                        }
                    },
                    onProgress = { pct, detail ->
                        _loadingProgress.value = pct
                        _loadingDetail.value = detail
                    }
                )

                if (fullList.isNotEmpty()) {
                    _playlist.value = fullList
                    currentLoadedUrl = cleanUrl
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = "${fullList.size} kanal yüklendi"
                } else if (_playlist.value.isEmpty()) {
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
    }
    
    fun getGroups(type: com.example.parser.ItemType? = null): List<String> {
        val hidden = CategoryManager.hiddenCategories.value
        val known = if (type != null) {
            _knownGroups.value[type].orEmpty()
        } else {
            _knownGroups.value.values.flatten().distinct()
        }
        val fromPlaylist = if (type == null) _playlist.value else _playlist.value.filter { it.type == type }
        val plGroups = fromPlaylist.mapNotNull { it.group }
        val combined = (known + plGroups).distinct()
        return combined.filter { !hidden.contains(it) }.sorted()
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

    private var backgroundSyncJob: kotlinx.coroutines.Job? = null

    fun startBackgroundCategorySync(context: Context, creds: XtreamCredentials) {
        backgroundSyncJob?.cancel()
        backgroundSyncJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Priority 1: LIVE categories (canlı kanallar)
                fetchRemainingCategories(context, creds, com.example.parser.ItemType.LIVE)
                // Priority 2: MOVIE categories (filmler)
                fetchRemainingCategories(context, creds, com.example.parser.ItemType.MOVIE)
                // Priority 3: SERIES categories (diziler)
                fetchRemainingCategories(context, creds, com.example.parser.ItemType.SERIES)
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchRemainingCategories(context: Context, creds: XtreamCredentials, type: com.example.parser.ItemType) {
        val cats = XtreamCodesApi.getCategories(creds, type)
        val missing = cats.filter { cat ->
            !_playlist.value.any { it.type == type && it.group == cat.categoryName }
        }
        for (chunk in missing.chunked(3)) {
            val streams = kotlinx.coroutines.coroutineScope {
                val jobs = chunk.map { cat ->
                    async(Dispatchers.IO) {
                        XtreamCodesApi.getStreamsForCategory(creds, cat.categoryId, cat.categoryName, type, context)
                    }
                }
                jobs.flatMap { it.await() }
            }
            if (streams.isNotEmpty()) {
                val current = _playlist.value.toMutableList()
                val existingUrls = current.map { it.url }.toSet()
                val newItems = streams.filter { !existingUrls.contains(it.url) }
                if (newItems.isNotEmpty()) {
                    current.addAll(newItems)
                    _playlist.value = current
                }
            }
            delay(150)
        }
    }

    suspend fun ensureTypeLoaded(context: Context, type: com.example.parser.ItemType) {
        val xtreamCreds = currentXtreamCredentials ?: XtreamCodesApi.extractCredentials(currentLoadedUrl ?: "", context) ?: return
        if (_typeLoadingStates.value[type] == true) return

        _typeLoadingStates.value = _typeLoadingStates.value + (type to true)
        withContext(Dispatchers.IO) {
            try {
                val cats = XtreamCodesApi.getCategories(xtreamCreds, type)
                val missing = cats.filter { cat ->
                    !_playlist.value.any { it.type == type && it.group == cat.categoryName }
                }
                for (chunk in missing.chunked(4)) {
                    val streams = kotlinx.coroutines.coroutineScope {
                        val jobs = chunk.map { cat ->
                            async(Dispatchers.IO) {
                                XtreamCodesApi.getStreamsForCategory(xtreamCreds, cat.categoryId, cat.categoryName, type, context)
                            }
                        }
                        jobs.flatMap { it.await() }
                    }
                    if (streams.isNotEmpty()) {
                        val current = _playlist.value.toMutableList()
                        val existingUrls = current.map { it.url }.toSet()
                        val newItems = streams.filter { !existingUrls.contains(it.url) }
                        if (newItems.isNotEmpty()) {
                            current.addAll(newItems)
                            _playlist.value = current
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _typeLoadingStates.value = _typeLoadingStates.value + (type to false)
            }
        }
    }

    suspend fun ensureCategoryLoaded(context: Context, groupName: String?, type: com.example.parser.ItemType) {
        if (groupName == null) return
        val xtreamCreds = currentXtreamCredentials ?: XtreamCodesApi.extractCredentials(currentLoadedUrl ?: "", context) ?: return
        val cacheKey = type to groupName
        if (loadingCategories[cacheKey] == true) return

        val alreadyLoaded = _playlist.value.any { it.type == type && it.group == groupName }
        if (alreadyLoaded) return

        loadingCategories[cacheKey] = true
        _categoryLoadingStates.value = _categoryLoadingStates.value + cacheKey
        withContext(Dispatchers.IO) {
            try {
                var catId = XtreamCodesApi.categoryNameToIdMap[cacheKey]
                if (catId == null) {
                    val cats = XtreamCodesApi.getCategories(xtreamCreds, type)
                    catId = cats.find { it.categoryName == groupName }?.categoryId
                }
                if (catId != null) {
                    val streams = XtreamCodesApi.getStreamsForCategory(xtreamCreds, catId, groupName, type, context)
                    if (streams.isNotEmpty()) {
                        val current = _playlist.value.toMutableList()
                        val existingUrls = current.map { it.url }.toSet()
                        val newItems = streams.filter { !existingUrls.contains(it.url) }
                        if (newItems.isNotEmpty()) {
                            current.addAll(newItems)
                            _playlist.value = current
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingCategories.remove(cacheKey)
                _categoryLoadingStates.value = _categoryLoadingStates.value - cacheKey
            }
        }
    }

    suspend fun getSeriesEpisodes(context: Context, seriesItem: M3uItem): List<M3uItem> {
        val xtreamCreds = currentXtreamCredentials ?: XtreamCodesApi.extractCredentials(currentLoadedUrl ?: "", context)
        val seriesId = seriesItem.tvgId
        if (xtreamCreds != null && !seriesId.isNullOrBlank()) {
            val episodes = XtreamCodesApi.getSeriesEpisodes(
                xtreamCreds,
                seriesId,
                seriesItem.seriesName ?: seriesItem.title,
                seriesItem.group ?: "DİZİLER"
            )
            if (episodes.isNotEmpty()) {
                return episodes
            }
        }
        val sName = seriesItem.seriesName ?: seriesItem.title
        val localEpisodes = _playlist.value.filter { it.type == com.example.parser.ItemType.SERIES && (it.seriesName == sName || it.title.startsWith(sName)) }
        return localEpisodes.ifEmpty { listOf(seriesItem) }
    }
}
