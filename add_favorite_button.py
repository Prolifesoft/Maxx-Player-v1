import sys

content = open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt").read()

favorite_logic = """            val favoriteUrls by com.example.model.FavoritesManager.favoriteUrls.collectAsState()
            val isFavorite = favoriteUrls.contains(item.url)
            var showReportDialog by remember { mutableStateOf(false) }"""

content = content.replace("            var showReportDialog by remember { mutableStateOf(false) }", favorite_logic)

favorite_button = """                IconButton(
                    onClick = {
                        com.example.model.FavoritesManager.toggleFavorite(item.url)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                ) {
                    Icon(
                        if (isFavorite) androidx.compose.material.icons.Icons.Default.Favorite else androidx.compose.material.icons.Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton("""

content = content.replace("                IconButton(", favorite_button, 2)

open("test_dash.kt", "w").write(content)
