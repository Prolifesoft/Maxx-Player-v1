import sys

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

content = content.replace("playWithPlaylist(item, listOf(item))", "playWithPlaylist(item, filteredItems)")
content = content.replace("playWithPlaylist(heroItem, listOf(heroItem))", "playWithPlaylist(heroItem, filteredItems)")
content = content.replace("playWithPlaylist(matchedItem, listOf(matchedItem))", "playWithPlaylist(matchedItem, listOf(matchedItem))") # Keep continue watching as single or fetch list? Actually Continue Watching should probably play as single item, or maybe fetch series... keeping it as is for now.

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
print("Reverted playWithPlaylist changes")
