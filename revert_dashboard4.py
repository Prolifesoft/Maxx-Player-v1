import sys
content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

target1 = """                    if (!hasPin) {
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        isLockingAction = !isLocked
                        showUnlockDialog = true
                    }"""

replacement1 = """                    if (!hasPin) {
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                    } else if (isLocked) {
                        showUnlockDialog = true
                    } else {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                    }"""
content = content.replace(target1, replacement1)

target2 = """            if (showUnlockDialog) {
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

replacement2 = """            if (showUnlockDialog) {
                com.example.ui.components.PinUnlockDialog(
                    onUnlock = {
                        com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                        showUnlockDialog = false
                        android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_unlocked), android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showUnlockDialog = false }
                )
            }"""
content = content.replace(target2, replacement2)

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
