import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("var currentUserId by remember { mutableStateOf(\"\") }", "var currentUserId by remember { mutableStateOf(\"\") }\n                    var isM3uMode by remember { mutableStateOf(false) }")

content = content.replace("onAddUser = { navController.navigate(NavRoutes.LOGIN) }", "onAddUser = { isM3uMode = false; navController.navigate(NavRoutes.LOGIN) },\n                                    onAddUrl = { isM3uMode = true; navController.navigate(NavRoutes.LOGIN) }")

content = content.replace("onAddPlaylist = { navController.navigate(NavRoutes.LOGIN) }", "onAddPlaylist = { isM3u -> isM3uMode = isM3u; navController.navigate(NavRoutes.LOGIN) }")

content = content.replace("val urlToLoad = \"$host/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts\"", "val urlToLoad = if (user.isEmpty() && pass.isEmpty()) host else \"$host/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts\"")

content = content.replace("SignInScreen(\n                                    userId = currentUserId,", "SignInScreen(\n                                    userId = currentUserId,\n                                    isM3uMode = isM3uMode,")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Updated MainActivity.kt")
