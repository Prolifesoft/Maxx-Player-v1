with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# Fix ImdbCard to handle the trailer click
old_icon_box = """                if (item.trailerKey != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Fragman",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }"""

new_icon_box = """                val context = androidx.compose.ui.platform.LocalContext.current
                if (item.trailerKey != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "YouTube uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Fragman",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .clickable {
                                Toast.makeText(context, "Fragman bulunamadı", Toast.LENGTH_SHORT).show()
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayDisabled,
                            contentDescription = "Fragman Yok",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }"""

content = content.replace(old_icon_box, new_icon_box)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
