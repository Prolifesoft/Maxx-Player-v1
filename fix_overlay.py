import re

content = open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt").read()

if "var isControllerVisible" not in content:
    content = content.replace("var showReportDialog by remember { mutableStateOf(false) }", "var showReportDialog by remember { mutableStateOf(false) }\n    var isControllerVisible by remember { mutableStateOf(true) }")
    
    view_apply = """PlayerView(context).apply {
                        player = exoPlayer
                        setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
                            isControllerVisible = visibility == android.view.View.VISIBLE
                        })"""
    content = content.replace("PlayerView(context).apply {\n                        player = exoPlayer", view_apply)
    
    box_end = """
            }
        }
    }
}"""
    overlay = """
            AnimatedVisibility(
                visible = isControllerVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                val currentItem = playingItem
                val title = if (currentItem?.type == com.example.parser.ItemType.SERIES) {
                    (currentItem.seriesName ?: "") + " $epPrefix${currentItem.episode ?: "?"}: ${currentItem.title}"
                } else {
                    currentItem?.title ?: ""
                }
                Text(
                    text = title,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
"""
    content = content.replace("                        )\n                    }\n                }\n            )\n        }\n    }\n}", "                        )\n                    }\n                }\n            )\n" + overlay + "        }\n    }\n}")
    
    if "import androidx.compose.material3.Text" not in content:
        content = "import androidx.compose.material3.Text\n" + content

open("app/src/main/java/com/example/ui/screens/PlayerScreen.kt", "w").write(content)
