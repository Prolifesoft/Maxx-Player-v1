with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

# 1. Remove the old box logic from the image Box
import re
box_pattern = r"val context = androidx\.compose\.ui\.platform\.LocalContext\.current.*?if \(item\.trailerKey != null\) \{.*?\}( else \{.*?\}\s*)?"
content = re.sub(box_pattern, "val context = androidx.compose.ui.platform.LocalContext.current", content, flags=re.DOTALL)

# 2. Modify the Button section in ImdbCard to include the trailer button next to it.
old_button_section = """                Button(
                    onClick = onNotifyToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isNotified) "Haber Verilecek" else "Haber Ver",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }"""

new_button_section = """                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onNotifyToggle,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isNotified) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isNotified) "Haber Verilecek" else "Haber Ver",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                color = if (item.trailerKey != null) Color(0xFFE50914) else Color.DarkGray,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = item.trailerKey != null) {
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.trailerKey != null) Icons.Default.PlayArrow else Icons.Default.PlayDisabled,
                            contentDescription = if (item.trailerKey != null) "Fragman İzle" else "Fragman Yok",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }"""

content = content.replace(old_button_section, new_button_section)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
