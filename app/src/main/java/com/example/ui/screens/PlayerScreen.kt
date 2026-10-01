@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.auth.findActivity
import com.example.model.CategoryManager
import com.example.model.EpgProgram
import com.example.model.EpgRepository
import com.example.model.PlayerRepository
import com.example.model.PlaylistRepository
import com.example.model.db.AppDatabase
import com.example.model.db.PlaybackProgressEntity
import com.example.R
import com.example.parser.ItemType
import com.example.parser.M3uItem
import com.example.ui.screens.player.PlayerBottomBar
import com.example.ui.screens.player.PlayerSettingsSheet
import com.example.ui.screens.player.PlayerSidePanel
import com.example.util.isTv
import androidx.activity.ComponentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity: Activity? = LocalActivity.current ?: context.findActivity()

    // Immersive Mode
    com.example.util.KeepSystemBarsHidden()

    val db = remember { AppDatabase.getDatabase(context) }
    var playingItem by remember { mutableStateOf(PlayerRepository.currentlyPlayingItem) }
    val allItems by PlaylistRepository.playlist.collectAsState()
    val hiddenCategories by CategoryManager.hiddenCategories.collectAsState()

    // Determine initial active list based on media type
    var activePlaylist by remember {
        val initialItem = PlayerRepository.currentlyPlayingItem
        val initialList = if (initialItem?.type == ItemType.LIVE) {
            val group = initialItem.group ?: ""
            val catChannels = allItems.filter { it.type == ItemType.LIVE && (it.group ?: "") == group }
            if (catChannels.isNotEmpty()) catChannels else PlayerRepository.currentPlaylist
        } else {
            PlayerRepository.currentPlaylist
        }
        mutableStateOf(initialList)
    }

    val epPrefix = stringResource(R.string.episode_dash_prefix)
    var showReportDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsInitialSubMenu by remember { mutableStateOf<String?>(null) }
    var isControllerVisible by remember { mutableStateOf(true) }

    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasSubtitles by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var epgNow by remember { mutableStateOf<EpgProgram?>(null) }
    var epgNext by remember { mutableStateOf<EpgProgram?>(null) }
    var currentResizeModeName by remember { mutableStateOf("Sığdır") }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var playlistPanelKeyHandler by remember { mutableStateOf<((AndroidKeyEvent) -> Boolean)?>(null) }
    var isBottomBarFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Request focus for D-Pad / Remote keys when panels or bottom bar close or screen opens
    LaunchedEffect(showPlaylistSheet, showSettingsSheet, isControllerVisible) {
        if (showPlaylistSheet || showSettingsSheet || !isControllerVisible) {
            isBottomBarFocused = false
        }
        if (!showPlaylistSheet && !showSettingsSheet && !isControllerVisible) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showPlaylistSheet, showSettingsSheet) {
        if (!showPlaylistSheet && !showSettingsSheet) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun createMediaItem(item: M3uItem, overrideMimeType: String? = null): MediaItem {
        val title = if (item.type == ItemType.SERIES) {
            (item.seriesName ?: "") + " $epPrefix${item.episode ?: "?"}: ${item.title}"
        } else {
            item.title
        }
        val cleanUrl = item.url.trim().let { u -> if (u.isNotEmpty() && !u.contains("://")) "http://$u" else u }
        val uri = Uri.parse(cleanUrl)

        val mimeType = overrideMimeType ?: run {
            val lower = cleanUrl.lowercase()
            val path = lower.substringBefore('?')
            when {
                path.endsWith(".m3u8") || lower.contains("output=hls") || lower.contains("output=m3u8") || lower.contains("type=m3u") || lower.contains("/hls/") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".ts") || lower.contains("output=mpegts") || lower.contains("output=ts") -> MimeTypes.VIDEO_MP2T
                path.endsWith(".mpd") || lower.contains("output=dash") -> MimeTypes.APPLICATION_MPD
                path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
                path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
                path.endsWith(".avi") -> MimeTypes.VIDEO_AVI
                path.endsWith(".mov") -> "video/quicktime"
                path.endsWith(".flv") -> "video/x-flv"
                path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
                item.type == ItemType.LIVE -> MimeTypes.VIDEO_MP2T
                else -> null
            }
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(cleanUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setDisplayTitle(title)
                    .build()
            )
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        return builder.build()
    }

    // Build ExoPlayer instance
    val exoPlayer = remember {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .setEnableAudioTrackPlaybackParams(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
            )
            .setTsExtractorTimestampSearchBytes(1500 * 188)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
            }
    }

    // D.5: Centralized playItem function
    fun playItem(item: M3uItem, list: List<M3uItem>) {
        val actualList = if (list.isNotEmpty()) list else listOf(item)
        activePlaylist = actualList
        PlayerRepository.currentPlaylist = actualList
        playingItem = item
        PlayerRepository.currentlyPlayingItem = item

        val validList = actualList.filter { it.url.isNotBlank() }
        if (validList.isNotEmpty()) {
            val mediaItems = validList.map { createMediaItem(it) }
            val startIndex = validList.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
            exoPlayer.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else if (item.url.isNotBlank()) {
            exoPlayer.setMediaItem(createMediaItem(item))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Initialize playback on mount
    LaunchedEffect(Unit) {
        val current = PlayerRepository.currentlyPlayingItem
        if (current != null) {
            val initialList = if (current.type == ItemType.LIVE) {
                val group = current.group ?: ""
                val catChannels = allItems.filter { it.type == ItemType.LIVE && (it.group ?: "") == group && !hiddenCategories.contains(it.group ?: "") }
                if (catChannels.isNotEmpty()) catChannels else PlayerRepository.currentPlaylist
            } else {
                PlayerRepository.currentPlaylist
            }
            playItem(current, initialList)
        }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        var retryCount = 0
        var liveRetryCount = 0
        var currentAttemptMimeType: String? = null

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                retryCount = 0
                liveRetryCount = 0
                currentAttemptMimeType = null

                // D.5: Find item using mediaId (= url) from activePlaylist, NOT by index
                val mediaId = mediaItem?.mediaId
                if (!mediaId.isNullOrBlank()) {
                    val found = activePlaylist.find { it.url == mediaId }
                    if (found != null) {
                        playingItem = found
                        PlayerRepository.currentlyPlayingItem = found
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                val current = playingItem
                val isLive = current?.type == ItemType.LIVE

                // FAZ E: BehindLiveWindow or Network/IO auto-retry up to 3 times
                val isBehindLive = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                val isNetworkOrIo = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS

                if (isLive && (isBehindLive || isNetworkOrIo) && liveRetryCount < 3) {
                    liveRetryCount++
                    scope.launch {
                        delay(2000)
                        if (isBehindLive) {
                            exoPlayer.seekToDefaultPosition()
                        }
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                    return
                }

                // Extractor format fallback
                val isExtractorError = error.cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException ||
                        (error.message ?: "").contains("None of the available extractors", ignoreCase = true) ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                        error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED

                if (current != null && isExtractorError && retryCount < 2) {
                    retryCount++
                    val fallbackMime = if (currentAttemptMimeType == MimeTypes.APPLICATION_M3U8) {
                        MimeTypes.VIDEO_MP2T
                    } else {
                        MimeTypes.APPLICATION_M3U8
                    }
                    currentAttemptMimeType = fallbackMime
                    val retryItem = createMediaItem(current, fallbackMime)
                    exoPlayer.setMediaItem(retryItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }

                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.player_playback_error),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_READY) {
                    liveRetryCount = 0
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(tracks: Tracks) {
                hasSubtitles = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // FAZ E: Lifecycle ON_PAUSE & ON_RESUME handling
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    wasPlaying = exoPlayer.isPlaying
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (wasPlaying) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Position tracking
    LaunchedEffect(playingItem?.url) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    // EPG loading (live only)
    LaunchedEffect(playingItem?.url) {
        val current = playingItem
        if (current != null && current.type == ItemType.LIVE) {
            val (now, next) = EpgRepository.getNowNext(current)
            epgNow = now
            epgNext = next
        } else {
            epgNow = null
            epgNext = null
        }
    }

    // EPG auto refresh when current program ends
    LaunchedEffect(epgNow, playingItem?.url) {
        val current = playingItem
        val now = epgNow
        if (current != null && current.type == ItemType.LIVE && now != null && now.endMs > 0) {
            val nowMs = System.currentTimeMillis()
            val delayMs = (now.endMs - nowMs).coerceAtLeast(1000L)
            delay(delayMs + 1000L)
            val (newNow, newNext) = EpgRepository.getNowNext(current)
            epgNow = newNow
            epgNext = newNext
        }
    }

    // Auto-hide controls after 4 seconds (unless panel or settings sheet is open)
    LaunchedEffect(isControllerVisible, showPlaylistSheet, showSettingsSheet, lastInteractionTime) {
        if (isControllerVisible && !showPlaylistSheet && !showSettingsSheet) {
            delay(4000)
            isControllerVisible = false
        }
    }

    // Resume playback for movie/series
    LaunchedEffect(playingItem?.url) {
        val currentItem = playingItem
        if (currentItem != null && (currentItem.type == ItemType.MOVIE || currentItem.type == ItemType.SERIES)) {
            val progress = db.iptvDao().getProgressForUrl(currentItem.url)
            if (progress != null && progress.positionMs > 0 && progress.positionMs < progress.durationMs - 10000) {
                exoPlayer.seekTo(progress.positionMs)
            }
        }
    }

    // Periodically save progress
    LaunchedEffect(playingItem?.url) {
        val currentItem = playingItem
        if (currentItem != null && (currentItem.type == ItemType.MOVIE || currentItem.type == ItemType.SERIES)) {
            while (true) {
                delay(5000)
                val position = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                if (position > 0 && duration > 0) {
                    val progress = PlaybackProgressEntity(
                        url = currentItem.url,
                        title = if (currentItem.type == ItemType.SERIES) {
                            currentItem.seriesName + "$epPrefix${currentItem.episode ?: "?"}: ${currentItem.title}"
                        } else {
                            currentItem.title
                        },
                        logo = currentItem.logo,
                        type = currentItem.type.name,
                        positionMs = position,
                        durationMs = duration
                    )
                    db.iptvDao().insertPlaybackProgress(progress)
                }
            }
        }
    }

    // Release player on unmount and save last position
    DisposableEffect(Unit) {
        onDispose {
            try {
                val currentItem = playingItem
                if (currentItem != null && (currentItem.type == ItemType.MOVIE || currentItem.type == ItemType.SERIES)) {
                    val position = exoPlayer.currentPosition
                    val duration = exoPlayer.duration
                    if (position > 0 && duration > 0) {
                        val progress = PlaybackProgressEntity(
                            url = currentItem.url,
                            title = if (currentItem.type == ItemType.SERIES) {
                                currentItem.seriesName + "$epPrefix${currentItem.episode ?: "?"}: ${currentItem.title}"
                            } else {
                                currentItem.title
                            },
                            logo = currentItem.logo,
                            type = currentItem.type.name,
                            positionMs = position,
                            durationMs = duration
                        )
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                db.iptvDao().insertPlaybackProgress(progress)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (_: Exception) {}
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    val isTvDevice = remember(context) { isTv(context) }

    // Back button handling hierarchy: dialog -> panel -> settings -> controls (TV) -> exit player
    BackHandler(enabled = true) {
        when {
            showReportDialog -> showReportDialog = false
            showPlaylistSheet -> showPlaylistSheet = false
            showSettingsSheet -> showSettingsSheet = false
            isTvDevice && isControllerVisible -> isControllerVisible = false
            else -> onBack()
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var showVolumeIndicator by remember { mutableStateOf(false) }

    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(showVolumeIndicator) {
        if (showVolumeIndicator) {
            delay(2000)
            showVolumeIndicator = false
        }
    }

    LaunchedEffect(showBrightnessIndicator) {
        if (showBrightnessIndicator) {
            delay(2000)
            showBrightnessIndicator = false
        }
    }

    // D.7: Root Box key listener for TV Remote / Keyboard (.onPreviewKeyEvent MUST be before .focusable())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode

                // Do NOT consume volume keys!
                if (keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP ||
                    keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN ||
                    keyCode == AndroidKeyEvent.KEYCODE_VOLUME_MUTE
                ) {
                    return@onPreviewKeyEvent false
                }

                // If side panel is open, route both KeyDown and KeyUp directly to side panel handler
                if (showPlaylistSheet) {
                    if (keyEvent.type == KeyEventType.KeyDown && keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                        showPlaylistSheet = false
                        return@onPreviewKeyEvent true
                    }
                    if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                        return@onPreviewKeyEvent true
                    }
                    if (playlistPanelKeyHandler?.invoke(keyEvent.nativeKeyEvent) == true) {
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // Consume KeyUp for center/enter when not focused on bottom bar so it doesn't leak
                if (keyEvent.type != KeyEventType.KeyDown) {
                    if (!showSettingsSheet && !isBottomBarFocused && (
                            keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
                        )
                    ) {
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // Back key hierarchy
                if (keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                    when {
                        showReportDialog -> {
                            showReportDialog = false
                            return@onPreviewKeyEvent true
                        }
                        showSettingsSheet -> {
                            showSettingsSheet = false
                            return@onPreviewKeyEvent true
                        }
                        isTvDevice && isControllerVisible -> {
                            isControllerVisible = false
                            return@onPreviewKeyEvent true
                        }
                        else -> {
                            onBack()
                            return@onPreviewKeyEvent true
                        }
                    }
                }

                // If settings sheet is open, let it process its keys
                if (showSettingsSheet) {
                    return@onPreviewKeyEvent false
                }

                val isLive = playingItem?.type == ItemType.LIVE

                // On any handled key, show controls for 4 seconds
                lastInteractionTime = System.currentTimeMillis()

                if (isLive) {
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                            if (activePlaylist.isNotEmpty()) {
                                val currIdx = activePlaylist.indexOfFirst { it.url == playingItem?.url }
                                val nextIdx = if (currIdx >= 0) (currIdx + 1) % activePlaylist.size else 0
                                playItem(activePlaylist[nextIdx], activePlaylist)
                            }
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                        AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (activePlaylist.isNotEmpty()) {
                                val currIdx = activePlaylist.indexOfFirst { it.url == playingItem?.url }
                                val prevIdx = if (currIdx >= 0) (currIdx - 1 + activePlaylist.size) % activePlaylist.size else 0
                                playItem(activePlaylist[prevIdx], activePlaylist)
                            }
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isControllerVisible && isBottomBarFocused) {
                                // Let focused bottom bar button (List / Settings / Subtitles) handle the click
                                false
                            } else {
                                if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                    showPlaylistSheet = true
                                    isControllerVisible = true
                                }
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            isControllerVisible = true
                            false
                        }
                        else -> false
                    }
                } else {
                    // Film / Dizi
                    when (keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER,
                        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (isControllerVisible && isBottomBarFocused) {
                                false
                            } else {
                                if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    isControllerVisible = true
                                }
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isControllerVisible = true
                            }
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isControllerVisible && isBottomBarFocused) {
                                false
                            } else {
                                exoPlayer.seekBack()
                                isControllerVisible = true
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                            exoPlayer.seekBack()
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (isControllerVisible && isBottomBarFocused) {
                                false
                            } else {
                                exoPlayer.seekForward()
                                isControllerVisible = true
                                true
                            }
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            exoPlayer.seekForward()
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT -> {
                            val currIdx = activePlaylist.indexOfFirst { it.url == playingItem?.url }
                            if (currIdx >= 0 && currIdx + 1 < activePlaylist.size) {
                                playItem(activePlaylist[currIdx + 1], activePlaylist)
                            }
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            val currIdx = activePlaylist.indexOfFirst { it.url == playingItem?.url }
                            if (currIdx > 0) {
                                playItem(activePlaylist[currIdx - 1], activePlaylist)
                            }
                            isControllerVisible = true
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP,
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                            isControllerVisible = true
                            false // Let focus pass to bottom bar buttons
                        }
                        else -> false
                    }
                }
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Unified gesture layer for phone/tablet or non-focusable tap listener for TV
        val gestureModifier = if (!isTvDevice) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val isLeftEdge = down.position.x <= w * 0.2f
                    val isRightEdge = down.position.x >= w * 0.8f

                    var totalDragY = 0f
                    var hasMoved = false
                    var isDrag = false
                    val touchSlop = viewConfiguration.touchSlop

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (!hasMoved) {
                                isControllerVisible = !isControllerVisible
                                lastInteractionTime = System.currentTimeMillis()
                            } else {
                                showVolumeIndicator = false
                                showBrightnessIndicator = false
                            }
                            break
                        }

                        val deltaY = change.position.y - change.previousPosition.y
                        totalDragY += deltaY

                        if (kotlin.math.abs(totalDragY) > touchSlop) {
                            hasMoved = true
                            if (isLeftEdge || isRightEdge) {
                                isDrag = true
                                if (isLeftEdge) showVolumeIndicator = true
                                if (isRightEdge) showBrightnessIndicator = true
                            }
                        }

                        if (isDrag) {
                            change.consume()
                            val dragFraction = -deltaY / size.height.toFloat()
                            if (isLeftEdge) {
                                currentVolume = (currentVolume + dragFraction).coerceIn(0f, 1f)
                                try {
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (currentVolume * maxVolume).roundToInt(),
                                        0
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                showVolumeIndicator = true
                            } else if (isRightEdge) {
                                currentBrightness = (currentBrightness + dragFraction).coerceIn(0.01f, 1f)
                                activity?.let { act ->
                                    val attrs = act.window.attributes
                                    attrs.screenBrightness = currentBrightness
                                    act.window.attributes = attrs
                                }
                                showBrightnessIndicator = true
                            }
                        }
                    }
                }
            }
        } else {
            Modifier.pointerInput(Unit) {
                detectTapGestures {
                    isControllerVisible = !isControllerVisible
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            // PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        resizeMode = currentResizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    pv.player = exoPlayer
                    pv.resizeMode = currentResizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Buffering progress indicator (centered)
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp
                )
            }

            // Volume Indicator (Left)
            AnimatedVisibility(
                visible = showVolumeIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = stringResource(R.string.player_volume),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${(currentVolume * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Brightness Indicator (Right)
            AnimatedVisibility(
                visible = showBrightnessIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BrightnessMedium,
                            contentDescription = stringResource(R.string.player_brightness),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${(currentBrightness * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Alt Bilgi Çubuğu (4 sn sonra otomatik gizlenir)
            AnimatedVisibility(
                visible = isControllerVisible && !showPlaylistSheet && !showSettingsSheet,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerBottomBar(
                    item = playingItem,
                    isPlaying = isPlaying,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    hasSubtitles = hasSubtitles,
                    epgNow = epgNow,
                    epgNext = epgNext,
                    epPrefix = epPrefix,
                    onPlayPauseClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onSeekBackClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        exoPlayer.seekBack()
                    },
                    onSeekForwardClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        exoPlayer.seekForward()
                    },
                    onSeekTo = { posMs ->
                        lastInteractionTime = System.currentTimeMillis()
                        exoPlayer.seekTo(posMs)
                    },
                    onSubtitleClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        settingsInitialSubMenu = "Altyazılar"
                        showSettingsSheet = true
                    },
                    onListClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        showPlaylistSheet = true
                    },
                    onSettingsClick = {
                        lastInteractionTime = System.currentTimeMillis()
                        settingsInitialSubMenu = null
                        showSettingsSheet = true
                    },
                    onBarFocusChanged = { focused ->
                        isBottomBarFocused = focused
                    }
                )
            }
        }

        // Kategori + Kanal / Bölüm Paneli (Sol taraf)
        PlayerSidePanel(
            isOpen = showPlaylistSheet,
            playingItem = playingItem,
            epPrefix = epPrefix,
            onClose = { showPlaylistSheet = false },
            onPlayItem = { newItem, newActiveList ->
                playItem(newItem, newActiveList)
                showPlaylistSheet = false
            },
            onRegisterKeyHandler = { handler ->
                playlistPanelKeyHandler = handler
            }
        )

        // Ayarlar Menüsü (YouTube tarzı, D-pad ile gezilebilir)
        PlayerSettingsSheet(
            isOpen = showSettingsSheet,
            initialSubMenu = settingsInitialSubMenu,
            exoPlayer = exoPlayer,
            isLive = playingItem?.type == ItemType.LIVE,
            currentResizeModeName = currentResizeModeName,
            onResizeModeChange = { name ->
                currentResizeModeName = name
                currentResizeMode = when (name) {
                    context.getString(R.string.player_aspect_fill) -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    context.getString(R.string.player_aspect_zoom) -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onReportIssueClick = {},
            onClose = { showSettingsSheet = false }
        )
    }
}
