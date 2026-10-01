import re

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    """            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (!hasNavigated) {
                    hasNavigated = true
                    onNavigateToLogin()
                }
            }""",
    """            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.widget.Toast.makeText(context, "Video oynatılamadı: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
                if (!hasNavigated) {
                    hasNavigated = true
                    onNavigateToLogin()
                }
            }"""
)

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "w") as f:
    f.write(content)
print("Updated")
