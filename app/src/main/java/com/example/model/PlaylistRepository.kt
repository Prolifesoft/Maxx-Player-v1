package com.example.model

import com.example.parser.M3uItem
import com.example.parser.M3uParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
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
    
    suspend fun loadPlaylist(context: Context, url: String) {
        if (_isLoading.value) return
        _isLoading.value = true
        _error.value = null
        _loadStep.value = PlaylistLoadStep.CONNECTING
        _loadingProgress.value = 5
        _loadingDetail.value = "Önbellek kontrol ediliyor..."

        try {
            val urlHash = MessageDigest.getInstance("MD5").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val cacheFile = File(context.filesDir, "playlist_cache_$urlHash.m3u")
            
            var cachedItems: List<M3uItem>? = null
            var cachedBytes: ByteArray? = null
            
            if (cacheFile.exists()) {
                _loadStep.value = PlaylistLoadStep.PROCESSING
                _loadingDetail.value = "Önbellekten yükleniyor..."
                cachedBytes = withContext(Dispatchers.IO) { cacheFile.readBytes() }
                
                cachedItems = M3uParser.parseFromBytes(cachedBytes) { pct, detail ->
                    _loadStep.value = PlaylistLoadStep.PROCESSING
                    _loadingProgress.value = pct
                    _loadingDetail.value = detail
                }
                
                if (cachedItems.isNotEmpty()) {
                    _playlist.value = cachedItems
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = "Önbellekten yüklendi"
                    // Short delay to show completion, but we still want to check for updates in background
                }
            }
            
            // Now fetch from network to check for updates
            _loadStep.value = PlaylistLoadStep.DOWNLOADING
            _loadingDetail.value = "Güncellemeler kontrol ediliyor..."
            
            val networkResult = M3uParser.downloadAndParse(url, cachedBytes) { step, progress, detail ->
                // Only update progress if we didn't just load from cache, or if we want to show background update
                if (cachedItems == null || step != PlaylistLoadStep.PROCESSING) {
                    _loadStep.value = step
                    _loadingProgress.value = progress
                    _loadingDetail.value = detail
                }
            }
            
            if (networkResult.isUnchanged) {
                // Same as cache, do nothing
                if (cachedItems != null) {
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = "Liste güncel"
                    delay(600)
                } else {
                    _error.value = "Listede yayın bulunamadı"
                    _loadStep.value = PlaylistLoadStep.ERROR
                }
            } else if (networkResult.items != null) {
                if (networkResult.items.isEmpty()) {
                    _error.value = "Listede yayın bulunamadı"
                    _loadStep.value = PlaylistLoadStep.ERROR
                    _loadingProgress.value = 0
                    delay(2000)
                } else {
                    // Update cache
                    withContext(Dispatchers.IO) {
                        networkResult.bytes?.let { cacheFile.writeBytes(it) }
                    }
                    _playlist.value = networkResult.items
                    _loadStep.value = PlaylistLoadStep.COMPLETED
                    _loadingProgress.value = 100
                    _loadingDetail.value = ""
                    delay(600)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // If we already loaded from cache, don't show error to disrupt user
            if (_playlist.value.isEmpty()) {
                _error.value = e.message ?: "Bağlantı veya bellek hatası"
                _loadStep.value = PlaylistLoadStep.ERROR
                _loadingProgress.value = 0
                delay(2000)
            }
        } finally {
            _isLoading.value = false
            // Don't reset IDLE if we successfully completed or if we already have items
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
