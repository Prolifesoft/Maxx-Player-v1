package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ParentalControlManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinUnlockDialog(onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val currentPin by ParentalControlManager.pinCode.collectAsState()
    var inputPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
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
                    text = stringResource(R.string.parental_enter_pin),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = inputPin,
                    onValueChange = { inputPin = it; pinError = false },
                    isError = pinError,
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
                if (pinError) {
                    Text(
                        text = stringResource(R.string.parental_wrong_pin),
                        color = Color(0xFFEF5350),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputPin == currentPin) {
                        onUnlock()
                    } else {
                        pinError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))
            ) {
                Text(stringResource(R.string.parental_unlock), color = Color.White)
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
