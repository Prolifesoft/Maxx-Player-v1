with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "r") as f:
    content = f.read()

content = content.replace('Uri.parse("asset:///intro1.mp4")', 'Uri.parse("file:///android_asset/intro1.mp4")')

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "w") as f:
    f.write(content)
