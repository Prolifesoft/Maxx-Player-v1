import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

if content.startswith("import androidx.compose.material3.Text\npackage"):
    content = content.replace("import androidx.compose.material3.Text\npackage com.example.ui.screens\n", "package com.example.ui.screens\nimport androidx.compose.material3.Text\n")
    open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
    print("Fixed syntax")
