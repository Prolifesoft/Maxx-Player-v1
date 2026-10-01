package com.example.model

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_IS_PORTRAIT_ENABLED = "is_portrait_enabled"
    private const val KEY_ORIENTATION_MODE = "orientation_mode" // 0: Otomatik, 1: Dikey, 2: Yatay

    private lateinit var prefs: SharedPreferences

    private val _isPortraitEnabled = MutableStateFlow(false)
    val isPortraitEnabled: StateFlow<Boolean> = _isPortraitEnabled.asStateFlow()

    private val _orientationMode = MutableStateFlow(0) // 0: Auto, 1: Portrait, 2: Landscape (Default: Auto)
    val orientationMode: StateFlow<Int> = _orientationMode.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPortraitEnabled.value = prefs.getBoolean(KEY_IS_PORTRAIT_ENABLED, false)
        _orientationMode.value = prefs.getInt(KEY_ORIENTATION_MODE, 0)
    }

    fun setPortraitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_PORTRAIT_ENABLED, enabled).apply()
        _isPortraitEnabled.value = enabled
        if (enabled) {
            setOrientationMode(1)
        } else {
            setOrientationMode(0)
        }
    }

    fun setOrientationMode(mode: Int) {
        prefs.edit().putInt(KEY_ORIENTATION_MODE, mode).apply()
        _orientationMode.value = mode
        _isPortraitEnabled.value = (mode == 1)
    }

    fun applyOrientationToActivity(activity: android.app.Activity) {
        val target = when (_orientationMode.value) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        if (activity.requestedOrientation != target) {
            activity.requestedOrientation = target
        }
    }
}

