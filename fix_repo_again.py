import sys
import re

content = open("app/src/main/java/com/example/model/PlaylistRepository.kt").read()

content = content.replace("    }\n    }\n        fun getGroups", "    }\n        fun getGroups")

open("app/src/main/java/com/example/model/PlaylistRepository.kt", "w").write(content)
print("PlaylistRepository syntax fixed")
