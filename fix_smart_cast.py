import sys
import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

def replace_playingItem_with_currentItem(match):
    return match.group(0).replace("playingItem", "currentItem")

# Replace inside LaunchedEffect(streamUrl) - wait, we removed streamUrl, it's LaunchedEffect((playingItem?.url ?: ""))
content = content.replace("    LaunchedEffect((playingItem?.url ?: \"\")) {", "    LaunchedEffect((playingItem?.url ?: \"\")) {\n        val currentItem = playingItem")
# Replace inside the second LaunchedEffect
content = content.replace("    // Periodically save progress\n    LaunchedEffect((playingItem?.url ?: \"\")) {", "    // Periodically save progress\n    LaunchedEffect((playingItem?.url ?: \"\")) {\n        val currentItem = playingItem")
# Replace inside DisposableEffect(Unit) -> onDispose
content = content.replace("        onDispose {\n            if (playingItem != null", "        onDispose {\n            val currentItem = playingItem\n            if (currentItem != null")

content = content.replace("playingItem != null", "currentItem != null")
content = content.replace("playingItem.type", "currentItem.type")
content = content.replace("playingItem.seriesName", "currentItem.seriesName")
content = content.replace("playingItem.episode", "currentItem.episode")
content = content.replace("playingItem.title", "currentItem.title")
content = content.replace("playingItem.logo", "currentItem.logo")

# Wait, `playingItem = playlist[index]` should remain `playingItem`, let's make sure I didn't replace that.
content = content.replace("currentItem = playlist[index]", "playingItem = playlist[index]")

open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
print("Fixed smart cast")
