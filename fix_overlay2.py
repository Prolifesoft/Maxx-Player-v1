import re
content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

overlay = """
            AnimatedVisibility(
                visible = isControllerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                val currentItem = playingItem
                val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                    (currentItem.seriesName ?: "") + " " + epPrefix + (currentItem.episode ?: "?") + ": " + (currentItem.title ?: "")
                } else {
                    currentItem?.title ?: ""
                }
                Text(
                    text = title,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
"""

content = content.replace("            FloatingActionButton(", overlay + "\n            FloatingActionButton(")
open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
