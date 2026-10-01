import re

with open("app/src/main/java/com/example/ui/screens/SignInScreen.kt", "r") as f:
    content = f.read()

# Add Visibility icons import if not there
if "import androidx.compose.material.icons.filled.Visibility" not in content:
    content = content.replace("import androidx.compose.material.icons.filled.Lock", "import androidx.compose.material.icons.filled.Lock\nimport androidx.compose.material.icons.filled.Visibility\nimport androidx.compose.material.icons.filled.VisibilityOff")

# Add passwordVisible state
content = content.replace("var password by remember { mutableStateOf(\"\") }", "var password by remember { mutableStateOf(\"\") }\n    var passwordVisible by remember { mutableStateOf(false) }")

# Update OutlinedTextField
old_tf = """            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(stringResource(R.string.password), color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                trailingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )"""

new_tf = """            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(stringResource(R.string.password), color = Color.Gray) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.DarkGray
                )
            )"""

content = content.replace(old_tf, new_tf)

with open("app/src/main/java/com/example/ui/screens/SignInScreen.kt", "w") as f:
    f.write(content)
