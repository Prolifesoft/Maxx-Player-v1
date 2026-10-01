package com.example.ui

import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun ProvideAppLocale(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val (newContext, configuration) = remember(context, language, currentConfiguration) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(currentConfiguration)
        config.setLocale(locale)
        val localizedContext = context.createConfigurationContext(config)
        val finalConfig = Configuration(localizedContext.resources.configuration).apply {
            orientation = currentConfiguration.orientation
            screenWidthDp = currentConfiguration.screenWidthDp
            screenHeightDp = currentConfiguration.screenHeightDp
            smallestScreenWidthDp = currentConfiguration.smallestScreenWidthDp
        }
        val wrappedContext = object : ContextWrapper(context) {
            override fun getResources(): Resources = localizedContext.resources
            override fun getAssets(): AssetManager = localizedContext.assets
        }
        Pair(wrappedContext, finalConfig)
    }
    
    CompositionLocalProvider(
        LocalContext provides newContext,
        LocalConfiguration provides configuration
    ) {
        content()
    }
}
