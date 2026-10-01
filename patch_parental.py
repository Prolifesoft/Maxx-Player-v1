import sys
content = open("app/src/main/java/com/example/ui/components/ParentalControlDialog.kt").read()

target = """    var isResetMode by remember { mutableStateOf(false) }

    var newPin by remember { mutableStateOf("") }"""

replacement = """    var isResetMode by remember { mutableStateOf(false) }
    var isRemoveMode by remember { mutableStateOf(false) }
    var removeStep by remember { mutableStateOf(1) }
    var inputPin by remember { mutableStateOf("") }

    var newPin by remember { mutableStateOf("") }"""
content = content.replace(target, replacement)

target2 = """            } else if (isResetMode) {"""

replacement2 = """            } else if (isRemoveMode) {
                Column {
                    if (removeStep == 1) {
                        Text(
                            text = stringResource(R.string.parental_enter_pin),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputPin,
                            onValueChange = { inputPin = it; inputError = false },
                            label = { Text(stringResource(R.string.parental_enter_pin), color = Color.Gray) },
                            isError = inputError,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(image, "Toggle password visibility")
                                }
                            }
                        )
                        if (inputError) {
                            Text(
                                text = stringResource(R.string.parental_wrong_pin),
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Text(
                            text = secretQuestion ?: "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answerInput,
                            onValueChange = { answerInput = it; inputError = false },
                            label = { Text(stringResource(R.string.parental_secret_answer), color = Color.Gray) },
                            isError = inputError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        if (inputError) {
                            Text(
                                text = stringResource(R.string.parental_wrong_answer),
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = {
                            // Sends email to cem_033@msn.com internally
                            SupportRepository.createTicket(
                                title = "PIN Remove Request",
                                channelName = "Master Admin",
                                message = "User forgot PIN and secret answer. Requesting master admin intervention for PIN removal."
                            )
                            Toast.makeText(context, context.getString(R.string.parental_support_sent), Toast.LENGTH_LONG).show()
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.parental_support_btn), color = Color(0xFF42A5F5))
                        }
                    }
                }
            } else if (isResetMode) {"""

content = content.replace(target2, replacement2)

target3 = """                    Button(
                        onClick = {
                            ParentalControlManager.removePin()
                            Toast.makeText(context, context.getString(R.string.parental_pin_remove), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.parental_pin_remove), color = Color.White)
                    }"""

replacement3 = """                    Button(
                        onClick = {
                            isRemoveMode = true
                            removeStep = 1
                            inputError = false
                            inputPin = ""
                            answerInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.parental_pin_remove), color = Color.White)
                    }"""

content = content.replace(target3, replacement3)

target4 = """            } else if (isResetMode) {
                Button(
                    onClick = {"""

replacement4 = """            } else if (isRemoveMode) {
                Button(
                    onClick = {
                        if (removeStep == 1) {
                            if (inputPin == currentPin) {
                                ParentalControlManager.removePin()
                                Toast.makeText(context, context.getString(R.string.parental_pin_remove), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                inputError = true
                                removeStep = 2 // transition to step 2 on error
                                answerInput = ""
                            }
                        } else {
                            if (answerInput.trim().equals(secretAnswer?.trim(), ignoreCase = true)) {
                                ParentalControlManager.removePin()
                                Toast.makeText(context, context.getString(R.string.parental_pin_remove), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                inputError = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text(if (removeStep == 1) stringResource(R.string.parental_unlock) else stringResource(R.string.parental_pin_remove), color = Color.White)
                }
            } else if (isResetMode) {
                Button(
                    onClick = {"""

content = content.replace(target4, replacement4)

open("app/src/main/java/com/example/ui/components/ParentalControlDialog.kt", "w").write(content)
