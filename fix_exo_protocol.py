with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "r") as f:
    content = f.read()

# Replace cleanUrl logic
import re

old_clean1 = """val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }"""
new_clean1 = """val cleanUrl = it.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }"""
content = content.replace(old_clean1, new_clean1)

old_clean2 = """val cleanPlayUrl = playUrl.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }"""
new_clean2 = """val cleanPlayUrl = playUrl.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }"""
content = content.replace(old_clean2, new_clean2)

old_clean3 = """val cUrl = item.url.trim().let { u -> if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) "http://$u" else u }"""
new_clean3 = """val cUrl = item.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }"""
content = content.replace(old_clean3, new_clean3)

with open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w") as f:
    f.write(content)

