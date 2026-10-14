package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
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
    val isDialogDismissed by PlaylistRepository.isDialogDismissed.collectAsState()
    val currentPlaylist by PlaylistRepository.playlist.collectAsState()

    // Otomatik kapatma: Kullanıcı asla bloklanmaz, veriler arka planda akmaya devam eder
    LaunchedEffect(isLoading, currentPlaylist.isNotEmpty()) {
        if (!isDialogDismissed) {
            PlaylistRepository.dismissLoadingDialog()
        }
    }
}
