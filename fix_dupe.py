import sys
content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()
content = content.replace("val currentItem = playingItem\n        val currentItem = playingItem", "val currentItem = playingItem")
open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
print("Fixed duplicated line")
