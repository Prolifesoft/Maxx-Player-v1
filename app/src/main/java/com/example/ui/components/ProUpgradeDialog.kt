package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.DeviceManager
import com.example.model.OdooIntegrationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class PortalCheckState {
    CHECKING,
    ONLINE,
    FALLBACK_MAIN,
    OFFLINE
}

data class PortalReachabilityResult(
    val state: PortalCheckState,
    val resolvedUrl: String,
    val statusMessage: String,
    val httpCode: Int = 0
)

suspend fun checkPortalReachability(primaryUrl: String): PortalReachabilityResult = withContext(Dispatchers.IO) {
    val shopUrl = DeviceManager.getPackageShopUrl()
    val cleanPrimary = primaryUrl.trim().ifBlank { shopUrl }

    fun probeUrl(target: String): Pair<Boolean, Int> {
        return try {
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }
            val code = conn.responseCode
            conn.disconnect()
            Pair(code in 200..399, code)
        } catch (_: Exception) {
            Pair(false, -1)
        }
    }

    val (primaryOk, primaryCode) = probeUrl(cleanPrimary)
    if (primaryOk) {
        return@withContext PortalReachabilityResult(
            state = PortalCheckState.ONLINE,
            resolvedUrl = cleanPrimary,
            statusMessage = "Web portalı aktif ve erişilebilir",
            httpCode = primaryCode
        )
    }

    if (!cleanPrimary.equals(shopUrl, ignoreCase = true)) {
        val (shopOk, shopCode) = probeUrl(shopUrl)
        if (shopOk) {
            return@withContext PortalReachabilityResult(
                state = PortalCheckState.FALLBACK_MAIN,
                resolvedUrl = shopUrl,
                statusMessage = "Paketler mağazası aktif (maxxplayers.com üzerinden açılacak)",
                httpCode = shopCode
            )
        }
    }

    val errorDetail = if (primaryCode > 0) "HTTP $primaryCode" else "Bağlantı kurulamadı"
    PortalReachabilityResult(
        state = PortalCheckState.OFFLINE,
        resolvedUrl = cleanPrimary,
        statusMessage = "Sunucu yanıt vermiyor ($errorDetail)",
        httpCode = primaryCode
    )
}

fun openExternalBrowserSafely(
    context: Context,
    url: String,
    onFallbackToInApp: (() -> Unit)? = null
) {
    val defaultShop = DeviceManager.getPackageShopUrl()
    val target = if (url.isBlank() || url.equals("https://maxxplayers.com", ignoreCase = true) || url.equals("https://maxxplayers.com/", ignoreCase = true) || url.contains("/my/maxx")) {
        defaultShop
    } else {
        url.trim()
    }
    val formattedUrl = target.let {
        if (!it.startsWith("http://") && !it.startsWith("https://")) "https://$it" else it
    }
    var started = false
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(intent)
        started = true
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(formattedUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            started = true
        } catch (_: Exception) {}
    }

    if (started) {
        Toast.makeText(context, "Paketler sayfasına yönlendiriliyor...", Toast.LENGTH_SHORT).show()
    } else {
        if (onFallbackToInApp != null) {
            onFallbackToInApp()
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Paket Satın Alma", formattedUrl))
            Toast.makeText(
                context,
                "Adres panoya kopyalandı: $formattedUrl",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun ProUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgraded: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val defaultPortalUrl = remember { DeviceManager.getWebPortalUrl() }
    var reachability by remember {
        mutableStateOf(
            PortalReachabilityResult(
                state = PortalCheckState.CHECKING,
                resolvedUrl = defaultPortalUrl,
                statusMessage = "Portal bağlantısı kontrol ediliyor..."
            )
        )
    }
    var showInAppWebPortal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        reachability = checkPortalReachability(defaultPortalUrl)
    }

    if (showInAppWebPortal) {
        WebPortalDialog(
            initialUrl = reachability.resolvedUrl,
            onDismiss = {
                showInAppWebPortal = false
                scope.launch {
                    try {
                        val uid = DeviceManager.getCurrentUserId()
                            ?: DeviceManager.getCurrentUserEmail()
                            ?: DeviceManager.getDeviceId()
                        OdooIntegrationManager.syncPlaylistsFromOdoo(context, uid)
                    } catch (_: Exception) {}
                }
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.example.util.KeepSystemBarsHidden()
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.62f else 0.90f)
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF151922),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = if (isLandscape) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Üst Kilit İkonu
                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 48.dp else 56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF6366F1).copy(alpha = 0.16f))
                        .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(if (isLandscape) 22.dp else 26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 14.dp))

                Text(
                    text = "Paket & Abonelik",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = if (isLandscape) 18.sp else 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Uygulama içerisinden satın alma işlemi yapılamamaktadır. Paket satın alma, yenileme ve süre uzatma işlemlerinin tamamı yalnızca resmi web portalımız üzerinden gerçekleştirilmektedir.",
                    color = Color(0xFF94A3B8),
                    fontSize = if (isLandscape) 12.sp else 13.sp,
                    lineHeight = if (isLandscape) 17.sp else 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 16.dp))

                // Web Portalı Kutusu
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showInAppWebPortal = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0E1117),
                    border = BorderStroke(1.dp, Color(0xFF262B36))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Web Portalı",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = defaultPortalUrl,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Web Portalı", reachability.resolvedUrl))
                                    Toast.makeText(context, "Portal adresi kopyalandı!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Adresi Kopyala",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Canlı Bağlantı Durumu Satırı
                        val statusColor = when (reachability.state) {
                            PortalCheckState.CHECKING -> Color(0xFF64B5F6)
                            PortalCheckState.ONLINE -> Color(0xFF4CAF50)
                            PortalCheckState.FALLBACK_MAIN -> Color(0xFFFFCA28)
                            PortalCheckState.OFFLINE -> Color(0xFFEF5350)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = reachability.statusMessage,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (reachability.state == PortalCheckState.OFFLINE) {
                                Text(
                                    text = "Tekrar Dene",
                                    color = Color(0xFF818CF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            reachability = PortalReachabilityResult(
                                                state = PortalCheckState.CHECKING,
                                                resolvedUrl = defaultPortalUrl,
                                                statusMessage = "Kontrol ediliyor..."
                                            )
                                            reachability = checkPortalReachability(defaultPortalUrl)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 16.dp))

                // Ana Buton: Paket Satın Al (Doğrudan tarayıcıda veya uygulama içinde açar)
                Button(
                    onClick = {
                        openExternalBrowserSafely(
                            context = context,
                            url = reachability.resolvedUrl,
                            onFallbackToInApp = { showInAppWebPortal = true }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E88E5),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paket Satın Al (maxxplayers.com)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // İkincil Butonlar: Uygulama İçinde Aç & Kapat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showInAppWebPortal = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Uygulama İçinde Aç",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.close_desc),
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPortalDialog(
    initialUrl: String = DeviceManager.getPackageShopUrl(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val deviceId = remember { DeviceManager.getDeviceId() }
    val deviceKey = remember { DeviceManager.getDeviceKey() }

    var currentUrl by remember { mutableStateOf(initialUrl.ifBlank { DeviceManager.getPackageShopUrl() }) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var triedMainFallback by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        com.example.util.KeepSystemBarsHidden()

        BackHandler(enabled = true) {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onDismiss()
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0E1117),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Üst Kontrol Barı
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canGoBack) {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = Color(0xFF818CF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Maxx Players Resmi Web Portalı",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Hızlı URL Geçişi (Paketler <-> Hesabım)
                    Surface(
                        onClick = {
                            val nextUrl = if (currentUrl.contains("/shop")) {
                                "https://maxxplayers.com/my/maxx"
                            } else {
                                DeviceManager.getPackageShopUrl()
                            }
                            pageError = null
                            isLoading = true
                            currentUrl = nextUrl
                            webViewRef?.loadUrl(nextUrl)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = if (currentUrl.contains("/shop")) "Hesabım" else "Paketler",
                            color = Color(0xFF93C5FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            pageError = null
                            isLoading = true
                            webViewRef?.reload()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            openExternalBrowserSafely(context, currentUrl)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Tarayıcıda Aç",
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kapat",
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Cihaz ID ve PIN Kopyalama Bilgi Barı (Portalda işlem yaparken kolaylık için)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111827))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Cihaz ID", deviceId))
                            Toast.makeText(context, "Cihaz ID kopyalandı: $deviceId", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Cihaz ID: ", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        Text(
                            text = deviceId,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(12.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("PIN", deviceKey))
                            Toast.makeText(context, "Eşleşme Kodu (PIN) kopyalandı: $deviceKey", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("PIN: ", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        Text(
                            text = deviceKey,
                            color = Color(0xFFFFCA28),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFFFCA28), modifier = Modifier.size(12.dp))
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF6366F1),
                        trackColor = Color.Transparent
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#0E1117"))

                                val wv = this
                                CookieManager.getInstance().apply {
                                    setAcceptCookie(true)
                                    setAcceptThirdPartyCookies(wv, true)
                                }

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadsImagesAutomatically = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(false)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress >= 95) {
                                            isLoading = false
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        isLoading = false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val reqUri = request?.url ?: return false
                                        val scheme = reqUri.scheme?.lowercase() ?: ""
                                        return if (scheme == "http" || scheme == "https") {
                                            false // WebView içerisinde aç, dışarı atma
                                        } else {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, reqUri).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                )
                                            } catch (_: Exception) {}
                                            true
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: WebResourceResponse?
                                    ) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        if (request?.isForMainFrame == true) {
                                            val statusCode = errorResponse?.statusCode ?: 0
                                            val failedUrl = request.url?.toString().orEmpty()
                                            if (statusCode >= 400) {
                                                if (!triedMainFallback && failedUrl.contains("/my/maxx")) {
                                                    triedMainFallback = true
                                                    currentUrl = "https://maxxplayers.com"
                                                    view?.post { view.loadUrl("https://maxxplayers.com") }
                                                } else if (statusCode >= 500 || statusCode == 404) {
                                                    pageError = "Web sunucusu hata döndürdü (HTTP $statusCode). Sunucu bakımda olabilir veya sayfa adresi değişmiş olabilir."
                                                }
                                            }
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            val failedUrl = request.url?.toString().orEmpty()
                                            if (!triedMainFallback && failedUrl.contains("/my/maxx")) {
                                                triedMainFallback = true
                                                currentUrl = "https://maxxplayers.com"
                                                view?.post { view.loadUrl("https://maxxplayers.com") }
                                            } else {
                                                val desc = error?.description?.toString() ?: "Bağlantı hatası"
                                                pageError = "Web sayfasına ulaşılamadı ($desc). Lütfen internet bağlantınızı veya sunucu durumunu kontrol edin."
                                                isLoading = false
                                            }
                                        }
                                    }
                                }

                                webViewRef = this
                                loadUrl(currentUrl)
                            }
                        }
                    )

                    if (pageError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0E1117))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(max = 380.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Web Portalı Yüklenemedi",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = pageError.orEmpty(),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Denenen Adres: $currentUrl",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            pageError = null
                                            isLoading = true
                                            webViewRef?.reload()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tekrar Dene", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            pageError = null
                                            isLoading = true
                                            val shopUrl = DeviceManager.getPackageShopUrl()
                                            currentUrl = shopUrl
                                            webViewRef?.loadUrl(shopUrl)
                                        },
                                        border = BorderStroke(1.dp, Color(0xFF6366F1)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Paketler Sayfası", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = { openExternalBrowserSafely(context, currentUrl) }
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dış Tarayıcıda Dene", color = Color(0xFF64B5F6), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

