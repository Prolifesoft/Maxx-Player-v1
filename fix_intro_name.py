import re

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "r") as f:
    content = f.read()

content = content.replace("R.raw.intrsso", "R.raw.intro")

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "w") as f:
    f.write(content)
print("Updated")
