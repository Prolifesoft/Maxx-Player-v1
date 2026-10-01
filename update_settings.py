import re

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

legal_text = """        Text("YASAL SORUMLULUK", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "MAXX PLAYER Herhangi bir içerik sağlamaz Uygulamaya Eklenen Tüm İçerikler yasal sormluluklar tamamen kullanıcıya aittir",
                color = Color.LightGray,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SettingsItem"""

content = content.replace("    }\n}\n\n@Composable\nfun SettingsItem", legal_text)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
