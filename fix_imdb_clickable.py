with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

old_click = """                            .clickable(enabled = item.trailerKey != null) {
                                if (item.trailerKey != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "YouTube uygulaması bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .clickable(enabled = item.trailerKey == null) {
                                android.widget.Toast.makeText(context, "Fragman bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                            }"""

new_click = """                            .clickable {
                                if (item.trailerKey != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${item.trailerKey}"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "YouTube uygulaması bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Fragman bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }"""

content = content.replace(old_click, new_click)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)

