package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ParentalControlManager
import com.example.model.SupportRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentalControlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentPin by ParentalControlManager.pinCode.collectAsState()
    val secretQuestion by ParentalControlManager.secretQuestion.collectAsState()
    val secretAnswer by ParentalControlManager.secretAnswer.collectAsState()

    var isResetMode by remember { mutableStateOf(false) }
    var isRemoveMode by remember { mutableStateOf(false) }
    var removeStep by remember { mutableStateOf(1) }
    var inputPin by remember { mutableStateOf("") }

    var newPin by remember { mutableStateOf("") }
    var questionInput by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            com.example.util.KeepSystemBarsHidden()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFE53935)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isResetMode) stringResource(R.string.parental_reset_title) 
                           else if (isRemoveMode) "PIN Kaldır"
                           else if (currentPin == null) stringResource(R.string.parental_setup_title) 
                           else stringResource(R.string.parental_control_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (currentPin == null) {
                Column {
                    Text(
                        text = stringResource(R.string.parental_control_desc),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if(it.length <= 4) newPin = it },
                        label = { Text(stringResource(R.string.parental_pin_set), color = Color.Gray) },
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        label = { Text(stringResource(R.string.parental_secret_question), color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = answerInput,
                        onValueChange = { answerInput = it },
                        label = { Text(stringResource(R.string.parental_secret_answer), color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            } else if (isRemoveMode) {
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
            } else if (isResetMode) {
                Column {
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if(it.length <= 4) newPin = it },
                        label = { Text(stringResource(R.string.parental_new_pin), color = Color.Gray) },
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
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = {
                        // Sends email to cem_033@msn.com internally
                        SupportRepository.createTicket(
                            title = "PIN Reset Request",
                            channelName = "Master Admin",
                            message = "User forgot PIN and secret answer. Requesting master admin intervention. "
                        )
                        Toast.makeText(context, context.getString(R.string.parental_support_sent), Toast.LENGTH_LONG).show()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.parental_support_btn), color = Color(0xFF42A5F5))
                    }
                }
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.parental_control_desc),
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { isResetMode = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.parental_forgot_pin), color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
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
                    }
                }
            }
            }
        },
        confirmButton = {
            if (currentPin == null) {
                Button(
                    onClick = {
                        if (newPin.isNotBlank() && questionInput.isNotBlank() && answerInput.isNotBlank()) {
                            ParentalControlManager.setupPin(newPin, questionInput, answerInput)
                            Toast.makeText(context, context.getString(R.string.parental_save), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text(stringResource(R.string.parental_save), color = Color.White)
                }
            } else if (isRemoveMode) {
                Button(
                    onClick = {
                        if (removeStep == 1) {
                            if (inputPin == currentPin) {
                                ParentalControlManager.removePin()
                                Toast.makeText(context, context.getString(R.string.parental_pin_remove), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                inputError = false
                                removeStep = 2 // transition to step 2 on error
                                answerInput = ""
                                Toast.makeText(context, context.getString(R.string.parental_wrong_pin), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (answerInput.trim().equals(secretAnswer?.trim(), ignoreCase = true)) {
                                ParentalControlManager.removePin()
                                Toast.makeText(context, context.getString(R.string.parental_pin_remove), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                // Send mail automatically on wrong answer
                                SupportRepository.createTicket(
                                    title = "PIN Remove Request",
                                    channelName = "Master Admin",
                                    message = "User failed PIN and secret answer. Requesting master admin intervention for PIN removal."
                                )
                                Toast.makeText(context, context.getString(R.string.parental_support_sent), Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text(if (removeStep == 1) stringResource(R.string.parental_unlock) else stringResource(R.string.parental_pin_remove), color = Color.White)
                }
            } else if (isResetMode) {
                Button(
                    onClick = {
                        if (answerInput.trim().equals(secretAnswer?.trim(), ignoreCase = true)) {
                            if (newPin.isNotBlank()) {
                                ParentalControlManager.resetPin(newPin)
                                Toast.makeText(context, context.getString(R.string.parental_save), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        } else {
                            inputError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66BB6A))
                ) {
                    Text(stringResource(R.string.parental_save), color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_desc), color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1E1E1E)
    )
}
