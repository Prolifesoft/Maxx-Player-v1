import sys
import re

content = open("app/src/main/java/com/example/model/PlaylistRepository.kt").read()

import_pattern = "import kotlinx.coroutines.delay"
replacement_import = """import kotlinx.coroutines.delay
import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext"""
content = content.replace(import_pattern, replacement_import)

load_pattern = r"suspend fun loadPlaylist\(url: String = DEFAULT_M3U\) \{(.*?)\s*if \(_isLoading\.value\) return\s*_isLoading\.value = true(.*?)try \{(.*?)\s*val items = M3uParser\.parseFromUrl\(url\) \{ step, progress, detail ->\s*_loadStep\.value = step\s*_loadingProgress\.value = progress\s*_loadingDetail\.value = detail\s*\}(.*?)\s*\} catch \(e: Throwable\) \{(.*?)\s*\} finally \{(.*?)\s*\}"

def load_repl(m):
    return """suspend fun loadPlaylist(context: Context, url: String = DEFAULT_M3U) {
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
    }"""

content = re.sub(load_pattern, load_repl, content, flags=re.DOTALL)

open("app/src/main/java/com/example/model/PlaylistRepository.kt", "w").write(content)
print("PlaylistRepository modified")
