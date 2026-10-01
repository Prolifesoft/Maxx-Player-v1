import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

builder_str = """
                if (playlist.isNotEmpty()) {
                    val mediaItems = playlist.map { 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        MediaItem.Builder()
                            .setUri(Uri.parse(it.url))
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
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.parse(playingItem?.url ?: ""))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    )
                }
"""

# Replace the block
pattern = re.compile(r"if \(playlist\.isNotEmpty\(\)\) \{.*?\} else \{.*?\}", re.DOTALL)
content = pattern.sub(builder_str.strip(), content)

open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
