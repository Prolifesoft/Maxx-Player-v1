with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "r") as f:
    content = f.read()

new_splash = """package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToLogin: () -> Unit) {
    var hasNavigated by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(2000)
        if (!hasNavigated) {
            hasNavigated = true
            onNavigateToLogin()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Just a simple fallback image if no video
        Image(
            painter = painterResource(id = android.R.drawable.ic_media_play),
            contentDescription = "Logo",
            modifier = Modifier.size(100.dp)
        )
    }
}
"""

with open("app/src/main/java/com/example/ui/screens/SplashScreen.kt", "w") as f:
    f.write(new_splash)
