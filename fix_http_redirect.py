import sys
import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

if "setAllowCrossProtocolRedirects(true)" in content:
    print("Cross protocol redirect already enabled")

