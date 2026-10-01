import sys

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

content = content.replace("playWithPlaylist(item, filteredItems)", "playWithPlaylist(item, listOf(item))")
content = content.replace("playWithPlaylist(heroItem, filteredItems)", "playWithPlaylist(heroItem, listOf(heroItem))")

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
print("Fixed playWithPlaylist")
