import sys
import re

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

content = content.replace("val currentSeries = selectedSeries!!", "val currentSeries = selectedSeries ?: return@SeriesDetailSheet")

open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w").write(content)
print("NPE fixed")
