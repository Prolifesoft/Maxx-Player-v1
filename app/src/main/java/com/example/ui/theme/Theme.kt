package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
      primary = RedPrimary,
      secondary = RedPrimary,
      tertiary = TextWhite,
      background = DarkBackground,
      surface = DarkSurface,
      onPrimary = TextWhite,
      onSecondary = TextWhite,
      onTertiary = DarkBackground,
      onBackground = TextWhite,
      onSurface = TextWhite
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  com.example.util.KeepSystemBarsHidden()

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
