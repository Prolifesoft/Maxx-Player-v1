import sys
content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

target1 = """            var showUnlockDialog by remember { mutableStateOf(false) }
            var isLockingAction by remember { mutableStateOf(false) }"""

replacement1 = """            var showUnlockDialog by remember { mutableStateOf(false) }"""

content = content.replace(target1, replacement1)

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
