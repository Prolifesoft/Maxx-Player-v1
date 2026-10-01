import re
content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()
content = content.replace(".padding(horizontal = 16.dp, vertical = 24.dp)", ".padding(start = 16.dp, end = 64.dp, top = 24.dp, bottom = 24.dp)")
open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
