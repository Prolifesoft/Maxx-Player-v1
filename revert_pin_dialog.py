import sys
content = open("app/src/main/java/com/example/ui/components/PinUnlockDialog.kt").read()

content = content.replace("fun PinUnlockDialog(onUnlock: () -> Unit, onDismiss: () -> Unit, isLocking: Boolean = false)", "fun PinUnlockDialog(onUnlock: () -> Unit, onDismiss: () -> Unit)")
content = content.replace("Text(if (isLocking) \"Kilitle\" else stringResource(R.string.parental_unlock), color = Color.White)", "Text(stringResource(R.string.parental_unlock), color = Color.White)")

open("app/src/main/java/com/example/ui/components/PinUnlockDialog.kt", "w").write(content)
