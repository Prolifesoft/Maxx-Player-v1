import sys
content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

target = """                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            isLockingAction = !isLocked
                            showUnlockDialog = true
                        }"""

replacement = """                        if (!hasPin) {
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_control_desc), android.widget.Toast.LENGTH_SHORT).show()
                        } else if (isLocked) {
                            showUnlockDialog = true
                        } else {
                            com.example.model.ParentalControlManager.toggleChannelLock(item.url)
                            android.widget.Toast.makeText(context, context.getString(R.string.parental_channel_locked), android.widget.Toast.LENGTH_SHORT).show()
                        }"""

content = content.replace(target, replacement)

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
