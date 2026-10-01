package com.example

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.PlaylistRepository
import com.example.model.db.AppDatabase
import com.example.ui.NavRoutes
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.DarkBackground
import com.example.util.applyImmersiveFullscreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.applyImmersiveFullscreen()
        super.onCreate(savedInstanceState)

        // Sistem çubuklarını UYGULAMA AÇILIR AÇILMAZ kalıcı olarak gizle
        window.applyImmersiveFullscreen()

        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ||
                (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
            ) {
                window.applyImmersiveFullscreen()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, _ ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            WindowInsetsCompat.CONSUMED
        }

        try {
            com.example.auth.GoogleAuthManager.currentActivity = java.lang.ref.WeakReference(this)
            com.example.model.SettingsManager.init(this)
            com.example.model.SettingsManager.applyOrientationToActivity(this)
            com.example.model.AppLanguageManager.init(this)
            com.example.model.ParentalControlManager.init(this)
            com.example.model.FavoritesManager.init(this)
            com.example.model.CategoryManager.init(this)
            com.example.model.DeviceManager.init(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during initialization", e)
        }

        setContent {
            val language by com.example.model.AppLanguageManager.currentLanguage.collectAsState()
            val orientationMode by com.example.model.SettingsManager.orientationMode.collectAsState()
            
            LaunchedEffect(orientationMode) {
                com.example.model.SettingsManager.applyOrientationToActivity(this@MainActivity)
                window.applyImmersiveFullscreen()
            }
            
            val navController = rememberNavController()
            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, _, _ ->
                    window.applyImmersiveFullscreen()
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose {
                    navController.removeOnDestinationChangedListener(listener)
                }
            }
            var currentUserId by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            var lastRootBackPressTime by remember { mutableLongStateOf(0L) }

            LaunchedEffect(currentUserId) {
                if (currentUserId.isNotBlank()) {
                    val db = AppDatabase.getDatabase(this@MainActivity)
                    val user = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        db.iptvDao().getUser(currentUserId)
                    }
                    if (user != null) {
                        com.example.model.OdooIntegrationManager.registerCustomerAndTrial(
                            context = this@MainActivity,
                            userId = user.id,
                            userName = user.name ?: "Kullanıcı",
                            userEmail = user.email ?: ""
                        )
                    }
                }
            }

            val handleRootExit: () -> Unit = {
                val now = System.currentTimeMillis()
                if (now - lastRootBackPressTime < 2500) {
                    finish()
                } else {
                    lastRootBackPressTime = now
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.press_again_to_exit),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
            com.example.ui.ProvideAppLocale(language) {
                MyApplicationTheme {
                    Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(navController = navController, startDestination = NavRoutes.SPLASH) {
                            composable(NavRoutes.SPLASH) {
                                SplashScreen(onNavigateToNext = { targetRoute, existingUserId ->
                                    if (!existingUserId.isNullOrEmpty()) {
                                        currentUserId = existingUserId
                                    }
                                    navController.navigate(targetRoute) {
                                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                })
                            }
                            composable(NavRoutes.GOOGLE_SIGN_IN) {
                                GoogleSignInScreen(
                                    onSignInSuccess = { userId ->
                                        currentUserId = userId
                                        navController.navigate(NavRoutes.DEVICE_INFO) {
                                            popUpTo(NavRoutes.GOOGLE_SIGN_IN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            handleRootExit()
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.DEVICE_INFO) {
                                DeviceInfoScreen(
                                    userId = currentUserId,
                                    firstSetup = true,
                                    onContinue = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            val playlists = withContext(Dispatchers.IO) {
                                                if (currentUserId.isNotBlank()) {
                                                    db.iptvDao().getPlaylistsForUserSync(currentUserId)
                                                } else {
                                                    db.iptvDao().getAllPlaylistsSync()
                                                }
                                            }
                                            if (playlists.isNotEmpty()) {
                                                val first = playlists.first()
                                                val urlToLoad = if (first.username.isEmpty() && first.password.isEmpty()) {
                                                    first.hostUrl
                                                } else {
                                                    "${first.hostUrl}/get.php?username=${first.username}&password=${first.password}&type=m3u_plus&output=mpegts"
                                                }
                                                PlaylistRepository.loadPlaylist(this@MainActivity, urlToLoad)
                                                navController.navigate(NavRoutes.DASHBOARD) {
                                                    popUpTo(NavRoutes.DEVICE_INFO) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            } else {
                                                navController.navigate(NavRoutes.PLAYLISTS) {
                                                    popUpTo(NavRoutes.DEVICE_INFO) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    },
                                    onNavigateToPackageSelection = {
                                        val safeUid = currentUserId.ifBlank {
                                            com.example.model.DeviceManager.getCurrentUserId()
                                                ?: com.example.model.DeviceManager.getDeviceId().ifBlank { "default" }
                                        }
                                        navController.navigate("${NavRoutes.PACKAGE_SELECTION}/${Uri.encode(safeUid)}")
                                    },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.WELCOME) {
                                                popUpTo(NavRoutes.DEVICE_INFO) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.DEVICE_INFO_VIEW) {
                                DeviceInfoScreen(
                                    userId = currentUserId,
                                    firstSetup = false,
                                    onContinue = {
                                        navController.navigate(NavRoutes.PLAYLISTS) {
                                            popUpTo(NavRoutes.PLAYLISTS) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToPackageSelection = {
                                        val safeUid = currentUserId.ifBlank {
                                            com.example.model.DeviceManager.getCurrentUserId()
                                                ?: com.example.model.DeviceManager.getDeviceId().ifBlank { "default" }
                                        }
                                        navController.navigate("${NavRoutes.PACKAGE_SELECTION}/${Uri.encode(safeUid)}")
                                    },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.DASHBOARD) {
                                                popUpTo(NavRoutes.DEVICE_INFO_VIEW) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                            composable(
                                route = "${NavRoutes.PACKAGE_SELECTION}/{userId}",
                                arguments = listOf(navArgument("userId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val uid = backStackEntry.arguments?.getString("userId") ?: currentUserId
                                PackageSelectionScreen(
                                    userId = uid,
                                    onPackageSelected = { action ->
                                        if (action == "BACK" && navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.PLAYLISTS) {
                                                popUpTo("${NavRoutes.PACKAGE_SELECTION}/{userId}") { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.WELCOME) {
                                WelcomeScreen(
                                    onPlaylists = { navController.navigate(NavRoutes.PLAYLISTS) },
                                    onDeviceInfo = { navController.navigate(NavRoutes.DEVICE_INFO_VIEW) },
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            handleRootExit()
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.PLAYLISTS) {
                                LaunchedEffect(currentUserId) {
                                    if (currentUserId.isNotBlank()) {
                                        val db = AppDatabase.getDatabase(this@MainActivity)
                                        val user = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            db.iptvDao().getUser(currentUserId)
                                        }
                                        if (user != null) {
                                            // Arka planda, ekranı bekletmeden: cihaz kaydı + deneme/paket durumunu tazele
                                            com.example.model.OdooIntegrationManager.registerCustomerAndTrial(
                                                context = this@MainActivity,
                                                userId = user.id,
                                                userName = user.name ?: "Kullanıcı",
                                                userEmail = user.email ?: ""
                                            )
                                        }
                                    }
                                }
                                PlayListsScreen(
                                    userId = currentUserId,
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.WELCOME) {
                                                popUpTo(NavRoutes.PLAYLISTS) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onSelectPlaylist = { host, user, pass -> 
                                        scope.launch {
                                            val urlToLoad = if (user.isEmpty() && pass.isEmpty()) host else "$host/get.php?username=$user&password=$pass&type=m3u_plus&output=mpegts"
                                            PlaylistRepository.loadPlaylist(this@MainActivity, urlToLoad)
                                            if (PlaylistRepository.error.value == null) {
                                                navController.navigate(NavRoutes.DASHBOARD) {
                                                    popUpTo(NavRoutes.PLAYLISTS) { inclusive = false }
                                                    launchSingleTop = true
                                                }
                                            } else {
                                                android.widget.Toast.makeText(this@MainActivity, PlaylistRepository.error.value, android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    onDeviceInfo = {
                                        navController.navigate(NavRoutes.DEVICE_INFO_VIEW)
                                    },
                                    onSignOut = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.DASHBOARD) {
                                DashboardScreen(
                                    onPlayStream = { item ->
                                        com.example.model.PlayerRepository.currentlyPlayingItem = item
                                        navController.navigate(NavRoutes.PLAYER) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToAuth = {
                                        scope.launch {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.iptvDao().clearUsers()
                                            currentUserId = ""
                                            navController.navigate(NavRoutes.GOOGLE_SIGN_IN) {
                                                popUpTo(navController.graph.id) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    onNavigateToPlaylists = {
                                        navController.navigate(NavRoutes.PLAYLISTS) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToDeviceInfo = {
                                        navController.navigate(NavRoutes.DEVICE_INFO_VIEW) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable(NavRoutes.PLAYER) {
                                PlayerScreen(
                                    onBack = {
                                        if (navController.previousBackStackEntry != null) {
                                            navController.popBackStack()
                                        } else {
                                            navController.navigate(NavRoutes.DASHBOARD) {
                                                popUpTo(NavRoutes.PLAYER) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        
                        PlaylistLoadingDialog()
                        com.example.ui.components.UpdateDialog()
                    }
                }
            }
        }
        }
        window.decorView.post {
            window.applyImmersiveFullscreen()
        }
    }

    override fun onStart() {
        super.onStart()
        window.applyImmersiveFullscreen()
    }

    override fun onResume() {
        super.onResume()
        com.example.auth.GoogleAuthManager.currentActivity = java.lang.ref.WeakReference(this)
        window.applyImmersiveFullscreen()
        window.decorView.post {
            window.applyImmersiveFullscreen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        window.applyImmersiveFullscreen()
        if (hasFocus) {
            window.decorView.post {
                window.applyImmersiveFullscreen()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.applyImmersiveFullscreen()
        window.decorView.post {
            window.applyImmersiveFullscreen()
        }
    }
}
