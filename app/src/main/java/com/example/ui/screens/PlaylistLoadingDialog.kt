package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PlaylistLoadStep
import com.example.model.PlaylistRepository

@Composable
fun PlaylistLoadingDialog() {
    val isLoading by PlaylistRepository.isLoading.collectAsState()
    val progress by PlaylistRepository.loadingProgress.collectAsState()
    val detail by PlaylistRepository.loadingDetail.collectAsState()
    val step by PlaylistRepository.loadStep.collectAsState()
    val error by PlaylistRepository.error.collectAsState()

    if (isLoading) {
        com.example.util.KeepSystemBarsHidden()
        BackHandler(enabled = true) { /* Prevent dismiss while loading */ }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks while loading */ }
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E1E1E),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.loading_playlist_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                        if (progress <= 0 && step == PlaylistLoadStep.CONNECTING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(80.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 8.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.size(80.dp),
                                color = if (step == PlaylistLoadStep.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                strokeWidth = 8.dp,
                                trackColor = Color(0xFF333333)
                            )
                        }
                        
                        Text(
                            text = "%$progress",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    LinearProgressIndicator(
                        progress = progress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(Color(0xFF333333), RoundedCornerShape(4.dp)),
                        color = if (step == PlaylistLoadStep.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF333333)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val statusText = when (step) {
                        PlaylistLoadStep.CONNECTING -> stringResource(R.string.loading_connecting)
                        PlaylistLoadStep.DOWNLOADING -> if (detail.isNotBlank()) "${stringResource(R.string.loading_downloading)} ($detail)" else stringResource(R.string.loading_downloading)
                        PlaylistLoadStep.PROCESSING -> if (detail.isNotBlank()) "${stringResource(R.string.loading_processing)} ($detail)" else stringResource(R.string.loading_processing)
                        PlaylistLoadStep.COMPLETED -> stringResource(R.string.loading_completed)
                        PlaylistLoadStep.ERROR -> "${stringResource(R.string.loading_error)} ${error ?: ""}"
                        PlaylistLoadStep.IDLE -> ""
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 14.sp,
                        color = if (step == PlaylistLoadStep.ERROR) MaterialTheme.colorScheme.error else Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontWeight = if (step == PlaylistLoadStep.COMPLETED) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
