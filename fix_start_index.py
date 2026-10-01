with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_start = """                    val mediaItems = playlist.mapNotNull {
                        if (it.url.isBlank()) return@mapNotNull null 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    }
                    setMediaItems(mediaItems)
                    val startIndex = playlist.indexOfFirst { it.url == (playingItem?.url ?: "") }.coerceAtLeast(0)
                    seekTo(startIndex, C.TIME_UNSET)"""

new_start = """                    val validPlaylist = playlist.filter { it.url.isNotBlank() }
                    val mediaItems = validPlaylist.map { 
                        val title = if (it.type == com.example.parser.ItemType.SERIES) {
                            (it.seriesName ?: "") + " $epPrefix${it.episode ?: "?"}: ${it.title}"
                        } else {
                            it.title
                        }
                        val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    }
                    setMediaItems(mediaItems)
                    val startIndex = validPlaylist.indexOfFirst { it.url == (playingItem?.url ?: "") }.coerceAtLeast(0)
                    seekTo(startIndex, C.TIME_UNSET)"""

content = content.replace(old_start, new_start)

old_transition = """                        if (playlist.isNotEmpty() && index in playlist.indices) {
                            playingItem = playlist[index]
                            PlayerRepository.currentlyPlayingItem = playlist[index]
                        }"""

new_transition = """                        val validPlaylist = playlist.filter { it.url.isNotBlank() }
                        if (validPlaylist.isNotEmpty() && index in validPlaylist.indices) {
                            playingItem = validPlaylist[index]
                            PlayerRepository.currentlyPlayingItem = validPlaylist[index]
                        }"""

content = content.replace(old_transition, new_transition)

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
