package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.RedPrimary

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Timer
import com.example.model.DeviceManager

@Composable
fun WelcomeScreen(
    onPlaylists: () -> Unit,
    onDeviceInfo: () -> Unit,
    onBack: () -> Unit = {}
) {
    BackHandler(enabled = true) {
        onBack()
    }
    val daysLeft = DeviceManager.getDaysRemaining()
    val isPro = DeviceManager.isProPurchased()
    val isTrial = DeviceManager.isTrialActive()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Logo",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Trial / Pro Badge
            AssistChip(
                onClick = onDeviceInfo,
                label = {
                    Text(
                        if (isPro) "PRO Sürüm Aktif" else if (isTrial) "15 Günlük Deneme Paketi ($daysLeft gün kaldı)" else "Deneme Sona Erdi (1 Cihaz/1 Liste)",
                        color = if (isPro) Color(0xFF81C784) else if (isTrial) Color(0xFF64B5F6) else Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isPro) Icons.Default.Verified else if (isTrial) Icons.Default.Timer else Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = if (isPro) Color(0xFF81C784) else if (isTrial) Color(0xFF64B5F6) else Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E232A))
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Çalma listenizi web portalından karekod ile kolayca ekleyebilirsiniz.",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDeviceInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cihaz Bilgileri ve QR Kod", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onPlaylists,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.playlists), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
