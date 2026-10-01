import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace(
    "var isM3uMode by remember { mutableStateOf(false) }", 
    "var isM3uMode by remember { mutableStateOf(false) }\n                    var editingPlaylistId by remember { mutableStateOf<Int?>(null) }"
)

content = content.replace(
    "onAddUser = { isM3uMode = false; navController.navigate(NavRoutes.LOGIN) },",
    "onAddUser = { editingPlaylistId = null; isM3uMode = false; navController.navigate(NavRoutes.LOGIN) },"
)

content = content.replace(
    "onAddUrl = { isM3uMode = true; navController.navigate(NavRoutes.LOGIN) },",
    "onAddUrl = { editingPlaylistId = null; isM3uMode = true; navController.navigate(NavRoutes.LOGIN) },"
)

content = content.replace(
    "onAddPlaylist = { isM3u -> isM3uMode = isM3u; navController.navigate(NavRoutes.LOGIN) }",
    "onAddPlaylist = { isM3u -> editingPlaylistId = null; isM3uMode = isM3u; navController.navigate(NavRoutes.LOGIN) },\n                                    onEditPlaylist = { id, isM3u -> editingPlaylistId = id; isM3uMode = isM3u; navController.navigate(NavRoutes.LOGIN) }"
)

content = content.replace(
    "isM3uMode = isM3uMode,",
    "isM3uMode = isM3uMode,\n                                    editingPlaylistId = editingPlaylistId,"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Updated MainActivity.kt for Editing")
