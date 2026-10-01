package com.example.model

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

object AppLanguageManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANG = "language"
    
    private val _currentLanguage = MutableStateFlow("tr")
    val currentLanguage: StateFlow<String> = _currentLanguage
    
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANG, "tr") ?: "tr"
        _currentLanguage.value = lang
        applyLocale(context, lang)
    }
    
    fun setLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, lang).apply()
        _currentLanguage.value = lang
        applyLocale(context, lang)
    }

    fun localizedContext(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(_currentLanguage.value))
        return base.createConfigurationContext(config)
    }

    private fun applyLocale(context: Context, lang: String) {
        try {
            val locale = Locale(lang)
            Locale.setDefault(locale)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
