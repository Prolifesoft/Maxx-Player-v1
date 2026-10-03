package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import com.example.model.db.AppDatabase
import com.example.model.db.UserEntity
import com.example.ui.components.openExternalBrowserSafely
import com.example.ui.theme.RedPrimary
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun Modifier.tvFocusBorder(
    shape: Shape = RoundedCornerShape(8.dp),
    borderColor: Color = Color(0xFF64B5F6)
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) Modifier.border(2.dp, borderColor, shape)
            else Modifier
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    userId: String,
    onContinue: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSignOut: () -> Unit = {},
    onNavigateToPackageSelection: () -> Unit = {},
    firstSetup: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val daysRemaining by DeviceManager.trialDaysLeft.collectAsState()
    val isPro by DeviceManager.isProState.collectAsState()
    val activePackageName by DeviceManager.activePackageNameState.collectAsState()
    val packageExpireDate by DeviceManager.packageExpireDateState.collectAsState()
    val activeDeviceCount by DeviceManager.activeDeviceCountState.collectAsState()
    val deviceLimit by DeviceManager.deviceLimitState.collectAsState()
    val activePlaylistCount by DeviceManager.activePlaylistCountState.collectAsState()
    val isPortalRegistered by DeviceManager.portalDeviceRegisteredState.collectAsState()
    val portalRegisteredEmail by DeviceManager.portalRegisteredEmailState.collectAsState()
    val portalLastSyncTime by DeviceManager.portalLastSyncTimeState.collectAsState()
    val isSyncing by OdooIntegrationManager.isSyncing.collectAsState()
    val isSendingDevice by OdooIntegrationManager.isSendingDevice.collectAsState()
    val portalCheckStatusText by OdooIntegrationManager.portalCheckStatusText.collectAsState()

    var deviceId by remember { mutableStateOf(DeviceManager.getDeviceId()) }
    var deviceKey by remember { mutableStateOf(DeviceManager.getDeviceKey()) }
    val deviceModel = remember { DeviceManager.getDeviceModel() }
    val osVersion = remember { DeviceManager.getOsVersion() }
    var webPortalUrl by remember { mutableStateOf(DeviceManager.getWebPortalUrl()) }
    val macAddress = remember { DeviceManager.getMacAddress() }

    val qrBitmap = remember(webPortalUrl) {
        QrCodeGenerator.generateQrImageBitmap(webPortalUrl, 380)
    }

    var showProfileSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf<UserEntity?>(null) }
    var isAutoRedirecting by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            showSupportSheet -> showSupportSheet = false
            showProfileSheet -> showProfileSheet = false
            onBack != null -> onBack.invoke()
        }
    }

    val sendDeviceToPortalAction: () -> Unit = {
        scope.launch {
            val targetId = currentUser?.id ?: userId.ifBlank { DeviceManager.getCurrentUserId() ?: DeviceManager.getDeviceId() }
            val targetName = currentUser?.name ?: DeviceManager.getCurrentUserName() ?: "Kullanıcı"
            val targetEmail = currentUser?.email ?: DeviceManager.getCurrentUserEmail() ?: ""
            OdooIntegrationManager.sendDeviceToPortalAndSyncAll(
                context = context,
                userId = targetId,
                userName = targetName,
                userEmail = targetEmail
            )
            deviceId = DeviceManager.getDeviceId()
            deviceKey = DeviceManager.getDeviceKey()
            webPortalUrl = DeviceManager.getWebPortalUrl()
            Toast.makeText(
                context,
                "Cihaz bilgileriniz ($deviceId • Kod: $deviceKey) web portalındaki hesabınıza kaydedildi ve güncel bilgiler eşitlendi!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(userId) {
        val db = AppDatabase.getDatabase(context)
        val user = withContext(Dispatchers.IO) {
            val fetched = if (userId.isNotBlank()) db.iptvDao().getUser(userId) else db.iptvDao().getFirstUser()
            if (fetched != null && fetched.email.isNullOrBlank()) {
                db.iptvDao().clearUsers()
                null
            } else {
                fetched
            }
        }
        if (user == null) {
            onSignOut()
            return@LaunchedEffect
        }
        currentUser = user
        val targetUserId = user.id
        val userEmail = user.email ?: ""
        val userName = user.name ?: "Kullanıcı"

        val initialPlaylists = withContext(Dispatchers.IO) {
            db.iptvDao().getPlaylistsForUserSync(targetUserId)
        }
        DeviceManager.updateActivePlaylistCount(initialPlaylists.size)

        if (firstSetup && initialPlaylists.isNotEmpty()) {
            onContinue()
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            OdooIntegrationManager.sendDeviceToPortalAndSyncAll(
                context = context,
                userId = targetUserId,
                userName = userName,
                userEmail = userEmail
            )
        }

        deviceId = DeviceManager.getDeviceId()
        deviceKey = DeviceManager.getDeviceKey()
        webPortalUrl = DeviceManager.getWebPortalUrl()

        while (true) {
            if (!isAutoRedirecting) {
                try {
                    val syncedCount = OdooIntegrationManager.syncPlaylistsFromOdoo(context, targetUserId)
                    deviceId = DeviceManager.getDeviceId()
                    deviceKey = DeviceManager.getDeviceKey()
                    if (firstSetup && syncedCount > 0) {
                        isAutoRedirecting = true
                        delay(1800)
                        onContinue()
                        break
                    }
                } catch (_: Exception) {}
            }
            delay(6000)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label ${context.getString(R.string.copy_copied)}", Toast.LENGTH_SHORT).show()
    }

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = density.fontScale.coerceAtMost(1.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1116))
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                val isLandscapeLayout = maxWidth > maxHeight

                if (isLandscapeLayout) {
                    // Yatay / Geniş Ekran / TV Düzeni (Üst ve alttan 15dp azaltılmış güvenli yerleşim)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        DeviceScreenHeader(
                            onBack = onBack,
                            onProfileClick = { showProfileSheet = true },
                            onSignOutClick = {
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    db.iptvDao().clearUsers()
                                    Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                    onSignOut()
                                }
                            }
                        )

                        // Yatayda Böl: Sol ve Sağ Kolon
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sol Kolon: Hesap Kartı + Portala Gönder Butonu + Sistem Bilgileri
                            Column(
                                modifier = Modifier
                                    .weight(0.48f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                DeviceAccountCard(
                                    currentUser = currentUser,
                                    isPro = isPro,
                                    daysRemaining = daysRemaining,
                                    onNavigateToPackageSelection = onNavigateToPackageSelection,
                                    isLandscape = true
                                )

                                SendDeviceToPortalButton(
                                    isSending = isSendingDevice || isSyncing,
                                    isRegistered = isPortalRegistered,
                                    onSendClick = sendDeviceToPortalAction,
                                    onContinueClick = onContinue,
                                    showContinueButton = firstSetup || activePlaylistCount > 0,
                                    isLandscape = true
                                )

                                DeviceSystemSpecsCard(
                                    deviceModel = deviceModel,
                                    osVersion = osVersion,
                                    macAddress = macAddress,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    isLandscape = true
                                )
                            }

                            // Sağ Kolon: QR & Kimlik Kartı + Portal Satırı
                            Column(
                                modifier = Modifier
                                    .weight(0.52f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                DeviceQrIdentityCard(
                                    qrBitmap = qrBitmap,
                                    deviceId = deviceId,
                                    deviceKey = deviceKey,
                                    onCopy = ::copyToClipboard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    isLandscape = true
                                )

                                DevicePortalRow(
                                    webPortalUrl = webPortalUrl,
                                    onCopy = ::copyToClipboard
                                )
                            }
                        }

                        // Uygulamanın altında cihaz bilgileri ekli olup olmadığı ve güncel paket/cihaz/liste eşleşme kontrol durumu
                        DevicePortalRegistrationStatusBar(
                            isChecking = isSendingDevice || isSyncing,
                            isRegistered = isPortalRegistered,
                            deviceId = deviceId,
                            deviceKey = deviceKey,
                            accountEmail = portalRegisteredEmail ?: currentUser?.email ?: DeviceManager.getCurrentUserEmail() ?: "",
                            activePackageName = activePackageName,
                            packageExpireDate = packageExpireDate,
                            daysRemaining = daysRemaining,
                            isPro = isPro,
                            activeDeviceCount = activeDeviceCount,
                            deviceLimit = deviceLimit,
                            activePlaylistCount = activePlaylistCount,
                            lastSyncTime = portalLastSyncTime,
                            statusDetailText = portalCheckStatusText,
                            onSyncClick = sendDeviceToPortalAction
                        )
                    }
                } else {
                    // Dikey Ekran Düzeni (Üst ve alttan 15dp azaltılmış güvenli yerleşim)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 22.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DeviceScreenHeader(
                            onBack = onBack,
                            onProfileClick = { showProfileSheet = true },
                            onSignOutClick = {
                                scope.launch {
                                    val db = AppDatabase.getDatabase(context)
                                    db.iptvDao().clearUsers()
                                    Toast.makeText(context, context.getString(R.string.sign_out_account), Toast.LENGTH_SHORT).show()
                                    onSignOut()
                                }
                            }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeviceAccountCard(
                                currentUser = currentUser,
                                isPro = isPro,
                                daysRemaining = daysRemaining,
                                onNavigateToPackageSelection = onNavigateToPackageSelection,
                                isLandscape = false
                            )

                            // QR/Kimlik Kartı (Eşleşme kodu ve Cihaz ID kesilmeden tam görünür)
                            DeviceQrIdentityCard(
                                qrBitmap = qrBitmap,
                                deviceId = deviceId,
                                deviceKey = deviceKey,
                                onCopy = ::copyToClipboard,
                                modifier = Modifier.fillMaxWidth(),
                                isLandscape = false
                            )

                            // Cihaz Bilgilerimi Portala Gönder Butonu
                            SendDeviceToPortalButton(
                                isSending = isSendingDevice || isSyncing,
                                isRegistered = isPortalRegistered,
                                onSendClick = sendDeviceToPortalAction,
                                onContinueClick = onContinue,
                                showContinueButton = firstSetup || activePlaylistCount > 0,
                                isLandscape = false
                            )

                            DevicePortalRow(
                                webPortalUrl = webPortalUrl,
                                onCopy = ::copyToClipboard
                            )

                            DeviceSystemSpecsCard(
                                deviceModel = deviceModel,
                                osVersion = osVersion,
                                macAddress = macAddress,
                                modifier = Modifier.fillMaxWidth(),
                                isLandscape = false
                            )
                        }

                        // Uygulamanın altında cihaz bilgileri ekli olup olmadığı ve güncel paket/cihaz/liste eşleşme kontrol durumu
                        DevicePortalRegistrationStatusBar(
                            isChecking = isSendingDevice || isSyncing,
                            isRegistered = isPortalRegistered,
                            deviceId = deviceId,
                            deviceKey = deviceKey,
                            accountEmail = portalRegisteredEmail ?: currentUser?.email ?: DeviceManager.getCurrentUserEmail() ?: "",
                            activePackageName = activePackageName,
                            packageExpireDate = packageExpireDate,
                            daysRemaining = daysRemaining,
                            isPro = isPro,
                            activeDeviceCount = activeDeviceCount,
                            deviceLimit = deviceLimit,
                            activePlaylistCount = activePlaylistCount,
                            lastSyncTime = portalLastSyncTime,
                            statusDetailText = portalCheckStatusText,
                            onSyncClick = sendDeviceToPortalAction
                        )
                    }
                }
            }
        }
    }

    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E232A),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            ProfileSettingsSheet(
                onClose = { showProfileSheet = false },
                onNavigateToAuth = {
                    showProfileSheet = false
                    onSignOut()
                },
                onOpenSupport = {
                    showProfileSheet = false
                    showSupportSheet = true
                }
            )
        }
    }

    if (showSupportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF1E232A),
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            SupportTicketsSheet(onClose = { showSupportSheet = false })
        }
    }
}

/**
 * 1. Başlık Satırı (Cihazım, Geri, Profil, Çıkış)
 */
@Composable
private fun DeviceScreenHeader(
    onBack: (() -> Unit)?,
    onProfileClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(32.dp)
                        .tvFocusBorder(CircleShape)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.close_desc),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = stringResource(R.string.device_info_title),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onProfileClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1E232A),
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .tvFocusBorder(RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    Icons.Default.PersonOutline,
                    contentDescription = null,
                    tint = Color(0xFF42A5F5),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.profile_desc),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onSignOutClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1E232A),
                    contentColor = Color(0xFFEF5350)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .tvFocusBorder(RoundedCornerShape(6.dp)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.btn_logout_short),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 4. Hesap Kartı
 * Dikeyde: Üstte fotoğraf + ad + eposta, altta [Deneme: 15 gün] ve [Paket Yükselt] hapları
 * Yatayda: Tek kompakt satır
 */
@Composable
private fun DeviceAccountCard(
    currentUser: UserEntity?,
    isPro: Boolean,
    daysRemaining: Int,
    onNavigateToPackageSelection: () -> Unit,
    isLandscape: Boolean = false
) {
    val photoUrl = currentUser?.photoUrl ?: DeviceManager.getCurrentUserPhotoUrl()
    val activePkgName = DeviceManager.getActivePackageName()

    val displayName = currentUser?.name?.takeIf { it.isNotBlank() }
        ?: DeviceManager.getCurrentUserName()?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.substringBefore("@")
        ?: DeviceManager.getCurrentUserEmail()?.substringBefore("@")
        ?: "Kullanıcı"

    val displayEmail = currentUser?.email?.takeIf { it.isNotBlank() }
        ?: DeviceManager.getCurrentUserEmail()
        ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14171C)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        if (isLandscape) {
            // Yatay mod: Tek satır kompakt düzen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Profil Fotoğrafı",
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E88E5))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayEmail.isNotBlank()) {
                        Text(
                            text = displayEmail,
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isPro && !activePkgName.isNullOrBlank() -> {
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = RoundedCornerShape(6.dp),
                                contentColor = Color.White
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.package_active_format, activePkgName),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        daysRemaining > 0 -> {
                            Surface(
                                color = Color(0xFF14243B),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.3f)),
                                contentColor = Color(0xFF64B5F6)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.trial_badge_days_short, daysRemaining),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                color = Color(0xFF331414),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
                                contentColor = Color(0xFFFF8A80)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.trial_badge_expired),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onNavigateToPackageSelection,
                        color = Color(0xFF163820),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                        contentColor = Color(0xFF81C784),
                        modifier = Modifier.tvFocusBorder(RoundedCornerShape(6.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPro) stringResource(R.string.btn_upgrade_short) else if (daysRemaining > 0) stringResource(R.string.btn_upgrade_short) else stringResource(R.string.btn_get_package),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Dikey mod: Mockup ile birebir aynı 2 satırlı düzen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profil Fotoğrafı",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E88E5))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (displayEmail.isNotBlank()) {
                            Text(
                                text = displayEmail,
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sol Hap: Durum Rozeti
                    when {
                        isPro && !activePkgName.isNullOrBlank() -> {
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784).copy(alpha = 0.4f)),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF81C784),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.package_active_format, activePkgName),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        daysRemaining > 0 -> {
                            Surface(
                                color = Color(0xFF14243B),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E88E5).copy(alpha = 0.3f)),
                                contentColor = Color(0xFF64B5F6),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = Color(0xFF64B5F6),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.trial_badge_days, daysRemaining),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                color = Color(0xFF331414),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.4f)),
                                contentColor = Color(0xFFFF8A80),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF8A80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.trial_badge_expired),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Sağ Hap: Aksiyon Butonu
                    Surface(
                        onClick = onNavigateToPackageSelection,
                        color = Color(0xFF163820),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.5f)),
                        contentColor = Color(0xFF81C784),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .tvFocusBorder(RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = Color(0xFF81C784),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPro) stringResource(R.string.btn_upgrade_short) else if (daysRemaining > 0) stringResource(R.string.btn_upgrade_package_label) else stringResource(R.string.btn_get_package),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5. QR + Kimlik Kartı (TEK birleşik kart)
 * Üstte: "QR kodu okutarak web portalını aç" (8sp, ortalanmış)
 * Sol: QR Kodu (~80-90dp kare, beyaz zemin, 8dp köşe, RedPrimary alfa kenarlık)
 * Sağ: CİHAZ ID ve PIN chipleri (monospace, kopyala ikonu, kesilmez)
 */
@Composable
private fun DeviceQrIdentityCard(
    qrBitmap: ImageBitmap?,
    deviceId: String,
    deviceKey: String,
    onCopy: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val displayKey = deviceKey.ifBlank { DeviceManager.getDeviceKey() }
    val displayId = deviceId.ifBlank { DeviceManager.getDeviceId() }

    if (!isLandscape) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF14171C), Color(0xFF0E1116))),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color(0xFF23262C), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.qr_open_web_portal),
                fontSize = 10.sp,
                color = Color(0xFF8B8E93),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // QR — sabit kare, dikeyde ortalanmış
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, RedPrimary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR Kod",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(
                            color = RedPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ID + EŞLEŞME KODU (PIN) — Sabit yükseklik kısıtlaması olmadan tam görünür
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.device_id_tag),
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E232A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .clickable { onCopy("Cihaz ID", displayId) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayId,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.device_pin_tag),
                            fontSize = 8.5.sp,
                            color = Color(0xFFFFCA28),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF262012), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFFFFCA28).copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                .clickable { onCopy("Eşleşme Kodu (PIN)", displayKey) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayKey,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                color = Color(0xFFFFCA28),
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = Color(0xFFFFCA28),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF14171C), Color(0xFF0E1116))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.qr_open_web_portal),
                        fontSize = 8.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Sol: QR Kodu
                        Box(
                            modifier = Modifier
                                .sizeIn(
                                    minWidth = 72.dp,
                                    maxWidth = 88.dp,
                                    minHeight = 72.dp,
                                    maxHeight = 88.dp
                                )
                                .aspectRatio(1f)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, RedPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "QR Kod",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(
                                    color = RedPrimary,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }

                        // Sağ: Cihaz ID ve Eşleşme Kodu (PIN) Chipleri
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                        ) {
                            // Cihaz ID
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.device_id_tag),
                                    fontSize = 7.5.sp,
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Surface(
                                    onClick = { onCopy("Cihaz ID", displayId) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E232A),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusBorder(RoundedCornerShape(6.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayId,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            color = Color.White,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Kopyala",
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            // Eşleşme Kodu / Cihaz PIN
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.device_pin_tag),
                                    fontSize = 7.5.sp,
                                    color = Color(0xFFFFCA28),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Surface(
                                    onClick = { onCopy("Eşleşme Kodu (PIN)", displayKey) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF262012),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCA28).copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tvFocusBorder(RoundedCornerShape(6.dp))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayKey,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp,
                                            letterSpacing = 1.5.sp,
                                            color = Color(0xFFFFCA28),
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Kopyala",
                                            tint = Color(0xFFFFCA28),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cihaz Bilgilerimi Portala Gönder & Eşitle Butonu
 */
@Composable
private fun SendDeviceToPortalButton(
    isSending: Boolean,
    isRegistered: Boolean,
    onSendClick: () -> Unit,
    onContinueClick: () -> Unit,
    showContinueButton: Boolean,
    isLandscape: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = { if (!isSending) onSendClick() },
            enabled = !isSending,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRegistered) Color(0xFF1565C0) else RedPrimary,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF1E293B),
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 10.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (isLandscape) 36.dp else 44.dp)
                .tvFocusBorder(RoundedCornerShape(10.dp))
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_sending_device_to_portal),
                    fontSize = if (isLandscape) 10.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Icon(
                    imageVector = if (isRegistered) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 15.dp else 18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRegistered) stringResource(R.string.btn_update_device_on_portal) else stringResource(R.string.btn_send_device_to_portal),
                    fontSize = if (isLandscape) 10.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showContinueButton) {
            OutlinedButton(
                onClick = onContinueClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF163820),
                    contentColor = Color(0xFF81C784)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.7f)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = if (isLandscape) 6.dp else 10.dp),
                modifier = Modifier
                    .heightIn(min = if (isLandscape) 36.dp else 44.dp)
                    .tvFocusBorder(RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(if (isLandscape) 15.dp else 17.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Listelerim",
                    fontSize = if (isLandscape) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Uygulamanın altında cihaz bilgilerinin web portalında ekli olup olmadığını
 * ve güncel paket / cihaz / çalma listesi otomatik eşleşme durumunu gösteren alt kontrol barı
 */
@Composable
private fun DevicePortalRegistrationStatusBar(
    isChecking: Boolean,
    isRegistered: Boolean,
    deviceId: String,
    deviceKey: String,
    accountEmail: String,
    activePackageName: String,
    packageExpireDate: String?,
    daysRemaining: Int,
    isPro: Boolean,
    activeDeviceCount: Int,
    deviceLimit: Int,
    activePlaylistCount: Int,
    lastSyncTime: String?,
    statusDetailText: String?,
    onSyncClick: () -> Unit
) {
    val bgColor = when {
        isChecking -> Color(0xFF14243B)
        isRegistered -> Color(0xFF11291B)
        else -> Color(0xFF2B1D12)
    }
    val borderColor = when {
        isChecking -> Color(0xFF42A5F5).copy(alpha = 0.45f)
        isRegistered -> Color(0xFF66BB6A).copy(alpha = 0.5f)
        else -> Color(0xFFFFB74D).copy(alpha = 0.5f)
    }
    val accentColor = when {
        isChecking -> Color(0xFF64B5F6)
        isRegistered -> Color(0xFF81C784)
        else -> Color(0xFFFFCA28)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isRegistered) Icons.Default.Verified else Icons.Default.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isChecking -> stringResource(R.string.portal_status_checking_title)
                            isRegistered -> stringResource(R.string.portal_status_registered_title)
                            else -> stringResource(R.string.portal_status_not_registered_title)
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val pkgSummary = if (isPro) {
                        "$activePackageName (PRO${if (!packageExpireDate.isNullOrBlank()) " • $packageExpireDate" else ""})"
                    } else {
                        "$activePackageName ($daysRemaining gün)"
                    }
                    val accountPart = if (accountEmail.isNotBlank()) "Hesap: $accountEmail • " else ""
                    val syncTimePart = if (!lastSyncTime.isNullOrBlank()) " • Saat: $lastSyncTime" else ""
                    val summaryLine = if (isRegistered) {
                        "${accountPart}ID: $deviceId • Kod: $deviceKey • Paket: $pkgSummary • Cihaz: $activeDeviceCount/$deviceLimit • Liste: $activePlaylistCount$syncTimePart"
                    } else {
                        statusDetailText ?: "Cihazınızı ($deviceId / Kod: $deviceKey) hesaba kaydetmek için 'Cihaz Bilgilerimi Portala Gönder' butonuna tıklayın."
                    }

                    Text(
                        text = summaryLine,
                        color = Color(0xFFCBD5E1),
                        fontSize = 8.5.sp,
                        maxLines = 2,
                        lineHeight = 11.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                onClick = { if (!isChecking) onSyncClick() },
                color = accentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                modifier = Modifier.tvFocusBorder(RoundedCornerShape(6.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = "Şimdi Eşitle",
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_check_sync_short),
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 6. Portal Satırı
 * "Portal: maxxplayers.com/my/maxx" solda (mavi), tarayıcıda aç ikonu sağda.
 */
@Composable
private fun DevicePortalRow(
    webPortalUrl: String,
    onCopy: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showInAppWebPortal by remember { mutableStateOf(false) }
    if (showInAppWebPortal) {
        com.example.ui.components.WebPortalDialog(
            initialUrl = webPortalUrl,
            onDismiss = { showInAppWebPortal = false }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0xFF14171C), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .clickable {
                openExternalBrowserSafely(
                    context = context,
                    url = webPortalUrl,
                    onFallbackToInApp = { showInAppWebPortal = true }
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${stringResource(R.string.portal_prefix)} ",
                fontSize = 9.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Normal
            )
            Text(
                text = webPortalUrl.removePrefix("https://").removePrefix("http://"),
                fontSize = 9.sp,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = {
                openExternalBrowserSafely(
                    context = context,
                    url = webPortalUrl,
                    onFallbackToInApp = { showInAppWebPortal = true }
                )
            },
            modifier = Modifier
                .size(26.dp)
                .tvFocusBorder(RoundedCornerShape(4.dp))
        ) {
            Icon(
                Icons.Default.OpenInBrowser,
                contentDescription = "Tarayıcıda Aç",
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * 7. Sistem Bilgileri: Sıkı 2x2 Grid (Model, Android, MAC, Uygulama Sürümü)
 */
@Composable
private fun DeviceSystemSpecsCard(
    deviceModel: String,
    osVersion: String,
    macAddress: String,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    if (isLandscape) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14171C)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.sys_info_model)}: ",
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = deviceModel,
                            fontSize = 8.5.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.sys_info_android)}: ",
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = osVersion,
                            fontSize = 8.5.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.04f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.sys_info_mac)}: ",
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = macAddress,
                            fontSize = 8.5.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.sys_info_version_short)}: ",
                            fontSize = 8.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            fontSize = 8.5.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Dikey mod: Mockup ile birebir aynı (kutu olmadan, doğrudan zemin üzerinde temiz 2x2 grid)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.sys_info_model)}: ",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = deviceModel,
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.sys_info_android)}: ",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = osVersion,
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.sys_info_mac)}: ",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = macAddress,
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.sys_info_version_short)}: ",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        fontSize = 8.5.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
