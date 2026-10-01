package com.example.util

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.auth.findActivity

@Suppress("DEPRECATION")
fun Window.applyImmersiveFullscreen() {
    try {
        WindowCompat.setDecorFitsSystemWindows(this, false)
        addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attrs = attributes
            if (attrs.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                attributes = attrs
            }
        }

        val controller = WindowCompat.getInsetsController(this, decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    } catch (_: Exception) {
    }
}

private tailrec fun View.findDialogWindow(): Window? {
    val provider = this as? DialogWindowProvider
    if (provider != null) return provider.window
    val parentView = parent as? View ?: return null
    return parentView.findDialogWindow()
}

@Composable
fun KeepSystemBarsHidden() {
    val view = LocalView.current
    val context = LocalContext.current

    SideEffect {
        view.findDialogWindow()?.applyImmersiveFullscreen()
        context.findActivity()?.window?.applyImmersiveFullscreen()
    }

    DisposableEffect(view) {
        val dialogWindow = view.findDialogWindow()
        val activityWindow = context.findActivity()?.window
        dialogWindow?.applyImmersiveFullscreen()
        activityWindow?.applyImmersiveFullscreen()

        onDispose {
            activityWindow?.applyImmersiveFullscreen()
            activityWindow?.decorView?.post {
                activityWindow.applyImmersiveFullscreen()
            }
        }
    }
}
