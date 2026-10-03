package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.example.model.DeviceManager
import com.example.ui.theme.RedPrimary
import com.example.util.QrCodeGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageSelectionScreen(
    userId: String,
    onPackageSelected: (String) -> Unit
) {
    BackHandler(enabled = true) {
        onPackageSelected("BACK")
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val activePackageName by DeviceManager.activePackageNameState.collectAsState()
    val packageExpireDate by DeviceManager.packageExpireDateState.collectAsState()
    val packageDuration by DeviceManager.packageDurationState.collectAsState()
    val daysRemaining by DeviceManager.trialDaysLeft.collectAsState()
    val isPro by DeviceManager.isProState.collectAsState()
    val isProOrInTrial by DeviceManager.isProOrInTrialState.collectAsState()

    val deviceId = remember { DeviceManager.getDeviceId() }
    val deviceKey = remember { DeviceManager.getDeviceKey() }
    val webPortalUrl = remember { DeviceManager.getPackageShopUrl() }
    var showInAppWebPortal by remember { mutableStateOf(false) }

    if (showInAppWebPortal) {
        com.example.ui.components.WebPortalDialog(
            initialUrl = webPortalUrl,
            onDismiss = { showInAppWebPortal = false }
        )
    }

    LaunchedEffect(userId) {
        val targetId = userId.ifBlank { DeviceManager.getCurrentUserId() ?: deviceId }
        try {
            com.example.model.OdooIntegrationManager.sendDeviceToPortalAndSyncAll(
                context = context,
                userId = targetId,
                userName = DeviceManager.getCurrentUserName() ?: "",
                userEmail = DeviceManager.getCurrentUserEmail() ?: ""
            )
        } catch (_: Exception) {}
        while (true) {
            kotlinx.coroutines.delay(10000)
            try {
                com.example.model.OdooIntegrationManager.syncPlaylistsFromOdoo(context, targetId)
            } catch (_: Exception) {}
        }
    }

    val qrBitmap = remember(webPortalUrl) {
        QrCodeGenerator.generateQrImageBitmap(webPortalUrl, 320)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.padding(top = 15.dp),
                title = {
                    Text(
                        text = "Paket & Abonelik Bilgisi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onPackageSelected("BACK") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp)
        ) {
            val isLandscapeLayout = maxWidth > maxHeight

            if (isLandscapeLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sol Taraf: Aktif Paket ve Bilgi Kartları (Scrollable)
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ActivePackageCard(
                            packageName = activePackageName,
                            expireDate = packageExpireDate,
                            duration = packageDuration,
                            daysRemaining = daysRemaining,
                            isPro = isPro,
                            isProOrInTrial = isProOrInTrial,
                            deviceId = deviceId,
                            deviceKey = deviceKey
                        )

                        WebOnlyNoticeCard()

                        ActionButtons(
                            context = context,
                            webPortalUrl = webPortalUrl,
                            onOpenInAppPortal = { showInAppWebPortal = true },
                            onContinue = {
                                DeviceManager.setActivePackageName(activePackageName)
                                onPackageSelected("CONTINUE")
                            }
                        )
                    }

                    // Sağ Taraf: Web Portalı QR Kod Kartı
                    Card(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Web Portalı QR Kodu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Paket satın alma ve süre uzatma için tarayın:",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "Web Portal QR",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "maxxplayers.com/shop",
                                color = Color(0xFF64B5F6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                // Dikey Yerleşim
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ActivePackageCard(
                        packageName = activePackageName,
                        expireDate = packageExpireDate,
                        duration = packageDuration,
                        daysRemaining = daysRemaining,
                        isPro = isPro,
                        isProOrInTrial = isProOrInTrial,
                        deviceId = deviceId,
                        deviceKey = deviceKey
                    )

                    WebOnlyNoticeCard()

                    // QR Kod Kartı
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = "Web Portal QR",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Web Portalı QR Kodu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kameranızla okutarak mağazamıza ulaşın ve paketinizi kolayca yönetin.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = webPortalUrl,
                                    color = Color(0xFF64B5F6),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    ActionButtons(
                        context = context,
                        webPortalUrl = webPortalUrl,
                        onOpenInAppPortal = { showInAppWebPortal = true },
                        onContinue = {
                            DeviceManager.setActivePackageName(activePackageName)
                            onPackageSelected("CONTINUE")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivePackageCard(
    packageName: String,
    expireDate: String?,
    duration: String?,
    daysRemaining: Int,
    isPro: Boolean,
    isProOrInTrial: Boolean,
    deviceId: String,
    deviceKey: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) Color(0xFF163820) else if (isProOrInTrial) Color(0xFF1A293E) else Color(0xFF381E1E)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPro) Color(0xFF81C784).copy(alpha = 0.5f) else if (isProOrInTrial) Color(0xFF64B5F6).copy(alpha = 0.5f) else Color(0xFFE57373).copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isPro) Icons.Default.Verified else if (isProOrInTrial) Icons.Default.Timer else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isPro) Color(0xFF81C784) else if (isProOrInTrial) Color(0xFF90CAF9) else Color(0xFFFF8A80),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = packageName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    color = if (isProOrInTrial) Color(0xFF2E7D32).copy(alpha = 0.35f) else Color(0xFFD32F2F).copy(alpha = 0.35f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isProOrInTrial) Color(0xFF81C784) else Color(0xFFE57373))
                ) {
                    Text(
                        text = if (isProOrInTrial) "$daysRemaining Gün Kaldı" else "Süresi Doldu",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

            if (!expireDate.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bitiş / Geçerlilik Tarihi:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(expireDate, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, softWrap = false)
                }
            }

            if (!duration.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paket Süresi:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(duration, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, softWrap = false)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cihaz ID:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(deviceId, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF64B5F6), softWrap = false)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cihaz PIN:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(deviceKey, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFFFFCA28), softWrap = false)
            }
        }
    }
}

@Composable
private fun WebOnlyNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF232B38)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF37474F))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF42A5F5),
                modifier = Modifier.size(22.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Paket Alımı Yalnızca Web Portalından Yapılır",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uygulama içerisinden paket satın alma işlemi yapılmamaktadır. Yeni paket almak, sürenizi uzatmak veya paket değişikliği yapmak için lütfen resmi web portalımızı ziyaret ediniz:",
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "https://maxxplayers.com/shop/category/maxx-players-web-player-paket-3",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64B5F6)
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    context: android.content.Context,
    webPortalUrl: String,
    onOpenInAppPortal: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                com.example.ui.components.openExternalBrowserSafely(
                    context = context,
                    url = webPortalUrl,
                    onFallbackToInApp = onOpenInAppPortal
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Paket Satın Al (maxxplayers.com)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Oynatma Listelerine Devam Et", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
