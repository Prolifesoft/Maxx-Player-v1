import sys
content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

target1 = """            var showUnlockDialog by remember { mutableStateOf(false) }

            Row("""

replacement1 = """            var showUnlockDialog by remember { mutableStateOf(false) }
            var isLockingAction by remember { mutableStateOf(false) }

            Row("""
content = content.replace(target1, replacement1)

target2 = """                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                        }"""

replacement2 = """                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isLockingAction = !isLocked
                            showUnlockDialog = true
                        }"""
content = content.replace(target2, replacement2)

target3 = """            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }"""
            
replacement3 = """            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        val msg = if (isLockingAction) R.string.parental_channel_locked else R.string.parental_channel_unlocked
                        android.widget.Toast.makeText(context, context.getString(msg), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false },
                    isLocking = isLockingAction
                )
            }"""
            
content = content.replace(target3, replacement3)

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
