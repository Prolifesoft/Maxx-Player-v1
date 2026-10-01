with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    content = f.read()

legal_text_to_remove = """        Text("YASAL SORUMLULUK", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
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
        }"""

# Remove it from the current location
content = content.replace(legal_text_to_remove, "")

# Insert it before the end of the main Column
target_spot = """        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Build, title = stringResource(R.string.settings_support), iconTint = Color(0xFF42A5F5), onClick = { onClose(); onOpenSupport() })
    }"""

new_target_spot = """        SettingsItem(icon = androidx.compose.material.icons.Icons.Default.Build, title = stringResource(R.string.settings_support), iconTint = Color(0xFF42A5F5), onClick = { onClose(); onOpenSupport() })

""" + legal_text_to_remove + """
    }"""

content = content.replace(target_spot, new_target_spot)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(content)
