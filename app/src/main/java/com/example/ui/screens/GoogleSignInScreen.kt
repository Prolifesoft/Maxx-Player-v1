package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleAuthResult
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import com.example.model.db.AppDatabase
import com.example.model.db.UserEntity
import com.example.ui.theme.RedPrimary
import kotlinx.coroutines.launch

private const val DEFAULT_FALLBACK_CLIENT_ID = "65327632118-hpoe8apnlf4s9aqjhfeicvb1obv4slkc.apps.googleusercontent.com"

@Composable
fun GoogleSignInScreen(
    onSignInSuccess: (String) -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    androidx.activity.compose.BackHandler(enabled = true) {
        onBack()
    }

    // Initialize device identity
    LaunchedEffect(Unit) {
        DeviceManager.init(context)
    }

    val startGoogleSignIn: () -> Unit = {
        val configuredId = BuildConfig.GOOGLE_CLIENT_ID
        val clientId = if (configuredId.isBlank() || configuredId == "YOUR_GOOGLE_CLIENT_ID" || configuredId == "mock-client-id") {
            DEFAULT_FALLBACK_CLIENT_ID
        } else {
            configuredId
        }

        isLoading = true
        statusText = "Google ile bağlantı kuruluyor..."
        errorMessage = null

        scope.launch {
            try {
                // Use the Google Web Application Client ID required by CredentialManager
                val result = GoogleAuthManager.signInWithGoogle(context, DEFAULT_FALLBACK_CLIENT_ID)
                when (result) {
                    is GoogleAuthResult.Success -> {
                        statusText = "Odoo 19 ile senkronize ediliyor..."
                        val user = result.user
                        val email = user.email ?: user.id
                        val displayName = user.displayName ?: "Google Kullanıcısı"

                        // 1. Doğrulanan Google kullanıcısını yerel veritabanına ve DeviceManager'a kaydet
                        val db = AppDatabase.getDatabase(context)
                        db.iptvDao().insertUser(
                            UserEntity(
                                id = email,
                                name = displayName,
                                email = email,
                                photoUrl = user.photoUrl
                            )
                        )
                        DeviceManager.setCustomerName(displayName)
                        DeviceManager.setCurrentUser(email, displayName, email, user.photoUrl)

                        // 2. Odoo sunucusuyla senkronizasyon
                        try {
                            OdooIntegrationManager.registerCustomerAndTrial(
                                context = context,
                                userId = email,
                                userName = displayName,
                                userEmail = email
                            )
                        } catch (e: Exception) {
                            android.util.Log.w("GoogleSignIn", "Odoo kayit uyarisi: ${e.message}")
                        }

                        // 3. Varsa mevcut Odoo çalma listelerini senkronize et
                        try {
                            OdooIntegrationManager.syncPlaylistsFromOdoo(context, email)
                        } catch (e: Exception) {}

                        // 4. Başarılı şekilde Cihaz Bilgileri ekranına geçiş yap
                        onSignInSuccess(email)
                    }
                    is GoogleAuthResult.NoAccountOnDevice -> {
                        isLoading = false
                        statusText = null
                        errorMessage = "Cihazınızda kayıtlı bir Google hesabı bulunamadı. Lütfen cihaz ayarlarınızdan Google hesabınızı ekleyip tekrar deneyiniz."
                    }
                    is GoogleAuthResult.Cancelled -> {
                        isLoading = false
                        statusText = null
                        errorMessage = "Google oturumu tamamlanamadı. Lütfen tekrar deneyiniz."
                    }
                    is GoogleAuthResult.Error -> {
                        isLoading = false
                        statusText = null
                        errorMessage = if (result.isConfigurationError) {
                            "Google OAuth yapılandırması kontrol edilmelidir. Google Cloud Console'da 'Web Uygulaması' (Web Application) türünde İstemci Kimliği oluşturulmalıdır."
                        } else {
                            result.message ?: "Google ile giriş yapılırken bir hata oluştu."
                        }
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                statusText = null
                errorMessage = "Google girişi sırasında hata: ${e.localizedMessage}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0A0E17),
                        Color(0xFF06090F)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // App Icon & Glow
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = stringResource(R.string.app_name),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Giriş Yap & Kayıt Ol",
                color = Color(0xFFE2E8F0),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tüm oynatma listeleriniz, paketleriniz ve cihaz ayarlarınız Google hesabınızla güvenle senkronize edilir.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error banner if any
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1318)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Giriş Uyarısı",
                                color = Color(0xFFFFCDD2),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFE0E0E0),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Exclusive Google Sign In Button
            Button(
                onClick = { if (!isLoading) startGoogleSignIn() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_signin_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = RedPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusText ?: "Giriş yapılıyor...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1F1F1F)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Google ile Giriş Yap",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Information / Features Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141E2E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureItem(
                        icon = Icons.Default.Shield,
                        iconTint = Color(0xFF4CAF50),
                        title = "Güvenli Oturum",
                        desc = "Şifre girmeden Google Identity güvencesiyle tek tıkla oturum açın."
                    )
                    Divider(color = Color(0xFF1E293B), thickness = 1.dp)
                    FeatureItem(
                        icon = Icons.Default.Sync,
                        iconTint = Color(0xFF2196F3),
                        title = "Otomatik Senkronizasyon",
                        desc = "Çalma listeleriniz ve paketleriniz anında cihazınıza tanımlanır."
                    )
                    Divider(color = Color(0xFF1E293B), thickness = 1.dp)
                    FeatureItem(
                        icon = Icons.Default.Devices,
                        iconTint = Color(0xFFFF9800),
                        title = "Çoklu Cihaz Desteği",
                        desc = "Web portalı üzerinden listelerinizi uzaktan yönetin."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )
        }
    }
}
