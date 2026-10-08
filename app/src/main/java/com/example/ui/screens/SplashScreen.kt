@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.auth.findActivity
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INTRO_ENABLED = true
private const val INTRO_ONLY_FIRST_LAUNCH = false

@Composable
fun SplashScreen(
    onNavigateToNext: (targetRoute: String, existingUserId: String?) -> Unit
) {
    val context = LocalContext.current
    val activity = (context as? android.app.Activity)
        ?: runCatching { LocalActivity.current }.getOrNull()
        ?: context.findActivity()
    var hasNavigated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Stage 2: Preload last used or default playlist while splash intro plays
    LaunchedEffect(Unit) {
        try {
            val targetUrl = com.example.model.PlaylistRepository.resolveLastOrFirstPlaylistUrl(context)
            if (!targetUrl.isNullOrBlank()) {
                com.example.model.PlaylistRepository.loadPlaylist(context, targetUrl)
            }
        } catch (_: Exception) {}
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        // Ignore or skip intro safely without popping the root NavHost destination
    }

    // Smooth pulse animation for logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    fun navigateNext() {
        if (!hasNavigated) {
            hasNavigated = true
            scope.launch {
                try {
                    val db = com.example.model.db.AppDatabase.getDatabase(context)
                    val (existingUser, targetPlaylistUrl) = withContext(Dispatchers.IO) {
                        val user = db.iptvDao().getFirstUser()
                        when {
                            user != null && (user.email.isNullOrBlank() || user.email == "demo@fixekran.xyz" || user.id == "demo@fixekran.xyz") -> {
                                db.iptvDao().clearUsers()
                                Pair<com.example.model.db.UserEntity?, String?>(null, null)
                            }
                            user != null -> Pair<com.example.model.db.UserEntity?, String?>(
                                user,
                                com.example.model.PlaylistRepository.resolveLastOrFirstPlaylistUrl(context, user.id)
                            )
                            else -> Pair<com.example.model.db.UserEntity?, String?>(null, null)
                        }
                    }
                    activity?.let { act ->
                        com.example.model.SettingsManager.applyOrientationToActivity(act)
                    }
                    if (existingUser != null) {
                        if (!targetPlaylistUrl.isNullOrBlank()) {
                            if (com.example.model.PlaylistRepository.playlist.value.isEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        com.example.model.PlaylistRepository.loadPlaylist(context, targetPlaylistUrl)
                                    } catch (_: Exception) {}
                                }
                            }
                            onNavigateToNext(com.example.ui.NavRoutes.DASHBOARD, existingUser.id)
                        } else {
                            onNavigateToNext(com.example.ui.NavRoutes.DEVICE_INFO, existingUser.id)
                        }
                    } else {
                        onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                    }
                } catch (e: Exception) {
                    onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                }
            }
        }
    }

    val playIntro = INTRO_ENABLED
    var introFailed by remember { mutableStateOf(false) }

    val introPlayer = remember(playIntro) {
        if (!playIntro) null else {
            try {
                // Pre-extract or verify local cache file for maximum compatibility across emulators and devices
                val cacheFile = java.io.File(context.cacheDir, "intro_cached.mp4")
                try {
                    val rawFd = runCatching { context.resources.openRawResourceFd(R.raw.intro3) }.getOrNull()
                    val rawLength = rawFd?.length ?: -1L
                    rawFd?.close()
                    // If cache does not exist, is empty, or size differs from the raw resource (e.g. user replaced intro3.mp4)
                    if (!cacheFile.exists() || cacheFile.length() == 0L || (rawLength > 0L && cacheFile.length() != rawLength)) {
                        context.resources.openRawResource(R.raw.intro3).use { input ->
                            java.io.FileOutputStream(cacheFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SplashScreen", "Could not copy raw resource to cache", e)
                }

                val mediaUri = if (cacheFile.exists() && cacheFile.length() > 0L) {
                    android.net.Uri.fromFile(cacheFile)
                } else {
                    RawResourceDataSource.buildRawResourceUri(R.raw.intro3)
                }

                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(mediaUri))
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    prepare()
                }
            } catch (e: Exception) {
                android.util.Log.e("SplashScreen", "ExoPlayer initialization failed", e)
                introFailed = true
                null
            }
        }
    }

    DisposableEffect(introPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    navigateNext()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("SplashScreen", "Intro playback error, navigating next", error)
                introFailed = true
                navigateNext()
            }
        }
        introPlayer?.addListener(listener)
        onDispose {
            introPlayer?.removeListener(listener)
            introPlayer?.release()
        }
    }

    // Safety watchdog: Automatically transitions as soon as video finishes or after timeout
    LaunchedEffect(introPlayer) {
        if (introPlayer != null) {
            val startTime = System.currentTimeMillis()
            while (!hasNavigated) {
                delay(200)
                try {
                    val duration = introPlayer.duration
                    val position = introPlayer.currentPosition
                    if (duration > 0 && position >= duration - 200) {
                        navigateNext()
                        break
                    }
                    if (System.currentTimeMillis() - startTime > 15_000) {
                        navigateNext()
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try { com.example.model.DeviceManager.init(context) } catch (e: Exception) { }
        }
        if (!playIntro) {
            delay(900)
            navigateNext()
        }
    }

    com.example.util.KeepSystemBarsHidden()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    if (!introFailed && introPlayer != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        navigateNext()
                        true
                    } else false
                }
                .clickable { navigateNext() }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = introPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    if (pv.player != introPlayer) {
                        pv.player = introPlayer
                    }
                }
            )

            var showSkipHint by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(600)
                showSkipHint = true
            }
            if (showSkipHint) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { navigateNext() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Geç  ▶",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF020617)
                        )
                    )
                )
                .clickable { navigateNext() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.img_app_icon),
                    contentDescription = "MAXX PLAYER",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(104.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(24.dp))
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "MAXX PLAYER",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Maxx Players Güvencesiyle",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                CircularProgressIndicator(
                    color = RedPrimary,
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 2.5.dp
                )
            }
        }
    }
}
