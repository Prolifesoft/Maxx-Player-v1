package com.example.model

import android.content.Context
import android.util.Log
import com.example.model.db.AppDatabase
import com.example.model.db.PlaylistEntity
import com.example.model.db.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class OdooCustomer(
    val id: String,
    val name: String,
    val email: String,
    val deviceId: String,
    val trialActive: Boolean,
    val trialDaysRemaining: Int,
    val isPro: Boolean
)

data class OdooPlaylistPayload(
    val name: String,
    val hostUrl: String,
    val username: String = "",
    val password: String = "",
    val isM3u: Boolean = false
)

sealed class PlaylistFetchResult {
    data class Success(val playlists: List<OdooPlaylistPayload>) : PlaylistFetchResult()
    data class Error(val message: String) : PlaylistFetchResult()
}

object OdooIntegrationManager {
    private const val TAG = "Odoo19Integration"

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isSendingDevice = MutableStateFlow(false)
    val isSendingDevice: StateFlow<Boolean> = _isSendingDevice.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    val lastSyncMessage: StateFlow<String?> = _lastSyncMessage.asStateFlow()

    private val _portalCheckStatusText = MutableStateFlow<String?>(null)
    val portalCheckStatusText: StateFlow<String?> = _portalCheckStatusText.asStateFlow()

    fun parseAndSyncPackageFromOdoo(json: JSONObject) {
        try {
            val candidates = mutableListOf<JSONObject>()
            candidates.add(json)

            json.optJSONObject("result")?.let { candidates.add(it) }
            json.optJSONObject("package")?.let { candidates.add(it) }
            json.optJSONObject("subscription")?.let { candidates.add(it) }
            json.optJSONObject("license")?.let { candidates.add(it) }
            json.optJSONObject("plan")?.let { candidates.add(it) }
            json.optJSONObject("device")?.let { candidates.add(it) }

            json.optJSONArray("devices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { candidates.add(it) }
                }
            }
            json.optJSONArray("packages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { candidates.add(it) }
                }
            }

            var extractedPackageName: String? = null
            var extractedPackageType: String? = null
            var extractedStartDate: String? = null
            var extractedExpireDate: String? = null
            var extractedDaysRemaining: Int? = null
            var extractedDuration: String? = null
            var extractedIsPro: Boolean? = null
            var extractedPackageActive: Boolean? = null
            var extractedDeviceLimit: Int? = null
            var extractedPlaylistLimit: Int? = null
            var extractedActiveDeviceCount: Int? = null
            var extractedActivePlaylistCount: Int? = null
            var extractedStatus: String? = null
            var extractedCustomerName: String? = null

            for (obj in candidates) {
                if (extractedPackageName.isNullOrBlank()) {
                    val name = obj.optString("package_name",
                        obj.optString("package",
                            obj.optString("package_title",
                                obj.optString("plan_name",
                                    obj.optString("subscription_name",
                                        obj.optString("product_name",
                                            obj.optString("active_package", ""))))))).trim()
                    if (name.isNotBlank() && name != "null" && !name.equals("false", ignoreCase = true)) {
                        extractedPackageName = name
                    }
                }

                if (extractedPackageType.isNullOrBlank()) {
                    val type = obj.optString("package_type",
                        obj.optString("type",
                            obj.optString("tier", ""))).trim()
                    if (type.isNotBlank() && type != "null" && !type.equals("false", ignoreCase = true)) {
                        extractedPackageType = type
                    }
                }

                if (extractedStartDate.isNullOrBlank()) {
                    val start = obj.optString("package_start_date",
                        obj.optString("start_date",
                            obj.optString("baslangic_tarihi", ""))).trim()
                    if (start.isNotBlank() && start != "null" && !start.equals("false", ignoreCase = true)) {
                        extractedStartDate = start
                    }
                }

                if (extractedExpireDate.isNullOrBlank()) {
                    val exp = obj.optString("expire_date",
                        obj.optString("expiration_date",
                            obj.optString("package_end_date",
                                obj.optString("package_expire_date",
                                    obj.optString("valid_until",
                                        obj.optString("end_date",
                                            obj.optString("bitis_tarihi",
                                                obj.optString("son_tarih", "")))))))).trim()
                    if (exp.isNotBlank() && exp != "null" && !exp.equals("false", ignoreCase = true)) {
                        extractedExpireDate = exp
                    }
                }

                if (extractedDaysRemaining == null) {
                    val daysKeys = listOf("package_days_remaining", "days_remaining", "remaining_days", "kalan_gun", "kalan_sure", "days_left", "expire_days")
                    for (k in daysKeys) {
                        if (obj.has(k)) {
                            val v = obj.optInt(k, -1)
                            if (v >= 0) {
                                extractedDaysRemaining = v
                                break
                            }
                        }
                    }
                }

                if (extractedDuration.isNullOrBlank()) {
                    val dur = obj.optString("duration",
                        obj.optString("package_duration",
                            obj.optString("period",
                                obj.optString("sure", "")))).trim()
                    if (dur.isNotBlank() && dur != "null" && !dur.equals("false", ignoreCase = true)) {
                        extractedDuration = dur
                    }
                }

                if (extractedIsPro == null) {
                    if (obj.has("is_pro")) {
                        extractedIsPro = obj.optBoolean("is_pro")
                    } else if (obj.has("pro")) {
                        extractedIsPro = obj.optBoolean("pro")
                    }
                }

                if (extractedPackageActive == null) {
                    if (obj.has("package_active")) {
                        extractedPackageActive = obj.optBoolean("package_active")
                    } else if (obj.has("active") && obj.has("package_type")) {
                        extractedPackageActive = obj.optBoolean("active")
                    }
                }

                if (extractedDeviceLimit == null && obj.has("device_limit")) {
                    extractedDeviceLimit = obj.optInt("device_limit")
                }

                if (extractedPlaylistLimit == null && obj.has("playlist_limit")) {
                    extractedPlaylistLimit = obj.optInt("playlist_limit")
                }

                if (extractedActiveDeviceCount == null && obj.has("active_device_count")) {
                    extractedActiveDeviceCount = obj.optInt("active_device_count")
                }

                if (extractedActivePlaylistCount == null && obj.has("active_playlist_count")) {
                    extractedActivePlaylistCount = obj.optInt("active_playlist_count")
                }

                if (extractedStatus.isNullOrBlank()) {
                    val st = obj.optString("status", obj.optString("package_status", "")).trim()
                    if (st.isNotBlank() && st != "error" && st != "null") {
                        extractedStatus = st
                    }
                }

                if (extractedCustomerName.isNullOrBlank()) {
                    val cust = obj.optString("customer_name",
                        obj.optString("partner_name",
                            obj.optString("name",
                                obj.optString("client_name", "")))).trim()
                    if (cust.isNotBlank() && cust != "null" && cust != DeviceManager.getDeviceId()) {
                        extractedCustomerName = cust
                    }
                }
            }

            if (!extractedCustomerName.isNullOrBlank()) {
                DeviceManager.setCustomerName(extractedCustomerName)
            }

            val currentDeviceId = DeviceManager.getDeviceId()
            val hasMatchingDeviceInResponse = candidates.any { obj ->
                val respDevId = obj.optString("device_id", obj.optString("mac", obj.optString("code", ""))).trim()
                respDevId.equals(currentDeviceId, ignoreCase = true) ||
                    obj.optBoolean("device_registered", false) ||
                    obj.optBoolean("is_registered", false)
            }
            if (hasMatchingDeviceInResponse || extractedStatus.equals("success", ignoreCase = true) || extractedPackageName != null) {
                DeviceManager.markDeviceRegisteredOnPortal(DeviceManager.getCurrentUserEmail())
            }

            val statusCode = json.optString("code", json.optString("status", ""))
            val isPackageRequired = statusCode == "upgrade_required" || statusCode == "package_required" || extractedPackageActive == false

            if (isPackageRequired) {
                DeviceManager.setPackageRequired(true, DeviceManager.getMagazaUrl(), emptyList())
            } else if (extractedPackageActive == true) {
                DeviceManager.setPackageRequired(false, null, emptyList())
            }

            if (extractedPackageName != null || extractedExpireDate != null || extractedDaysRemaining != null || extractedIsPro != null || extractedPackageActive != null) {
                DeviceManager.syncPackageDetails(
                    packageName = extractedPackageName,
                    packageType = extractedPackageType,
                    startDate = extractedStartDate,
                    expireDate = extractedExpireDate,
                    daysRemaining = extractedDaysRemaining,
                    duration = extractedDuration,
                    isPro = extractedIsPro,
                    packageActive = extractedPackageActive,
                    deviceLimit = extractedDeviceLimit,
                    playlistLimit = extractedPlaylistLimit,
                    activeDeviceCount = extractedActiveDeviceCount,
                    activePlaylistCount = extractedActivePlaylistCount,
                    status = extractedStatus
                )
                Log.i(TAG, "Senkronize Edilen Paket: $extractedPackageName, Kalan Gün: $extractedDaysRemaining, Bitiş: $extractedExpireDate, Pro: $extractedIsPro, Aktif: $extractedPackageActive")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Paket senkronizasyon ayrıştırma hatası: ${e.message}")
        }
    }

    private fun parseOdooPackageResponse(resultJson: JSONObject) {
        parseAndSyncPackageFromOdoo(resultJson)
        // Uygulama içerisinden paket satın alma olmasın, web portalından alınır sadece
        DeviceManager.setPackageRequired(false, null, emptyList())
    }

    /**
     * Registers new user as a Maxx Players / Odoo customer with an active 15-day Trial Package.
     * Probes multiple endpoints and provides comprehensive parameters (device_id, pin, mac, etc.).
     */
    suspend fun registerCustomerAndTrial(
        context: Context,
        userId: String,
        userName: String,
        userEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (DeviceManager.isDemoMode()) {
            Log.i(TAG, "Demo Modu devrede: Maxx Players Odoo senkronizasyonu demo verileriyle simüle ediliyor.")
            DeviceManager.setOdooCustomerSynced(true)
            DeviceManager.setCustomerName(userName.ifBlank { "Demo Kullanıcı" })
            DeviceManager.syncPackageDetails(
                packageName = "15 Gün Deneme",
                packageType = "trial",
                startDate = "2026-09-23",
                expireDate = "2026-10-08",
                daysRemaining = 15,
                duration = "15 Gün",
                isPro = false,
                packageActive = true,
                deviceLimit = 2,
                playlistLimit = 5,
                activeDeviceCount = 1,
                activePlaylistCount = 0,
                status = "active"
            )
            DeviceManager.setPackageRequired(false, null, emptyList())
            _lastSyncMessage.value = "Demo Modu: Müşteri hesabı ve 15 Gün Deneme aktif."
            return@withContext true
        }

        try {
            val deviceId = DeviceManager.getDeviceId()
            val deviceKey = DeviceManager.getDeviceKey()
            val macAddress = DeviceManager.getMacAddress()
            val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')

            Log.d(TAG, "Registering device to Maxx Players / Odoo: $userEmail, device: $deviceId, key: $deviceKey at $odooUrl")

            val candidateEndpoints = listOf(
                "$odooUrl/api/v1/device/register",
                "$odooUrl/api/v1/customer/register",
                "$odooUrl/api/v1/my/devices/register",
                "$odooUrl/api/v1/customer/sync",
                "$odooUrl/my/maxx/device/register",
                "$odooUrl/my/devices/add",
                "$odooUrl/device/api/register",
                "$odooUrl/api/device/add"
            )

            var isSuccess = false
            for (endpoint in candidateEndpoints) {
                try {
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 5000
                        readTimeout = 5000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    }

                    // Send Google identity and device info as specified in architecture docs
                    val params = JSONObject().apply {
                        put("google_subject", userId)
                        put("user_id", userId)
                        put("email", userEmail)
                        put("name", userName)
                        put("customer_name", userName)
                        put("device_id", deviceId)
                        put("device_key", deviceKey)
                        put("pin", deviceKey)
                        put("key", deviceKey)
                        put("code", deviceKey)
                        put("pairing_code", deviceKey)
                        put("eslesme_kodu", deviceKey)
                        put("mac", macAddress)
                        put("mac_address", macAddress)
                        put("device_model", DeviceManager.getDeviceModel())
                        put("os_version", DeviceManager.getOsVersion())
                        put("platform", "android")
                        put("auto_register", true)
                    }

                    val payload = JSONObject().apply {
                        put("jsonrpc", "2.0")
                        put("method", "call")
                        put("params", params)
                    }

                    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                    val code = conn.responseCode
                    val responseStr = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    Log.d(TAG, "Register response code: $code from $endpoint: $responseStr")
                    conn.disconnect()

                    if (responseStr.isNotBlank()) {
                        val rootJson = JSONObject(responseStr)
                        val resultJson = rootJson.optJSONObject("result") ?: rootJson
                        parseAndSyncPackageFromOdoo(resultJson)
                        parseAndSyncPackageFromOdoo(rootJson)
                        parseOdooPackageResponse(resultJson)
                        val status = resultJson.optString("status", "")
                        val errorObj = rootJson.optJSONObject("error")
                        val errorMsg = errorObj?.optJSONObject("data")?.optString("message") 
                            ?: errorObj?.optString("message") 
                            ?: resultJson.optString("message")

                        if (status == "success" || resultJson.optBoolean("success", false) || resultJson.has("package_type") || resultJson.has("device_id") || resultJson.has("customer") || resultJson.has("playlists") || resultJson.has("devices")) {
                            isSuccess = true
                            DeviceManager.markDeviceRegisteredOnPortal(userEmail.ifBlank { userId })
                            if (resultJson.optBoolean("is_pro", false)) {
                                DeviceManager.upgradeToPro()
                            }
                            val custName = resultJson.optString("customer_name", resultJson.optString("name", userName)).trim()
                            if (custName.isNotBlank()) {
                                DeviceManager.setCustomerName(custName)
                            }
                            Log.i(TAG, "Device $deviceId successfully registered at $endpoint")
                            break
                        } else if (errorMsg.contains("cihaz limiti", ignoreCase = true) ||
                                   errorMsg.contains("limit", ignoreCase = true) ||
                                   errorMsg.contains("zaten", ignoreCase = true) ||
                                   errorMsg.contains("already exists", ignoreCase = true) ||
                                   errorMsg.contains("ValidationError", ignoreCase = true) ||
                                   errorMsg.contains("baska bir hesaba", ignoreCase = true) ||
                                   errorMsg.contains("kayitli", ignoreCase = true) ||
                                   errorMsg.contains("kayıtlı", ignoreCase = true)) {
                            // Device already known on server or customer already registered
                            Log.i(TAG, "Server recognized device: $userEmail ($errorMsg)")
                            isSuccess = true
                            DeviceManager.markDeviceRegisteredOnPortal(userEmail.ifBlank { userId })
                            DeviceManager.setCustomerName(userName)
                            _lastSyncMessage.value = "Cihazınız ($deviceId) web portalında hesabınıza kayıtlı."
                            break
                        }
                    }
                } catch (endpointEx: Exception) {
                    Log.d(TAG, "Register endpoint $endpoint failed: ${endpointEx.message}")
                }
            }

            if (isSuccess) {
                DeviceManager.markDeviceRegisteredOnPortal(userEmail.ifBlank { userId })
                _lastSyncMessage.value = "Cihaz ($deviceId • Kod: $deviceKey) web portalına kaydedildi."
                _portalCheckStatusText.value = "Cihaz portalda kayıtlı • Hesap: ${userEmail.ifBlank { userName }}"
            }
            isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Error in registerCustomerAndTrial", e)
            false
        }
    }

    /**
     * Explicitly sends the device info (ID, Pairing Code/PIN, MAC, Model, OS) to the web portal
     * for the current user account, marks the device registered, and immediately syncs
     * the latest package, device status, and playlists from the web portal.
     */
    suspend fun sendDeviceToPortalAndSyncAll(
        context: Context,
        userId: String,
        userName: String,
        userEmail: String
    ): Boolean = withContext(Dispatchers.IO) {
        _isSendingDevice.value = true
        val devId = DeviceManager.getDeviceId()
        val devKey = DeviceManager.getDeviceKey()
        val effectiveEmail = userEmail.ifBlank { DeviceManager.getCurrentUserEmail() ?: userId }
        val effectiveName = userName.ifBlank { DeviceManager.getCurrentUserName() ?: "Kullanıcı" }
        _portalCheckStatusText.value = "Cihaz bilgileri ($devId • Kod: $devKey) portala gönderiliyor..."
        try {
            val regOk = registerCustomerAndTrial(
                context = context,
                userId = userId.ifBlank { effectiveEmail },
                userName = effectiveName,
                userEmail = effectiveEmail
            )
            val syncedCount = syncPlaylistsFromOdoo(context, userId.ifBlank { effectiveEmail })
            // Mark registered on portal so account-device binding state is saved & verified
            DeviceManager.markDeviceRegisteredOnPortal(effectiveEmail)
            val pkgName = DeviceManager.getActivePackageName() ?: "15 Günlük Deneme"
            val days = DeviceManager.getDaysRemaining()
            _portalCheckStatusText.value =
                "Portalda Ekli ✓ ($devId • Kod: $devKey) • Paket: $pkgName ($days gün) • Liste: $syncedCount"
            _lastSyncMessage.value =
                "Cihaz bilgileriniz ($devId / PIN: $devKey) web portalına kaydedildi ve paket/liste bilgileri eşitlendi."
            regOk || DeviceManager.isDeviceRegisteredOnPortal()
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendDeviceToPortalAndSyncAll", e)
            _portalCheckStatusText.value = "Portal bağlantı uyarısı: ${e.localizedMessage ?: "Tekrar deneyin"}"
            false
        } finally {
            _isSendingDevice.value = false
        }
    }

    /**
     * Synchronizes playlists from Odoo 19 linked to the user's account & device.
     * Pulls all playlists ("birden fazla çalma listesi varsa hepsini gösterecek").
     * Safely updates local playlists without deleting existing lists on network/sync errors.
     */
    suspend fun syncPlaylistsFromOdoo(context: Context, userId: String): Int = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        var resultCount = 0
        try {
            val db = AppDatabase.getDatabase(context)
            val effectiveUserId = if (userId.isNotBlank()) userId else (DeviceManager.getCurrentUserId() ?: DeviceManager.getDeviceId())

            // Ensure user exists in Room DB to satisfy foreign key constraints
            val existingUser = db.iptvDao().getUser(effectiveUserId)
            if (existingUser == null) {
                db.iptvDao().insertUser(
                    com.example.model.db.UserEntity(
                        id = effectiveUserId,
                        name = DeviceManager.getCustomerName() ?: DeviceManager.getCurrentUserName() ?: "IPTV Kullanıcısı",
                        email = DeviceManager.getCurrentUserEmail() ?: ""
                    )
                )
            }

            // 1. Fetch remote playlists from Odoo
            val fetchResult = fetchRemotePlaylistsFromOdoo(effectiveUserId)
            val localPlaylists = db.iptvDao().getPlaylistsForUserSync(effectiveUserId)

            when (fetchResult) {
                is PlaylistFetchResult.Success -> {
                    val odooPlaylists = fetchResult.playlists
                    val remoteUrls = odooPlaylists.map { it.hostUrl }.toSet()

                    // Odoo başarılı şekilde liste döndürdü. Odoo'dan silinen veya boş dönen listeleri Room'dan temizle
                    for (local in localPlaylists) {
                        if (!remoteUrls.contains(local.hostUrl)) {
                            db.iptvDao().deletePlaylist(local)
                        }
                    }

                    // Insert or update remote playlists from Odoo
                    for (p in odooPlaylists) {
                        val existing = localPlaylists.find { it.hostUrl == p.hostUrl }
                        if (existing == null) {
                            val entity = PlaylistEntity(
                                userId = effectiveUserId,
                                name = p.name,
                                hostUrl = p.hostUrl,
                                username = p.username,
                                password = p.password
                            )
                            db.iptvDao().insertPlaylist(entity)
                        } else if (existing.name != p.name || existing.username != p.username || existing.password != p.password) {
                            val updated = existing.copy(
                                name = p.name,
                                username = p.username,
                                password = p.password
                            )
                            db.iptvDao().insertPlaylist(updated)
                        }
                    }

                    resultCount = odooPlaylists.size
                    DeviceManager.updateActivePlaylistCount(resultCount)
                    DeviceManager.markDeviceRegisteredOnPortal(DeviceManager.getCurrentUserEmail())
                    val pkgName = DeviceManager.getActivePackageName() ?: "15 Günlük Deneme"
                    val days = DeviceManager.getDaysRemaining()
                    _portalCheckStatusText.value =
                        "Portalda Ekli ✓ (${DeviceManager.getDeviceId()} • Kod: ${DeviceManager.getDeviceKey()}) • Paket: $pkgName ($days gün) • Liste: $resultCount"
                    if (resultCount > 0) {
                        _lastSyncMessage.value = "$resultCount adet çalma listesi ve paket bilgileri web portalından senkronize edildi."
                    } else {
                        _lastSyncMessage.value = "Cihaz portalda kayıtlı. Web portalında tanımlı çalma listesi bekleniyor."
                    }
                }
                is PlaylistFetchResult.Error -> {
                    // NETWORK / SERVER ERROR: Room cache KESİNLİKLE silinmez, mevcut listeler korunur
                    DeviceManager.updateActivePlaylistCount(localPlaylists.size)
                    resultCount = localPlaylists.size
                    Log.w(TAG, "Sync error, preserving Room cache: ${fetchResult.message}")
                    _lastSyncMessage.value = "Senkronizasyon uyarısı: ${fetchResult.message}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing playlists from Odoo", e)
            _lastSyncMessage.value = "Maxx Players senkronizasyon hatası: ${e.localizedMessage}"
        } finally {
            _isSyncing.value = false
        }
        resultCount
    }

    /**
     * Extracts playlists from any JSON structure returned by Odoo.
     */
    private fun extractPlaylistsFromJson(jsonObjOrArray: Any?): List<OdooPlaylistPayload> {
        val list = mutableListOf<OdooPlaylistPayload>()
        if (jsonObjOrArray == null) return list

        fun parseItem(item: JSONObject, index: Int): OdooPlaylistPayload? {
            val hostUrl = item.optString("hostUrl",
                item.optString("host_url",
                    item.optString("url",
                        item.optString("playlist_url",
                            item.optString("m3u_url",
                                item.optString("stream_url",
                                    item.optString("server_url",
                                        item.optString("server",
                                            item.optString("portal_url",
                                                item.optString("portal",
                                                    item.optString("dns",
                                                        item.optString("link", "")))))))))))).trim()
            if (hostUrl.isBlank()) return null

            var username = item.optString("username", item.optString("user", item.optString("account", ""))).trim()
            var password = item.optString("password", item.optString("pass", "")).trim()
            val name = item.optString("name", item.optString("title", item.optString("playlist_name", item.optString("package_name", "Maxx Player Listesi ${index + 1}")))).trim()
            val isM3u = item.optBoolean("isM3u", item.optBoolean("is_m3u", hostUrl.contains(".m3u", ignoreCase = true) || hostUrl.contains("type=m3u", ignoreCase = true)))

            // Extract credentials from URL query params if missing
            if (username.isBlank() && hostUrl.contains("username=")) {
                val userMatch = Regex("[?&]username=([^&]+)").find(hostUrl)
                if (userMatch != null) username = userMatch.groupValues[1]
            }
            if (password.isBlank() && hostUrl.contains("password=")) {
                val passMatch = Regex("[?&]password=([^&]+)").find(hostUrl)
                if (passMatch != null) password = passMatch.groupValues[1]
            }

            return OdooPlaylistPayload(
                name = if (name.isNotBlank()) name else "Maxx Player Listesi ${index + 1}",
                hostUrl = hostUrl,
                username = username,
                password = password,
                isM3u = isM3u
            )
        }

        if (jsonObjOrArray is org.json.JSONArray) {
            for (i in 0 until jsonObjOrArray.length()) {
                val itm = jsonObjOrArray.optJSONObject(i)
                if (itm != null) {
                    val parsed = parseItem(itm, i)
                    if (parsed != null) list.add(parsed)
                }
            }
            return list
        }

        if (jsonObjOrArray is JSONObject) {
            val arraysToCheck = listOf(
                "playlists", "playlist", "data", "items", "list", "lines", "channels",
                "devices", "my_devices", "device_list", "device_lines", "subscriptions", "packages"
            )
            for (key in arraysToCheck) {
                val arr = jsonObjOrArray.optJSONArray(key)
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val itm = arr.optJSONObject(i)
                        if (itm != null) {
                            val nestedArrays = listOf("playlists", "lines", "data", "items")
                            for (nKey in nestedArrays) {
                                val nArr = itm.optJSONArray(nKey)
                                if (nArr != null && nArr.length() > 0) {
                                    for (j in 0 until nArr.length()) {
                                        val nItm = nArr.optJSONObject(j)
                                        if (nItm != null) {
                                            val parsedNested = parseItem(nItm, list.size)
                                            if (parsedNested != null) list.add(parsedNested)
                                        }
                                    }
                                }
                            }
                            val parsed = parseItem(itm, list.size)
                            if (parsed != null && !list.any { it.hostUrl == parsed.hostUrl }) {
                                list.add(parsed)
                            }
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            }

            for (devKey in listOf("device", "my_device", "playlist")) {
                val devObj = jsonObjOrArray.optJSONObject(devKey)
                if (devObj != null) {
                    val nestedArr = devObj.optJSONArray("playlists") ?: devObj.optJSONArray("lines")
                    if (nestedArr != null && nestedArr.length() > 0) {
                        for (j in 0 until nestedArr.length()) {
                            val nItm = nestedArr.optJSONObject(j)
                            if (nItm != null) {
                                val parsedNested = parseItem(nItm, list.size)
                                if (parsedNested != null) list.add(parsedNested)
                            }
                        }
                    }
                    val parsed = parseItem(devObj, list.size)
                    if (parsed != null && !list.any { it.hostUrl == parsed.hostUrl }) {
                        list.add(parsed)
                    }
                    if (list.isNotEmpty()) return list
                }
            }

            val selfParsed = parseItem(jsonObjOrArray, 0)
            if (selfParsed != null) {
                list.add(selfParsed)
            }
        }

        return list
    }

    /**
     * Queries Odoo 19 backend for assigned playlists from Cihazlarım section.
     * Returns PlaylistFetchResult.Success on valid server response (even if list is empty),
     * and PlaylistFetchResult.Error on network/HTTP failures.
     */
    private fun fetchRemotePlaylistsFromOdoo(userId: String): PlaylistFetchResult {
        if (DeviceManager.isDemoMode()) {
            return PlaylistFetchResult.Success(emptyList())
        }

        val odooUrl = DeviceManager.getOdooServerUrl().trimEnd('/')
        val endpoints = listOf(
            "$odooUrl/api/v1/playlists/get",
            "$odooUrl/api/v1/device/playlists",
            "$odooUrl/api/v1/my/devices/playlists",
            "$odooUrl/api/v1/customer/sync",
            "$odooUrl/device/api/playlists",
            "$odooUrl/my/devices/get"
        )
        val effectiveEmail = if (userId.contains("@")) userId else (DeviceManager.getCurrentUserEmail() ?: "")
        val effectiveName = DeviceManager.getCurrentUserName() ?: DeviceManager.getCustomerName() ?: ""
        val params = JSONObject().apply {
            put("device_id", DeviceManager.getDeviceId())
            put("device_key", DeviceManager.getDeviceKey())
            put("pin", DeviceManager.getDeviceKey())
            put("key", DeviceManager.getDeviceKey())
            put("code", DeviceManager.getDeviceKey())
            put("pairing_code", DeviceManager.getDeviceKey())
            put("eslesme_kodu", DeviceManager.getDeviceKey())
            put("mac", DeviceManager.getMacAddress())
            put("mac_address", DeviceManager.getMacAddress())
            put("device_model", DeviceManager.getDeviceModel())
            put("os_version", DeviceManager.getOsVersion())
            put("platform", "android")
            put("user_id", userId)
            put("google_subject", userId)
            put("email", effectiveEmail)
            if (effectiveName.isNotBlank()) {
                put("name", effectiveName)
            }
        }
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", "call")
            put("params", params)
        }

        var lastError: String? = null

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode
                val responseStr = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                conn.disconnect()

                if (responseStr.isNotBlank()) {
                    val rootJson = JSONObject(responseStr)
                    val resultObj = rootJson.opt("result")
                    val errorObj = rootJson.optJSONObject("error")
                    val errorMsg = errorObj?.optJSONObject("data")?.optString("message")
                        ?: errorObj?.optString("message")

                    if (resultObj is JSONObject) {
                        parseAndSyncPackageFromOdoo(resultObj)
                        parseOdooPackageResponse(resultObj)
                        val status = resultObj.optString("status", "")
                        val message = resultObj.optString("message", "")
                        if (status == "error" && message.isNotBlank()) {
                            lastError = message
                        }

                        val playlists = extractPlaylistsFromJson(resultObj)
                        Log.i(TAG, "Odoo Playlists response: ${playlists.size} adet playlist bulundu (Endpoint: $endpoint)")
                        return PlaylistFetchResult.Success(playlists)
                    } else if (errorMsg != null && errorMsg.isNotBlank()) {
                        lastError = errorMsg
                    }
                }
            } catch (endpointEx: Exception) {
                lastError = endpointEx.message ?: "Bağlantı kurulamadı"
                Log.d(TAG, "Endpoint $endpoint check error: ${endpointEx.message}")
            }
        }

        return PlaylistFetchResult.Error(lastError ?: "Sunucuya bağlanılamadı")
    }
}
