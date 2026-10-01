with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_click = """                                    .clickable {
                                        playingItem = item
                                        PlayerRepository.currentlyPlayingItem = item
                                        val cUrl = item.url.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                                        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(cUrl)))
                                        exoPlayer.prepare()
                                        exoPlayer.playWhenReady = true
                                        showPlaylistSheet = false
                                    }"""

new_click = """                                    .clickable {
                                        playingItem = item
                                        PlayerRepository.currentlyPlayingItem = item
                                        if (item.url.isNotBlank()) {
                                            val cUrl = item.url.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                                            exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(cUrl)))
                                            exoPlayer.prepare()
                                            exoPlayer.playWhenReady = true
                                        }
                                        showPlaylistSheet = false
                                    }"""

if old_click in content:
    content = content.replace(old_click, new_click)
    print("Replaced click successfully")
else:
    print("Could not find old_click")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
