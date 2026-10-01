import sys
import re

content = open("app/src/main/java/com/example/parser/M3uParser.kt").read()

# Add ParseResult class
imports_pattern = "object M3uParser {"
replacement = """data class ParseResult(val isUnchanged: Boolean, val items: List<M3uItem>?, val bytes: ByteArray?)

object M3uParser {"""
content = content.replace(imports_pattern, replacement)

# Add parseFromBytes and downloadAndParse functions
new_funcs = """
    suspend fun parseFromBytes(
        bytes: ByteArray,
        onProgress: suspend (Int, String) -> Unit = { _, _ -> }
    ): List<M3uItem> = withContext(Dispatchers.IO) {
        parse(ByteArrayInputStream(bytes), bytes.size.toLong(), onProgress)
    }

    suspend fun downloadAndParse(
        url: String,
        cachedBytes: ByteArray?,
        onProgress: suspend (PlaylistLoadStep, Int, String) -> Unit = { _, _, _ -> }
    ): ParseResult = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        try {
            onProgress(PlaylistLoadStep.CONNECTING, 5, "")
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()
                    
                    onProgress(PlaylistLoadStep.DOWNLOADING, 10, "")
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    val outputStream = ByteArrayOutputStream()
                    
                    var lastReportTime = System.currentTimeMillis()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 100) {
                            lastReportTime = now
                            val progress = if (contentLength > 0) {
                                10 + ((totalBytesRead.toDouble() / contentLength) * 40).toInt().coerceIn(0, 40)
                            } else {
                                10 + (40 * (1.0 - Math.exp(-totalBytesRead.toDouble() / 2000000.0))).toInt().coerceIn(0, 40)
                            }
                            val mb = String.format("%.1f MB", totalBytesRead / (1024.0 * 1024.0))
                            onProgress(PlaylistLoadStep.DOWNLOADING, progress, mb)
                        }
                    }
                    outputStream.flush()
                    val downloadedBytes = outputStream.toByteArray()
                    
                    // Check if identical to cache using MD5
                    if (cachedBytes != null && downloadedBytes.size == cachedBytes.size) {
                        val md = java.security.MessageDigest.getInstance("MD5")
                        val downloadedHash = md.digest(downloadedBytes).contentEquals(md.digest(cachedBytes))
                        if (downloadedHash) {
                            onProgress(PlaylistLoadStep.COMPLETED, 100, "")
                            return@withContext ParseResult(true, null, null)
                        }
                    }
                    
                    onProgress(PlaylistLoadStep.PROCESSING, 50, "")
                    
                    val items = parse(ByteArrayInputStream(downloadedBytes), downloadedBytes.size.toLong()) { pct, detail ->
                        onProgress(PlaylistLoadStep.PROCESSING, 50 + (pct / 2), detail)
                    }
                    onProgress(PlaylistLoadStep.COMPLETED, 100, "")
                    return@withContext ParseResult(false, items, downloadedBytes)
                }
            } else {
                onProgress(PlaylistLoadStep.ERROR, 0, "HTTP ${response.code}")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            onProgress(PlaylistLoadStep.ERROR, 0, e.message ?: "Bağlantı veya bellek hatası")
        }
        return@withContext ParseResult(false, emptyList(), null)
    }
"""

content = content.replace("object M3uParser {", "object M3uParser {" + new_funcs)

open("app/src/main/java/com/example/parser/M3uParser.kt", "w").write(content)
print("M3uParser updated")
