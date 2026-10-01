import sys
import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

content = content.replace("val playingItem = PlayerRepository.currentlyPlayingItem", "var playingItem by remember { mutableStateOf(PlayerRepository.currentlyPlayingItem) }")
content = content.replace("val streamUrl = playingItem?.url ?: \"\"", "")

# We need to replace usages of streamUrl with playingItem?.url ?: ""
content = content.replace("streamUrl", '(playingItem?.url ?: "")')

# And add a listener to ExoPlayer.Builder
listener_code = """
                prepare()
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        super.onMediaItemTransition(mediaItem, reason)
                        val index = currentMediaItemIndex
                        if (playlist.isNotEmpty() && index in playlist.indices) {
                            playingItem = playlist[index]
                            PlayerRepository.currentlyPlayingItem = playlist[index]
                        }
                    }
                })
"""
content = content.replace("prepare()\n                playWhenReady = true", listener_code.strip())

open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
print("Added Player.Listener")
