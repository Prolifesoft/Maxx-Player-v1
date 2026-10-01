with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

old_map = """                    val mediaItems = playlist.map {"""
new_map = """                    val mediaItems = playlist.mapNotNull {
                        if (it.url.isBlank()) return@mapNotNull null"""
                        
if old_map in content:
    content = content.replace(old_map, new_map)
    print("Replaced map successfully")
else:
    print("Could not find old_map")

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)
