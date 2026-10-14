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
import org.json.JSONArray
import org.json.JSONObject

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

    private val _currentPlaylistUrl = MutableStateFlow<String?>(null)
    val currentPlaylistUrl: StateFlow<String?> = _currentPlaylistUrl

    fun dismissLoadingDialog() {
        _isDialogDismissed.value = true
    }

    private var currentLoadedUrl: String? = null
    var currentXtreamCredentials: XtreamCredentials? = null
    private val loadingCategories = java.util.concurrent.ConcurrentHashMap<Pair<com.example.parser.ItemType, String>, Boolean>()
    private val loadMutex = Mutex()

    fun prepareForNewPlaylist(context: Context, newUrl: String) {
        val cleanUrl = newUrl.trim()
        saveLastUsedPlaylistUrl(context, cleanUrl)
        currentLoadedUrl = null
        _currentPlaylistUrl.value = cleanUrl
        currentXtreamCredentials = null
        _playlist.value = emptyList()
        _knownGroups.value = emptyMap()
        _categoryLoadingStates.value = emptySet()
        _typeLoadingStates.value = emptyMap()
        _error.value = null
        _loadStep.value = PlaylistLoadStep.CONNECTING
        _loadingProgress.value = 15
        _loadingDetail.value = "Yeni çalma listesi anlık çekiliyor..."
        _isDialogDismissed.value = false
        _isLoading.value = true
        PlayerRepository.clearForNewPlaylist()
        XtreamCodesApi.clearAllCaches(context)
        cleanOldPlaylistCaches(context, keepUrl = cleanUrl)
    }

    fun cleanOldPlaylistCaches(context: Context, keepUrl: String? = null) {
        try {
            val keepHash = keepUrl?.takeIf { it.isNotBlank() }?.let { url ->
                val clean = url.trim().lowercase()
                MessageDigest.getInstance("MD5").digest(clean.toByteArray()).joinToString("") { "%02x".format(it) }
            }
            val files = context.filesDir.listFiles()
            if (files != null) {
                for (f in files) {
                    val name = f.name
                    if (name.startsWith("playlist_cache_") || name.startsWith("groups_cache_") || name.endsWith(".m3u.tmp") || name.startsWith("xtream_")) {
                        if (keepHash == null || !name.contains(keepHash)) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }
            val cacheFiles = context.cacheDir.listFiles()
            if (cacheFiles != null) {
                for (f in cacheFiles) {
                    val name = f.name
                    if (name.startsWith("xtream_") || name.endsWith(".download")) {
                        if (keepHash == null || !name.contains(keepHash)) {
                            try { f.delete() } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

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

    private fun getGroupsCacheFile(context: Context, url: String): File {
        val clean = url.trim().lowercase()
        val urlHash = MessageDigest.getInstance("MD5").digest(clean.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "groups_cache_$urlHash.json")
    }

    fun saveKnownGroupsCache(context: Context, url: String, groups: Map<com.example.parser.ItemType, List<String>>) {
        if (groups.isEmpty() || url.isBlank()) return
        try {
            val file = getGroupsCacheFile(context, url)
            val root = JSONObject()
            for ((type, list) in groups) {
                val arr = JSONArray()
                list.forEach { arr.put(it) }
                root.put(type.name, arr)
            }
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun loadKnownGroupsCache(context: Context, url: String): Map<com.example.parser.ItemType, List<String>> {
        if (url.isBlank()) return emptyMap()
        try {
            val file = getGroupsCacheFile(context, url)
            if (file.exists() && file.length() > 0L) {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                val map = mutableMapOf<com.example.parser.ItemType, List<String>>()
                for (type in com.example.parser.ItemType.values()) {
                    val arr = root.optJSONArray(type.name)
                    if (arr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            list.add(arr.getString(i))
                        }
                        if (list.isNotEmpty()) {
                            map[type] = list
                        }
                    }
                }
                return map
            }
        } catch (_: Exception) {}
        return emptyMap()
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
                savedLast != null && dbUrls.contains(savedLast) -> savedLast
                savedLast != null && hasCacheForUrl(context, savedLast) -> savedLast
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
        _currentPlaylistUrl.value = cleanUrl

        // If already loaded in memory for the exact same URL and not forcing refresh, return immediately
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

            // Çalma listeleri arasında geçişte önceki liste temizlenir ve eski önbelleklerin birikmesi engellenir
            if (currentLoadedUrl != cleanUrl || forceRefresh) {
                _playlist.value = emptyList()
                _knownGroups.value = emptyMap()
                currentLoadedUrl = null
                currentXtreamCredentials = null
                _categoryLoadingStates.value = emptySet()
                _typeLoadingStates.value = emptyMap()
                _error.value = null
                PlayerRepository.clearForNewPlaylist()
                XtreamCodesApi.clearAllCaches(context)
                cleanOldPlaylistCaches(context, keepUrl = cleanUrl)
            }

            _isDialogDismissed.value = false
            _isLoading.value = true
            _error.value = null

            val cacheFile = getCacheFile(context, cleanUrl)

            // Xtream Codes API Mode: player_api.php ile hızlı API entegrasyonu (buket sırasını korur)
            val parsedCreds = XtreamCodesApi.extractCredentials(cleanUrl, context)
            if (parsedCreds != null) {
                currentXtreamCredentials = parsedCreds
                _loadStep.value = PlaylistLoadStep.PROCESSING
                _loadingDetail.value = "Kategoriler ve buketler API ile alınıyor..."
                _loadingProgress.value = 25

                // ANLIK LİSTE ÇEKME: Xtream Codes JSON API (buket sıralaması korunur)
                try {
                    _loadStep.value = PlaylistLoadStep.DOWNLOADING
                    _loadingDetail.value = "Kategoriler ve yayınlar sunucudan alınıyor..."
                    val previewResult = XtreamCodesApi.initializeCategoriesAndPreview(
                        context = context,
                        creds = currentXtreamCredentials!!,
                        onStreamingBatch = { cats, partialItems, detail ->
                            if (cats.values.any { it.isNotEmpty() }) {
                                _knownGroups.value = cats
                                saveKnownGroupsCache(context, cleanUrl, cats)
                            }
                            if (partialItems.isNotEmpty()) {
                                _playlist.value = partialItems
                                currentLoadedUrl = cleanUrl
                                saveXtreamToM3uCache(context, cleanUrl, partialItems)
                            }
                            _isDialogDismissed.value = true
                            _loadingDetail.value = detail
                        }
                    )
                    val hasCats = previewResult.categoriesByType.values.any { it.isNotEmpty() }
                    val hasItems = previewResult.previewItems.isNotEmpty()

                    if (hasCats || hasItems) {
                        _knownGroups.value = previewResult.categoriesByType
                        saveKnownGroupsCache(context, cleanUrl, previewResult.categoriesByType)

                        _playlist.value = previewResult.previewItems
                        if (hasItems) {
                            saveXtreamToM3uCache(context, cleanUrl, previewResult.previewItems)
                        }

                        val liveCount = previewResult.previewItems.count { it.type == com.example.parser.ItemType.LIVE }
                        val movieCount = previewResult.previewItems.count { it.type == com.example.parser.ItemType.MOVIE }
                        val seriesCount = previewResult.previewItems.count { it.type == com.example.parser.ItemType.SERIES }
                        val catCount = previewResult.categoriesByType.values.sumOf { it.size }
                        android.util.Log.d("PlaylistFetch", "method=API, url=$cleanUrl, live=$liveCount, movies=$movieCount, series=$seriesCount, categories=$catCount")

                        currentLoadedUrl = cleanUrl
                        _isDialogDismissed.value = true
                        _loadStep.value = PlaylistLoadStep.COMPLETED
                        _loadingProgress.value = 100
                        _loadingDetail.value = "$catCount kategori ve ${previewResult.previewItems.size} yayın hazır"
                        _isLoading.value = false
                        return@withLock
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("PlaylistFetch", "method=API_ERROR, url=$cleanUrl, error=${e.message}", e)
                    e.printStackTrace()
                }

                // Xtream API boş döndüyse veya hata verdiyse M3U indirme akışına devam et
                android.util.Log.d("PlaylistFetch", "method=M3U_FALLBACK, url=$cleanUrl, reason=API_returned_empty_or_failed")
            } else {
                android.util.Log.d("PlaylistFetch", "method=M3U_FALLBACK, url=$cleanUrl, reason=No_Xtream_credentials_found")
            }

            // M3U / HLS / Standart Akış İndirme Modu
            // SADECE ve SADECE bu URL'nin kendi önbelleği varsa (asla başka listenin dosyası yüklenmez)
            if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 0L) {
                try {
                    val preview = M3uParser.scanPreviewAndCategories(cacheFile)
                    if (preview.previewItems.isNotEmpty()) {
                        _knownGroups.value = preview.categoriesByType
                        _playlist.value = preview.previewItems
                        currentLoadedUrl = cleanUrl
                        _isDialogDismissed.value = true
                        _loadStep.value = PlaylistLoadStep.PROCESSING
                        _loadingProgress.value = 50
                        _loadingDetail.value = "${preview.previewItems.size} yayın hazır..."
                    }

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
                        _loadingDetail.value = "${fullList.size} kanal hazır"
                        _isLoading.value = false
                        return@withLock
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            // Canlı/Anlık İndirme Modu (Network Download)
            _isDialogDismissed.value = false
            _isLoading.value = true
            _loadStep.value = PlaylistLoadStep.CONNECTING
            _loadingProgress.value = 10
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
                    val isXtream = com.example.parser.XtreamApiClient.parseXtreamUrl(cleanUrl) != null
                    _error.value = if (isXtream) {
                        context.getString(com.example.R.string.error_server_blocked)
                    } else {
                        context.getString(com.example.R.string.error_server_invalid_playlist)
                    }
                    _loadStep.value = PlaylistLoadStep.ERROR
                    _loadingProgress.value = 0
                    delay(1500)
                    return@withLock
                }

                _loadStep.value = PlaylistLoadStep.PROCESSING
                _loadingDetail.value = "Kategoriler ve yayınlar hazırlanıyor..."
                val preview = M3uParser.scanPreviewAndCategories(cacheFile, maxPerCategory = 10)
                if (preview.previewItems.isNotEmpty()) {
                    _knownGroups.value = preview.categoriesByType
                    _playlist.value = preview.previewItems
                    currentLoadedUrl = cleanUrl
                    _isDialogDismissed.value = true
                    _loadStep.value = PlaylistLoadStep.PROCESSING
                    _loadingProgress.value = 50
                    _loadingDetail.value = "${preview.previewItems.size} yayın hazır..."
                }

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
                    val isXtream = com.example.parser.XtreamApiClient.parseXtreamUrl(cleanUrl) != null
                    _error.value = if (isXtream) {
                        context.getString(com.example.R.string.error_server_blocked)
                    } else {
                        context.getString(com.example.R.string.error_server_invalid_playlist)
                    }
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
            listOfNotNull(
                _knownGroups.value[com.example.parser.ItemType.LIVE],
                _knownGroups.value[com.example.parser.ItemType.MOVIE],
                _knownGroups.value[com.example.parser.ItemType.SERIES]
            ).flatten().distinct()
        }
        val fromPlaylist = if (type == null) _playlist.value else _playlist.value.filter { it.type == type }
        val plGroups = fromPlaylist.mapNotNull { it.group }.distinct()
        // API buket sıralamasını birebir koru (A-Z alfabetik sıralamayla bozulmaz)
        val combined = (known + plGroups).distinct()
        return combined.filter { !hidden.contains(it) }
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
        // Artık arka planda yüzlerce kategoriye seri istek atıp sunucudan geçici ban yemiyoruz.
        // Tüm yayınlar initializeCategoriesAndPreview içinde tek seferde (bulk) güvenli çekiliyor.
        backgroundSyncJob?.cancel()
        backgroundSyncJob = null
    }

    fun saveXtreamToM3uCache(context: Context, url: String, items: List<M3uItem>) {
        if (items.isEmpty() || url.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val knownLive = _knownGroups.value[com.example.parser.ItemType.LIVE].orEmpty()
                val knownVod = _knownGroups.value[com.example.parser.ItemType.MOVIE].orEmpty()
                val knownSeries = _knownGroups.value[com.example.parser.ItemType.SERIES].orEmpty()
                val allKnown = knownLive + knownVod + knownSeries
                val groupOrderMap = allKnown.withIndex().associate { it.value to it.index }

                // Yayınları API buket sırasına göre gruplayarak kaydet
                val sortedItems = if (groupOrderMap.isNotEmpty()) {
                    items.sortedBy { item -> groupOrderMap[item.group ?: ""] ?: 999999 }
                } else {
                    items
                }

                val cacheFile = getCacheFile(context, url)
                val tempFile = File(context.filesDir, "${cacheFile.name}.tmp")
                tempFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write("#EXTM3U\n")
                    for (item in sortedItems) {
                        val tvgId = item.tvgId?.let { " tvg-id=\"$it\"" } ?: ""
                        val tvgName = (item.tvgName ?: item.title).let { " tvg-name=\"$it\"" }
                        val logo = item.logo?.let { " tvg-logo=\"$it\"" } ?: ""
                        val group = (item.group ?: "Diğer").let { " group-title=\"$it\"" }
                        val seasonAttr = item.season?.let { " season=\"$it\"" } ?: ""
                        val epAttr = item.episode?.let { " episode=\"$it\"" } ?: ""
                        val seriesAttr = item.seriesName?.let { " series-name=\"$it\"" } ?: ""
                        writer.write("#EXTINF:-1$tvgId$tvgName$logo$group$seasonAttr$epAttr$seriesAttr,${item.title}\n")
                        writer.write("${item.url}\n")
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0L) {
                    tempFile.renameTo(cacheFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun ensureTypeLoaded(context: Context, type: com.example.parser.ItemType) {
        val xtreamCreds = currentXtreamCredentials ?: XtreamCodesApi.extractCredentials(currentLoadedUrl ?: "", context) ?: return
        if (_typeLoadingStates.value[type] == true) return

        // Eğer bu türün yayınları zaten yüklüyse tekrar istek atıp sunucuyu yorma / ban riski oluşturma
        if (_playlist.value.any { it.type == type }) return

        _typeLoadingStates.value = _typeLoadingStates.value + (type to true)
        withContext(Dispatchers.IO) {
            try {
                val cats = XtreamCodesApi.getCategories(xtreamCreds, type)
                if (cats.isNotEmpty()) {
                    val currentKnown = _knownGroups.value.toMutableMap()
                    currentKnown[type] = cats.map { it.categoryName }
                    _knownGroups.value = currentKnown
                    saveKnownGroupsCache(context, currentLoadedUrl ?: "", currentKnown)
                }
                val catsMap = cats.associate { it.categoryId to it.categoryName }
                val streams = XtreamCodesApi.getAllStreams(xtreamCreds, type, catsMap, context)
                if (streams.isNotEmpty()) {
                    val current = _playlist.value.toMutableList()
                    val existingUrls = current.map { it.url }.toSet()
                    val newItems = streams.filter { !existingUrls.contains(it.url) }
                    if (newItems.isNotEmpty()) {
                        current.addAll(newItems)
                        _playlist.value = current
                        saveXtreamToM3uCache(context, currentLoadedUrl ?: "", current)
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
                var catId = XtreamCodesApi.getCategoryId(xtreamCreds, type, groupName)
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
                            saveXtreamToM3uCache(context, currentLoadedUrl ?: "", current)
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
