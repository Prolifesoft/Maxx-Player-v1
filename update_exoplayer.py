with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_listener = """                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val index = currentMediaItemIndex
                        if (playlist.isNotEmpty() && index in playlist.indices) {
                            playingItem = playlist[index]
                            PlayerRepository.currentlyPlayingItem = playlist[index]
                        }
                    }
                })"""

new_listener = """                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val index = currentMediaItemIndex
                        if (playlist.isNotEmpty() && index in playlist.indices) {
                            playingItem = playlist[index]
                            PlayerRepository.currentlyPlayingItem = playlist[index]
                        }
                    }
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        super.onPlayerError(error)
                        android.widget.Toast.makeText(context, "Oynatma Hatası: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                })"""

if old_listener in content:
    content = content.replace(old_listener, new_listener)
    print("Replaced Listener")
else:
    print("Could not find Listener")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
