package com.example.ui.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.example.R
import com.example.auth.findActivity
import com.example.util.isTv
import kotlin.math.roundToInt

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

data class VideoTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val label: String,
    val isSelected: Boolean
)

data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean
)

@Composable
fun PlayerSettingsSheet(
    isOpen: Boolean,
    initialSubMenu: String? = null,
    exoPlayer: ExoPlayer,
    isLive: Boolean,
    currentResizeModeName: String,
    onResizeModeChange: (String) -> Unit,
    onReportIssueClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val context = LocalContext.current
    val activity: Activity? = LocalActivity.current ?: context.findActivity()
    val isTvDevice = remember(context) { isTv(context) }

    var selectedSubMenu by remember(isOpen, initialSubMenu) { mutableStateOf(initialSubMenu) }

    BackHandler(enabled = isOpen) {
        if (selectedSubMenu != null) {
            selectedSubMenu = null
        } else {
            onClose()
        }
    }

    // Tracks state tracked via listener
    var tracksState by remember { mutableStateOf(exoPlayer.currentTracks) }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                tracksState = tracks
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Video tracks (resolutions)
    val videoTracks = remember(tracksState) {
        val list = mutableListOf<VideoTrackInfo>()
        for (gIdx in 0 until tracksState.groups.size) {
            val group = tracksState.groups[gIdx]
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val h = format.height
                    val lbl = if (h > 0) "${h}p" else format.label ?: "Kalite #${tIdx + 1}"
                    list.add(
                        VideoTrackInfo(
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            height = h,
                            label = lbl,
                            isSelected = group.isTrackSelected(tIdx)
                        )
                    )
                }
            }
        }
        list.distinctBy { it.label }.sortedByDescending { it.height }
    }

    val hasVideoOverrides = remember(exoPlayer.trackSelectionParameters) {
        exoPlayer.trackSelectionParameters.overrides.any { it.key.type == C.TRACK_TYPE_VIDEO }
    }

    val selectedVideoQualityLabel = remember(videoTracks, hasVideoOverrides) {
        if (!hasVideoOverrides) {
            "Otomatik"
        } else {
            videoTracks.find { it.isSelected }?.label ?: "Otomatik"
        }
    }

    // Audio tracks
    val audioTracks = remember(tracksState) {
        val list = mutableListOf<AudioTrackInfo>()
        var audioCounter = 1
        for (gIdx in 0 until tracksState.groups.size) {
            val group = tracksState.groups[gIdx]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val lang = format.language?.uppercase()?.takeIf { it.isNotBlank() }
                    val label = format.label?.takeIf { it.isNotBlank() } ?: lang ?: "Ses #$audioCounter"
                    list.add(
                        AudioTrackInfo(
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            label = label,
                            isSelected = group.isTrackSelected(tIdx)
                        )
                    )
                    audioCounter++
                }
            }
        }
        list
    }

    // Subtitle tracks
    val subtitleTracks = remember(tracksState) {
        val list = mutableListOf<SubtitleTrackInfo>()
        var subCounter = 1
        for (gIdx in 0 until tracksState.groups.size) {
            val group = tracksState.groups[gIdx]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val lang = format.language?.uppercase()?.takeIf { it.isNotBlank() }
                    val label = format.label?.takeIf { it.isNotBlank() } ?: lang ?: "Altyazı #$subCounter"
                    list.add(
                        SubtitleTrackInfo(
                            groupIndex = gIdx,
                            trackIndex = tIdx,
                            label = label,
                            isSelected = group.isTrackSelected(tIdx)
                        )
                    )
                    subCounter++
                }
            }
        }
        list
    }

    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var currentSpeed by remember { mutableFloatStateOf(exoPlayer.playbackParameters.speed) }

    val resizeOptions = listOf(
        stringResource(R.string.player_aspect_fit),
        stringResource(R.string.player_aspect_fill),
        stringResource(R.string.player_aspect_zoom)
    )

    // Volume & Brightness for phone/tablet
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var currentBrightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f)
    }

    val sheetWidth = if (isTvDevice) 420.dp else 360.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 15.dp, bottom = 15.dp)
                .width(sheetWidth)
                .background(
                    Color(0xFF13131D),
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                )
                .clickable(enabled = false) {}
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectedSubMenu != null) {
                        IconButton(
                            onClick = { selectedSubMenu = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = selectedSubMenu ?: stringResource(R.string.player_settings_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_desc), tint = Color.Gray)
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Content
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedSubMenu == null) {
                    // 1. Görüntü Kalitesi (Video Track)
                    item {
                        val hasMultipleQuality = videoTracks.size > 1
                        TvFocusableSettingsRow(
                            icon = Icons.Default.HighQuality,
                            title = stringResource(R.string.player_quality),
                            subtitle = if (hasMultipleQuality) selectedVideoQualityLabel else stringResource(R.string.player_single_quality),
                            enabled = hasMultipleQuality,
                            onClick = {
                                if (hasMultipleQuality) {
                                    selectedSubMenu = "Kalite"
                                }
                            }
                        )
                    }

                    // 2. Ses İzi (Audio Track)
                    item {
                        val hasMultipleAudio = audioTracks.size > 1
                        val activeAudioLabel = audioTracks.find { it.isSelected }?.label ?: stringResource(R.string.player_default_audio)
                        TvFocusableSettingsRow(
                            icon = Icons.Default.Audiotrack,
                            title = stringResource(R.string.player_audio_track),
                            subtitle = if (hasMultipleAudio) activeAudioLabel else stringResource(R.string.player_single_audio),
                            enabled = hasMultipleAudio,
                            onClick = {
                                if (hasMultipleAudio) {
                                    selectedSubMenu = "Ses İzi"
                                }
                            }
                        )
                    }

                    // 3. Altyazı (Subtitles) - Parça yoksa satırı GİZLE
                    if (subtitleTracks.isNotEmpty()) {
                        item {
                            val activeSubtitle = subtitleTracks.find { it.isSelected }
                            val subLabel = activeSubtitle?.label ?: stringResource(R.string.player_subtitles_off)
                            TvFocusableSettingsRow(
                                icon = Icons.Default.ClosedCaption,
                                title = stringResource(R.string.player_subtitles),
                                subtitle = subLabel,
                                enabled = true,
                                onClick = { selectedSubMenu = "Altyazılar" }
                            )
                        }
                    }

                    // 4. Oynatma Hızı (Sadece film/dizi, canlıda gizli)
                    if (!isLive) {
                        item {
                            TvFocusableSettingsRow(
                                icon = Icons.Default.Speed,
                                title = stringResource(R.string.player_playback_speed),
                                subtitle = "${currentSpeed}x",
                                enabled = true,
                                onClick = { selectedSubMenu = "Oynatma Hızı" }
                            )
                        }
                    }

                    // 5. Ekran Oranı (Fit / Fill / Zoom)
                    item {
                        TvFocusableSettingsRow(
                            icon = Icons.Default.AspectRatio,
                            title = stringResource(R.string.player_aspect_ratio),
                            subtitle = currentResizeModeName,
                            enabled = true,
                            onClick = { selectedSubMenu = "Ekran Oranı" }
                        )
                    }

                    // 6 & 7. Parlaklık ve Ses Kaydırıcıları (Sadece telefon/tablet, isTv değilse)
                    if (!isTvDevice) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E2A))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.player_brightness), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Text("${(currentBrightness * 100).roundToInt()}%", color = Color.Gray, fontSize = 12.sp)
                                }
                                Slider(
                                    value = currentBrightness.coerceIn(0.01f, 1f),
                                    onValueChange = { br ->
                                        currentBrightness = br
                                        activity?.let { act ->
                                            val attrs = act.window.attributes
                                            attrs.screenBrightness = br
                                            act.window.attributes = attrs
                                        }
                                    },
                                    valueRange = 0.01f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E2A))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.player_volume), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Text("${(currentVolume * 100).roundToInt()}%", color = Color.Gray, fontSize = 12.sp)
                                }
                                Slider(
                                    value = currentVolume.coerceIn(0f, 1f),
                                    onValueChange = { vol ->
                                        currentVolume = vol
                                        try {
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                (vol * maxVolume).roundToInt(),
                                                0
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                } else when (selectedSubMenu) {
                    "Kalite" -> {
                        // Otomatik Seçeneği
                        item {
                            TvSelectableItemRow(
                                title = stringResource(R.string.player_quality_auto),
                                isSelected = !hasVideoOverrides,
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .build()
                                    selectedSubMenu = null
                                }
                            )
                        }

                        items(videoTracks.size) { idx ->
                            val track = videoTracks[idx]
                            val isSelected = hasVideoOverrides && track.isSelected
                            TvSelectableItemRow(
                                title = track.label,
                                isSelected = isSelected,
                                onClick = {
                                    val group = tracksState.groups[track.groupIndex]
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                track.trackIndex
                                            )
                                        )
                                        .build()
                                    selectedSubMenu = null
                                }
                            )
                        }
                    }

                    "Altyazılar" -> {
                        // Altyazı Kapat seçeneği
                        val isNoneSelected = subtitleTracks.none { it.isSelected }
                        item {
                            TvSelectableItemRow(
                                title = stringResource(R.string.player_subtitles_off),
                                isSelected = isNoneSelected,
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                    selectedSubMenu = null
                                }
                            )
                        }

                        items(subtitleTracks.size) { idx ->
                            val track = subtitleTracks[idx]
                            TvSelectableItemRow(
                                title = track.label,
                                isSelected = track.isSelected,
                                onClick = {
                                    val group = tracksState.groups[track.groupIndex]
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                track.trackIndex
                                            )
                                        )
                                        .build()
                                    selectedSubMenu = null
                                }
                            )
                        }
                    }

                    "Ses İzi" -> {
                        items(audioTracks.size) { idx ->
                            val track = audioTracks[idx]
                            TvSelectableItemRow(
                                title = track.label,
                                isSelected = track.isSelected,
                                onClick = {
                                    val group = tracksState.groups[track.groupIndex]
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                track.trackIndex
                                            )
                                        )
                                        .build()
                                    selectedSubMenu = null
                                }
                            )
                        }
                    }

                    "Oynatma Hızı" -> {
                        items(speedOptions.size) { idx ->
                            val spd = speedOptions[idx]
                            TvSelectableItemRow(
                                title = "${spd}x" + if (spd == 1.0f) " (Normal)" else "",
                                isSelected = currentSpeed == spd,
                                onClick = {
                                    currentSpeed = spd
                                    exoPlayer.setPlaybackSpeed(spd)
                                    selectedSubMenu = null
                                }
                            )
                        }
                    }

                    "Ekran Oranı" -> {
                        items(resizeOptions.size) { idx ->
                            val opt = resizeOptions[idx]
                            TvSelectableItemRow(
                                title = opt,
                                isSelected = currentResizeModeName == opt,
                                onClick = {
                                    onResizeModeChange(opt)
                                    selectedSubMenu = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvFocusableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else Color(0xFF1E1E2A)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(text = subtitle, color = Color.Gray, fontSize = 12.sp)
        }
        if (enabled) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TvSelectableItemRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else -> Color(0xFF1E1E2A)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Seçili", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
