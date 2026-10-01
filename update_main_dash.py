import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace(
    """                                    onNavigateToAuth = {
                                        navController.navigate(NavRoutes.GOOGLE_SIGN_IN)
                                    }""",
    """                                    onNavigateToAuth = {
                                        navController.navigate(NavRoutes.GOOGLE_SIGN_IN)
                                    },
                                    onNavigateToPlaylists = {
                                        navController.navigate(NavRoutes.PLAYLISTS)
                                    }"""
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Updated MainActivity.kt for DashboardScreen")
