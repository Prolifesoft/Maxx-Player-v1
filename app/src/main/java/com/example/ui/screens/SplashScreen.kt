@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
                    val (existingUser, hasPlaylists) = withContext(Dispatchers.IO) {
                        val user = db.iptvDao().getFirstUser()
                        when {
                            user != null && (user.email.isNullOrBlank() || user.email == "demo@fixekran.xyz" || user.id == "demo@fixekran.xyz") -> {
                                db.iptvDao().clearUsers()
                                Pair<com.example.model.db.UserEntity?, Boolean>(null, false)
                            }
                            user != null -> Pair<com.example.model.db.UserEntity?, Boolean>(
                                user,
                                db.iptvDao().getPlaylistsForUserSync(user.id).isNotEmpty()
                            )
                            else -> Pair<com.example.model.db.UserEntity?, Boolean>(null, false)
                        }
                    }
                    activity?.let { act ->
                        com.example.model.SettingsManager.applyOrientationToActivity(act)
                    }
                    if (existingUser != null) {
                        val target = if (hasPlaylists) com.example.ui.NavRoutes.PLAYLISTS
                                     else com.example.ui.NavRoutes.DEVICE_INFO
                        onNavigateToNext(target, existingUser.id)
                    } else {
                        onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                    }
                } catch (e: Exception) {
                    onNavigateToNext(com.example.ui.NavRoutes.GOOGLE_SIGN_IN, null)
                }
            }
        }
    }

    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    val playIntro = remember {
        INTRO_ENABLED && (!INTRO_ONLY_FIRST_LAUNCH || !prefs.getBoolean("intro_seen", false))
    }
    var introFailed by remember { mutableStateOf(!playIntro) }

    val introUri = remember {
        android.net.Uri.parse("android.resource://" + context.packageName + "/" + R.raw.intro3)
    }

    val introPlayer = remember(playIntro) {
        if (!playIntro) null else {
            try {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(introUri))
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
                if (state == Player.STATE_READY) {
                    if (INTRO_ONLY_FIRST_LAUNCH) {
                        prefs.edit().putBoolean("intro_seen", true).apply()
                    }
                }
                if (state == Player.STATE_ENDED) navigateNext()
            }
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("SplashScreen", "Intro playback error", error)
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
                    if (e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.Back)
                    ) { navigateNext(); true } else false
                }
                .clickable { navigateNext() }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = introPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            )
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
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(RedPrimary, Color(0xFFB71C1C))
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

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
