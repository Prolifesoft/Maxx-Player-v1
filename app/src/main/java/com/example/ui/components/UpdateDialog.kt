package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateInfo by UpdateManager.updateInfo.collectAsState()
    val isDownloading by UpdateManager.isDownloading.collectAsState()
    val downloadProgress by UpdateManager.downloadProgress.collectAsState()
    val errorMsg by UpdateManager.errorMsg.collectAsState()
    val upToDateMsg by UpdateManager.upToDateMessage.collectAsState()

    if (updateInfo != null && updateInfo!!.updateAvailable) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = {
                if (!info.forceUpdate) {
                    UpdateManager.dismissUpdate()
                }
            },
            title = {
                com.example.util.KeepSystemBarsHidden()
                Text(
                    text = if (info.forceUpdate) "Zorunlu Güncelleme Mevcut" else "Yeni Güncelleme Mevcut",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text("Yeni Sürüm: ${info.versionName}", color = Color(0xFF42A5F5), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (info.releaseNotes.isNotBlank()) {
                        Text("Yenilikler:", color = Color.Gray, fontSize = 12.sp)
                        Text(info.releaseNotes, color = Color.White, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (info.forceUpdate) {
                        Text("Devam etmek için uygulamayı güncellemeniz gerekmektedir.", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF42A5F5)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("İndiriliyor... %$downloadProgress", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            UpdateManager.downloadAndInstallApk(context, info.downloadUrl)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "İndiriliyor..." else "Güncelle", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (!info.forceUpdate && !isDownloading) {
                    TextButton(onClick = { UpdateManager.dismissUpdate() }) {
                        Text("Daha Sonra", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E1E2C)
        )
    }

    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { UpdateManager.clearMessages() },
            title = { Text("Bilgilendirme", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(errorMsg!!, color = Color.LightGray) },
            confirmButton = {
                Button(onClick = { UpdateManager.clearMessages() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))) {
                    Text("Tamam", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E2C)
        )
    }

    if (upToDateMsg != null) {
        AlertDialog(
            onDismissRequest = { UpdateManager.clearMessages() },
            title = { Text("Sürüm Kontrolü", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(upToDateMsg!!, color = Color.LightGray) },
            confirmButton = {
                Button(onClick = { UpdateManager.clearMessages() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5))) {
                    Text("Tamam", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E2C)
        )
    }
}
