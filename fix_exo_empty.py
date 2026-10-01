with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_setup = """                if (playlist.isNotEmpty()) {
                    val mediaItems = playlist.map { 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        val cleanUrl = it.url.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    }
                    setMediaItems(mediaItems)
                    val startIndex = playlist.indexOfFirst { it.url == (playingItem?.url ?: "") }.coerceAtLeast(0)
                    seekTo(startIndex, C.TIME_UNSET)
                } else {
                    val currentItem = playingItem
                    val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                        (currentItem?.seriesName ?: "") + " $epPrefix${currentItem?.episode ?: "?"}: ${currentItem?.title ?: ""}"
                    } else {
                        currentItem?.title ?: ""
                    }
                    val playUrl = playingItem?.url ?: ""
                    val cleanPlayUrl = playUrl.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanPlayUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    )
                }
                prepare()
                playWhenReady = true"""

new_setup = """                if (playlist.isNotEmpty()) {
                    val mediaItems = playlist.map { 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    }
                    setMediaItems(mediaItems)
                    val startIndex = playlist.indexOfFirst { it.url == (playingItem?.url ?: "") }.coerceAtLeast(0)
                    seekTo(startIndex, C.TIME_UNSET)
                    prepare()
                    playWhenReady = true
                } else {
                    val currentItem = playingItem
                    val playUrl = playingItem?.url ?: ""
                    if (playUrl.isNotBlank()) {
                        val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                            (currentItem?.seriesName ?: "") + " $epPrefix${currentItem?.episode ?: "?"}: ${currentItem?.title ?: ""}"
                        } else {
                            currentItem?.title ?: ""
                        }
                        val cleanPlayUrl = playUrl.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                        setMediaItem(
                            MediaItem.Builder()
                                .setUri(Uri.parse(cleanPlayUrl))
                                .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                                .build()
                        )
                        prepare()
                        playWhenReady = true
                    }
                }"""

if old_setup in content:
    content = content.replace(old_setup, new_setup)
    print("Replaced successfully")
else:
    print("Could not find old_setup")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
