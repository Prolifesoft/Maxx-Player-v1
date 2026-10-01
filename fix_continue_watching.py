import sys

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

# Replace matchedItem with the correct list based on type
replacement = """
                                                    val playlistToPlay = if (matchedItem.type == com.example.parser.ItemType.SERIES) {
                                                        allItems.filter { it.type == com.example.parser.ItemType.SERIES && it.seriesName == matchedItem.seriesName }.sortedBy { it.episode ?: 0 }
                                                    } else {
                                                        allItems.filter { it.type == matchedItem.type }
                                                    }
                                                    playWithPlaylist(matchedItem, playlistToPlay)
"""
content = content.replace("playWithPlaylist(matchedItem, listOf(matchedItem))", replacement.strip())

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
print("Fixed continue watching playlists")
