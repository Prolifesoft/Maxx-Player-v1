package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.EpgProgram
import com.example.R
import com.example.parser.ItemType
import com.example.parser.M3uItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomBar(
    item: M3uItem?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    hasSubtitles: Boolean,
    epgNow: EpgProgram?,
    epgNext: EpgProgram?,
    epPrefix: String,
    onPlayPauseClick: () -> Unit,
    onSeekBackClick: () -> Unit,
    onSeekForwardClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSubtitleClick: () -> Unit,
    onListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBarFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLive = item?.type == ItemType.LIVE

    // Format start - end times for EPG
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val nowTimeStr = remember(epgNow) {
        if (epgNow != null && epgNow.startMs > 0 && epgNow.endMs > 0) {
            val s = timeFormat.format(Date(epgNow.startMs))
            val e = timeFormat.format(Date(epgNow.endMs))
            "$s–$e"
        } else ""
    }

    val nextTimeStr = remember(epgNext) {
        if (epgNext != null && epgNext.startMs > 0) {
            timeFormat.format(Date(epgNext.startMs))
        } else ""
    }

    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isLive, epgNow) {
        if (isLive && epgNow != null) {
            while (true) {
                currentTimeMs = System.currentTimeMillis()
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    val epgProgress = remember(epgNow, currentTimeMs) {
        if (epgNow != null && epgNow.endMs > epgNow.startMs) {
            ((currentTimeMs - epgNow.startMs).toFloat() / (epgNow.endMs - epgNow.startMs)).coerceIn(0f, 1f)
        } else 0f
    }

    // Title formatting
    val title = remember(item, epPrefix) {
        when (item?.type) {
            ItemType.LIVE -> item.title
            ItemType.SERIES -> {
                val series = item.seriesName ?: ""
                val ep = item.episode?.let { "$epPrefix$it: " } ?: ""
                "$series$ep${item.title}"
            }
            ItemType.MOVIE -> item.title
            else -> item?.title ?: ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onBarFocusChanged(it.hasFocus) }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.6f),
                        Color.Black.copy(alpha = 0.95f)
                    )
                )
            )
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 26.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Seek bar for Movie / Series above the main bottom row
            if (!isLive && durationMs > 0) {
                var sliderSeekingValue by remember { mutableStateOf<Float?>(null) }
                val displayPosition = sliderSeekingValue ?: currentPositionMs.toFloat()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatPlayerDuration(displayPosition.toLong()),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = displayPosition.coerceIn(0f, durationMs.toFloat()),
                        onValueChange = { sliderSeekingValue = it },
                        onValueChangeFinished = {
                            sliderSeekingValue?.let { onSeekTo(it.toLong()) }
                            sliderSeekingValue = null
                        },
                        valueRange = 0f..durationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        )
                    )

                    Text(
                        text = formatPlayerDuration(durationMs),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol Bölüm: Logo (48dp) + Başlık + EPG (Canlı)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    var logoLoadFailed by remember(item?.logo) { mutableStateOf(false) }
                    if (!item?.logo.isNullOrBlank() && !logoLoadFailed) {
                        AsyncImage(
                            model = item?.logo,
                            contentDescription = item?.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF080F19)),
                            contentScale = ContentScale.Fit,
                            placeholder = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                            error = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                            onError = { logoLoadFailed = true }
                        )
                    } else {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                            contentDescription = item?.title,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF080F19)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // EPG satırları (sadece canlıda ve veri varsa)
                        if (isLive && (epgNow != null || epgNext != null)) {
                            if (epgNow != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                val liveNowStr = stringResource(R.string.player_live_now)
                                Text(
                                    text = "$liveNowStr: ${epgNow.title} $nowTimeStr",
                                    color = Color(0xFFE0E0E0),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                LinearProgressIndicator(
                                    progress = { epgProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                            if (epgNext != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                val liveNextStr = stringResource(R.string.player_live_next)
                                Text(
                                    text = "$liveNextStr: ${epgNext.title} $nextTimeStr",
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Sağ Bölüm: SADECE belirtilen ikonlar (TvFocusableIconButton)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Film/dizi için: Geri 10sn, Oynat/Duraklat, İleri 10sn (Canlıda GÖSTERİLMEZ)
                    if (!isLive) {
                        TvFocusableIconButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "10 sn geri",
                            iconSize = 28.dp,
                            onClick = onSeekBackClick
                        )

                        TvFocusableIconButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else stringResource(R.string.play),
                            iconSize = 32.dp,
                            onClick = onPlayPauseClick
                        )

                        TvFocusableIconButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "10 sn ileri",
                            iconSize = 28.dp,
                            onClick = onSeekForwardClick
                        )
                    }

                    // Altyazı (CC) - Altyazı parçası yoksa HİÇ GÖSTERİLMEZ
                    if (hasSubtitles) {
                        TvFocusableIconButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = stringResource(R.string.player_subtitles),
                            iconSize = 26.dp,
                            onClick = onSubtitleClick
                        )
                    }

                    // Liste (canlıda kategori+kanal paneli, dizide bölüm listesi, filmde film listesi)
                    TvFocusableIconButton(
                        icon = Icons.Default.List,
                        contentDescription = stringResource(R.string.player_channel_list),
                        iconSize = 28.dp,
                        onClick = onListClick
                    )

                    // Ayarlar (dişli)
                    TvFocusableIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        iconSize = 26.dp,
                        onClick = onSettingsClick
                    )
                }
            }
        }
    }
}

@Composable
fun TvFocusableIconButton(
    icon: ImageVector,
    contentDescription: String?,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

fun formatPlayerDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
