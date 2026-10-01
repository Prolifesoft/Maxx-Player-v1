import re

with open("app/src/main/java/com/example/ui/screens/PlayListsScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "fun PlayListsScreen(userId: String, onBack: () -> Unit, onSelectPlaylist: (String, String, String) -> Unit, onAddPlaylist: (isM3u: Boolean) -> Unit) {",
    "fun PlayListsScreen(userId: String, onBack: () -> Unit, onSelectPlaylist: (String, String, String) -> Unit, onAddPlaylist: (isM3u: Boolean) -> Unit, onEditPlaylist: (Int, Boolean) -> Unit) {"
)

edit_icon_row = """
                        Row {
                            IconButton(onClick = {
                                onEditPlaylist(playlist.id, playlist.username.isEmpty())
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = Color.Gray)
                            }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    db.iptvDao().deletePlaylist(playlist)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color.Gray)
                            }
                        }
"""
content = re.sub(
    r'Row\s*\{\s*IconButton\(onClick = \{\s*coroutineScope\.launch\s*\{\s*db\.iptvDao\(\)\.deletePlaylist\(playlist\)\s*\}\s*\}\)\s*\{\s*Icon\(Icons\.Default\.Delete, contentDescription = "Sil", tint = Color\.Gray\)\s*\}\s*\}',
    edit_icon_row.strip(),
    content
)

with open("app/src/main/java/com/example/ui/screens/PlayListsScreen.kt", "w") as f:
    f.write(content)
print("Updated PlayListsScreen.kt")
