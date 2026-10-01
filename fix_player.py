with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_playlist_item = """                        MediaItem.Builder()
                            .setUri(Uri.parse(it.url))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()"""

new_playlist_item = """                        val cleanUrl = it.url.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()"""

content = content.replace(old_playlist_item, new_playlist_item)

old_single_item = """                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.parse(playingItem?.url ?: ""))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    )"""

new_single_item = """                    val playUrl = playingItem?.url ?: ""
                    val cleanPlayUrl = playUrl.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(Uri.parse(cleanPlayUrl))
                            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).setDisplayTitle(title).build())
                            .build()
                    )"""

content = content.replace(old_single_item, new_single_item)


old_clickable = """                                        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(item.url)))"""
new_clickable = """                                        val cUrl = item.url.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }
                                        exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(cUrl)))"""

content = content.replace(old_clickable, new_clickable)

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
