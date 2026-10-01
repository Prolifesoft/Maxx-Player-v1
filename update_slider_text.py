with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

old_text = """        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {"""

new_text = """        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
        ) {"""

if old_text in content:
    content = content.replace(old_text, new_text)
    print("Replaced HeroBanner text width")
else:
    print("Could not find text block")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)

