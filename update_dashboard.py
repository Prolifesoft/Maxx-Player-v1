import re

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "fun DashboardScreen(onPlayStream: (M3uItem) -> Unit, onNavigateToAuth: () -> Unit = {}) {", 
    "fun DashboardScreen(onPlayStream: (M3uItem) -> Unit, onNavigateToAuth: () -> Unit = {}, onNavigateToPlaylists: () -> Unit = {}) {"
)

content = content.replace(
    "ProfileSettingsSheet(onNavigateToAuth = onNavigateToAuth)", 
    "ProfileSettingsSheet(onNavigateToAuth = onNavigateToAuth, onNavigateToPlaylists = onNavigateToPlaylists)"
)

content = content.replace(
    "fun ProfileSettingsSheet(onNavigateToAuth: () -> Unit = {}) {", 
    "fun ProfileSettingsSheet(onNavigateToAuth: () -> Unit = {}, onNavigateToPlaylists: () -> Unit = {}) {"
)

# Insert Oynatma Listeleri row in ProfileSettingsSheet
row_to_insert = """
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToPlaylists() }
                .padding(vertical = 10.dp)
        ) {
            Icon(Icons.Default.FeaturedPlayList, contentDescription = null, tint = Color(0xFFE53935))
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(com.example.R.string.playlists), color = Color.White, fontSize = 15.sp)
        }
        """
content = content.replace(
    "Text(stringResource(com.example.R.string.account_management), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))", 
    f"Text(stringResource(com.example.R.string.account_management), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))\n{row_to_insert}"
)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
print("Updated DashboardScreen.kt")
