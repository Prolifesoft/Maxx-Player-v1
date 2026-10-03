package com.example.model

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

object DeviceManager {
    private const val PREFS_NAME = "device_license_prefs"
    private const val KEY_DEVICE_ID = "pref_device_id"
    private const val KEY_DEVICE_KEY = "pref_device_key"
    private const val KEY_TRIAL_START = "pref_trial_start"
    private const val KEY_IS_PRO = "pref_is_pro"
    private const val KEY_EXPIRED_TEST = "pref_expired_test_mode"
    private const val KEY_ODOO_SYNCED = "pref_odoo_customer_synced"
    private const val KEY_ODOO_SERVER = "pref_odoo_server_url"
    private const val KEY_CUSTOMER_NAME = "pref_odoo_customer_name"
    private const val KEY_PACKAGE_NAME = "current_package_name"
    private const val KEY_PACKAGE_TYPE = "pref_package_type"
    private const val KEY_PACKAGE_START_DATE = "pref_package_start_date"
    private const val KEY_PACKAGE_EXPIRE_DATE = "pref_package_expire_date"
    private const val KEY_PACKAGE_DAYS_LEFT = "pref_package_days_left"
    private const val KEY_PACKAGE_DURATION = "pref_package_duration"
    private const val KEY_PACKAGE_STATUS = "pref_package_status"
    private const val KEY_PACKAGE_ACTIVE = "pref_package_active"
    private const val KEY_DEVICE_LIMIT = "pref_device_limit"
    private const val KEY_PLAYLIST_LIMIT = "pref_playlist_limit"
    private const val KEY_ACTIVE_DEVICE_COUNT = "pref_active_device_count"
    private const val KEY_ACTIVE_PLAYLIST_COUNT = "pref_active_playlist_count"
    private const val KEY_SERVER_SYNC_TIME = "pref_server_sync_time"
    private const val KEY_CURRENT_USER_PHOTO = "current_user_photo"
    private const val KEY_DEMO_MODE = "pref_demo_mode"
    private const val KEY_PORTAL_DEVICE_REGISTERED = "pref_portal_device_registered"
    private const val KEY_PORTAL_REGISTERED_EMAIL = "pref_portal_registered_email"
    private const val KEY_PORTAL_REGISTERED_DEVICE_ID = "pref_portal_registered_device_id"
    private const val KEY_PORTAL_LAST_SYNC_TEXT = "pref_portal_last_sync_text"

    private lateinit var prefs: SharedPreferences

    private val _customerNameState = MutableStateFlow<String?>(null)
    val customerNameState: StateFlow<String?> = _customerNameState.asStateFlow()

    private val _activePackageNameState = MutableStateFlow("15 Günlük Deneme")
    val activePackageNameState: StateFlow<String> = _activePackageNameState.asStateFlow()

    private val _packageTypeState = MutableStateFlow("trial")
    val packageTypeState: StateFlow<String> = _packageTypeState.asStateFlow()

    private val _packageStartDateState = MutableStateFlow<String?>(null)
    val packageStartDateState: StateFlow<String?> = _packageStartDateState.asStateFlow()

    private val _packageExpireDateState = MutableStateFlow<String?>(null)
    val packageExpireDateState: StateFlow<String?> = _packageExpireDateState.asStateFlow()

    private val _packageDurationState = MutableStateFlow<String?>(null)
    val packageDurationState: StateFlow<String?> = _packageDurationState.asStateFlow()

    private val _trialDaysLeft = MutableStateFlow(15)
    val trialDaysLeft: StateFlow<Int> = _trialDaysLeft.asStateFlow()

    private val _isProOrInTrialState = MutableStateFlow(true)
    val isProOrInTrialState: StateFlow<Boolean> = _isProOrInTrialState.asStateFlow()

    private val _isProState = MutableStateFlow(false)
    val isProState: StateFlow<Boolean> = _isProState.asStateFlow()

    private val _packageActiveState = MutableStateFlow(true)
    val packageActiveState: StateFlow<Boolean> = _packageActiveState.asStateFlow()

    private val _deviceLimitState = MutableStateFlow(2)
    val deviceLimitState: StateFlow<Int> = _deviceLimitState.asStateFlow()

    private val _playlistLimitState = MutableStateFlow(5)
    val playlistLimitState: StateFlow<Int> = _playlistLimitState.asStateFlow()

    private val _activeDeviceCountState = MutableStateFlow(1)
    val activeDeviceCountState: StateFlow<Int> = _activeDeviceCountState.asStateFlow()

    private val _activePlaylistCountState = MutableStateFlow(0)
    val activePlaylistCountState: StateFlow<Int> = _activePlaylistCountState.asStateFlow()

    private val _demoModeState = MutableStateFlow(false)
    val demoModeState: StateFlow<Boolean> = _demoModeState.asStateFlow()

    private val _portalDeviceRegisteredState = MutableStateFlow(false)
    val portalDeviceRegisteredState: StateFlow<Boolean> = _portalDeviceRegisteredState.asStateFlow()

    private val _portalRegisteredEmailState = MutableStateFlow<String?>(null)
    val portalRegisteredEmailState: StateFlow<String?> = _portalRegisteredEmailState.asStateFlow()

    private val _portalLastSyncTimeState = MutableStateFlow<String?>(null)
    val portalLastSyncTimeState: StateFlow<String?> = _portalLastSyncTimeState.asStateFlow()

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ensureDeviceId(context.applicationContext)
            refreshLicenseState()
        }
    }

    @SuppressLint("HardwareIds")
    private fun ensureDeviceId(context: Context) {
        if (!prefs.contains(KEY_DEVICE_ID)) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) {
                null
            }
            val cleanId = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                val formatted = androidId.uppercase(Locale.ROOT).takeLast(8)
                "MAX-${formatted.take(4)}-${formatted.takeLast(4)}"
            } else {
                val uuid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT).take(8)
                "MAX-${uuid.take(4)}-${uuid.takeLast(4)}"
            }

            // Generate 6-digit PIN key
            val pin = (100000..999999).random().toString()

            prefs.edit()
                .putString(KEY_DEVICE_ID, cleanId)
                .putString(KEY_DEVICE_KEY, pin)
                .putLong(KEY_TRIAL_START, System.currentTimeMillis())
                .apply()
        }
    }

    fun setDeviceCredentials(newId: String, pin: String) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DEVICE_KEY, pin)
            .putBoolean(KEY_ODOO_SYNCED, true)
            .putBoolean(KEY_PORTAL_DEVICE_REGISTERED, true)
            .putString(KEY_PORTAL_REGISTERED_DEVICE_ID, newId)
            .apply()
        refreshLicenseState()
    }

    fun generateNewDeviceCredentials() {
        val uuid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT).take(8)
        val newId = "MAX-${uuid.take(4)}-${uuid.takeLast(4)}"
        val pin = (100000..999999).random().toString()
        prefs.edit()
            .putString(KEY_DEVICE_ID, newId)
            .putString(KEY_DEVICE_KEY, pin)
            .putBoolean(KEY_ODOO_SYNCED, false)
            .putBoolean(KEY_PORTAL_DEVICE_REGISTERED, false)
            .apply()
        refreshLicenseState()
    }

    fun getDeviceId(): String {
        var id = if (::prefs.isInitialized) prefs.getString(KEY_DEVICE_ID, "") ?: "" else ""
        if (id.isBlank() || id == "MAX-0000-0000") {
            generateNewDeviceCredentials()
            id = prefs.getString(KEY_DEVICE_ID, "") ?: ""
        }
        return id
    }

    fun getDeviceKey(): String {
        var pin = if (::prefs.isInitialized) prefs.getString(KEY_DEVICE_KEY, "") ?: "" else ""
        if (pin.isBlank() || pin == "123456") {
            generateNewDeviceCredentials()
            pin = prefs.getString(KEY_DEVICE_KEY, "") ?: ""
        }
        return pin
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        val model = Build.MODEL
        return "$manufacturer $model"
    }

    fun getOsVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    fun getMacAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null && mac.isNotEmpty()) {
                    val sb = StringBuilder()
                    for (b in mac) {
                        sb.append(String.format("%02X:", b))
                    }
                    if (sb.isNotEmpty()) {
                        sb.deleteCharAt(sb.length - 1)
                    }
                    val macStr = sb.toString()
                    if (macStr != "02:00:00:00:00:00") {
                        return macStr
                    }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        val id = getDeviceId().replace("MAX-", "").replace("-", "")
        return if (id.length >= 8) {
            "${id.substring(0,2)}:${id.substring(2,4)}:${id.substring(4,6)}:${id.substring(6,8)}:5E:21"
        } else {
            "02:42:AC:11:00:22"
        }
    }

    fun getTrialStartDate(): Long {
        return prefs.getLong(KEY_TRIAL_START, System.currentTimeMillis())
    }

    fun getDaysRemaining(): Int {
        if (isExpiredTestMode()) return 0
        if (!::prefs.isInitialized) return 15
        if (prefs.contains(KEY_PACKAGE_DAYS_LEFT)) {
            val baseDays = prefs.getInt(KEY_PACKAGE_DAYS_LEFT, 15)
            val syncTime = prefs.getLong(KEY_SERVER_SYNC_TIME, System.currentTimeMillis())
            val daysPassed = ((System.currentTimeMillis() - syncTime) / (1000L * 60 * 60 * 24)).toInt()
            val remaining = baseDays - daysPassed
            return if (remaining < 0) 0 else remaining
        }
        val trialStart = prefs.getLong(KEY_TRIAL_START, System.currentTimeMillis())
        val daysPassed = ((System.currentTimeMillis() - trialStart) / (1000L * 60 * 60 * 24)).toInt()
        val remaining = 15 - daysPassed
        return if (remaining < 0) 0 else remaining
    }

    fun isCreditItem(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase(Locale.ROOT)
        val lowerTr = name.lowercase(Locale.forLanguageTag("tr"))
        val keywords = listOf("kredi", "kredı", "credit", "credits", "bakiye", "jeton", "coin", "token")
        return keywords.any { lower.contains(it) || lowerTr.contains(it) }
    }

    fun setActivePackageName(name: String?) {
        if (isCreditItem(name)) {
            prefs.edit().remove(KEY_PACKAGE_NAME).apply()
            _activePackageNameState.value = if (isProPurchased()) "PRO Paket" else if (isTrialActive()) "15 Günlük Deneme" else "Süresi Dolmuş Paket"
            return
        }
        prefs.edit().putString(KEY_PACKAGE_NAME, name).apply()
        _activePackageNameState.value = name ?: (if (isProPurchased()) "PRO Paket" else if (isTrialActive()) "15 Günlük Deneme" else "Süresi Dolmuş Paket")
    }

    fun getActivePackageName(): String? {
        if (!::prefs.isInitialized) return null
        val stored = prefs.getString(KEY_PACKAGE_NAME, null)
        if (stored != null && isCreditItem(stored)) {
            prefs.edit().remove(KEY_PACKAGE_NAME).apply()
            return null
        }
        return stored
    }

    fun getPackageExpireDate(): String? = if (::prefs.isInitialized) prefs.getString(KEY_PACKAGE_EXPIRE_DATE, null) else null

    fun getPackageStartDate(): String? = if (::prefs.isInitialized) prefs.getString(KEY_PACKAGE_START_DATE, null) else null

    fun getPackageType(): String = if (::prefs.isInitialized) prefs.getString(KEY_PACKAGE_TYPE, if (isProPurchased()) "pro" else "trial") ?: "trial" else "trial"

    fun getPackageDuration(): String? = if (::prefs.isInitialized) prefs.getString(KEY_PACKAGE_DURATION, null) else null

    fun getDeviceLimit(): Int = if (::prefs.isInitialized) prefs.getInt(KEY_DEVICE_LIMIT, 2) else 2

    fun getPlaylistLimit(): Int = if (::prefs.isInitialized) prefs.getInt(KEY_PLAYLIST_LIMIT, 5) else 5

    fun getActiveDeviceCount(): Int = if (::prefs.isInitialized) prefs.getInt(KEY_ACTIVE_DEVICE_COUNT, 1) else 1

    fun getActivePlaylistCount(): Int = if (::prefs.isInitialized) prefs.getInt(KEY_ACTIVE_PLAYLIST_COUNT, 0) else 0

    fun isPackageActive(): Boolean {
        if (!::prefs.isInitialized) return isDemoMode()
        return prefs.getBoolean(KEY_PACKAGE_ACTIVE, isProPurchased() || getDaysRemaining() > 0)
    }

    fun isPackageExpired(): Boolean = !isPackageActive() || (getDaysRemaining() <= 0 && !isProPurchased())

    fun isDemoMode(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_DEMO_MODE, false) else false
    }

    fun setDemoMode(enabled: Boolean) {
        if (::prefs.isInitialized) {
            prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        }
        _demoModeState.value = enabled
        refreshLicenseState()
    }

    fun syncPackageDetails(
        packageName: String?,
        packageType: String? = null,
        startDate: String? = null,
        expireDate: String?,
        daysRemaining: Int?,
        duration: String? = null,
        isPro: Boolean?,
        packageActive: Boolean? = null,
        deviceLimit: Int? = null,
        playlistLimit: Int? = null,
        activeDeviceCount: Int? = null,
        activePlaylistCount: Int? = null,
        status: String? = null
    ) {
        val editor = prefs.edit()
        if (!packageName.isNullOrBlank()) {
            if (!isCreditItem(packageName)) {
                editor.putString(KEY_PACKAGE_NAME, packageName.trim())
            } else {
                editor.remove(KEY_PACKAGE_NAME)
            }
        }
        if (!packageType.isNullOrBlank()) {
            editor.putString(KEY_PACKAGE_TYPE, packageType.trim())
        }
        if (!startDate.isNullOrBlank()) {
            editor.putString(KEY_PACKAGE_START_DATE, startDate.trim())
        }
        if (!expireDate.isNullOrBlank()) {
            editor.putString(KEY_PACKAGE_EXPIRE_DATE, expireDate.trim())
        }
        if (daysRemaining != null && daysRemaining >= 0) {
            editor.putInt(KEY_PACKAGE_DAYS_LEFT, daysRemaining)
            editor.putLong(KEY_SERVER_SYNC_TIME, System.currentTimeMillis())
        }
        if (!duration.isNullOrBlank()) {
            editor.putString(KEY_PACKAGE_DURATION, duration.trim())
        }
        if (!status.isNullOrBlank()) {
            editor.putString(KEY_PACKAGE_STATUS, status.trim())
        }
        if (packageActive != null) {
            editor.putBoolean(KEY_PACKAGE_ACTIVE, packageActive)
        }
        if (deviceLimit != null && deviceLimit > 0) {
            editor.putInt(KEY_DEVICE_LIMIT, deviceLimit)
        }
        if (playlistLimit != null && playlistLimit > 0) {
            editor.putInt(KEY_PLAYLIST_LIMIT, playlistLimit)
        }
        if (activeDeviceCount != null) {
            editor.putInt(KEY_ACTIVE_DEVICE_COUNT, activeDeviceCount)
        }
        if (activePlaylistCount != null) {
            editor.putInt(KEY_ACTIVE_PLAYLIST_COUNT, activePlaylistCount)
        }
        if (isPro != null) {
            editor.putBoolean(KEY_IS_PRO, isPro)
            if (isPro) editor.putBoolean(KEY_EXPIRED_TEST, false)
        } else if (packageType.equals("pro", ignoreCase = true) || packageType.equals("lifetime", ignoreCase = true)) {
            editor.putBoolean(KEY_IS_PRO, true)
            editor.putBoolean(KEY_EXPIRED_TEST, false)
        }
        editor.apply()
        refreshLicenseState()
    }

    fun isTrialActive(): Boolean {
        if (isExpiredTestMode()) return false
        return isPackageActive() && getDaysRemaining() > 0
    }

    fun isProPurchased(): Boolean {
        return prefs.getBoolean(KEY_IS_PRO, false)
    }

    fun isProOrInTrial(): Boolean {
        return isProPurchased() || (isPackageActive() && isTrialActive())
    }

    fun isExpiredTestMode(): Boolean {
        return prefs.getBoolean(KEY_EXPIRED_TEST, false)
    }

    fun setExpiredTestMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXPIRED_TEST, enabled).apply()
        refreshLicenseState()
    }

    fun upgradeToPro() {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putBoolean(KEY_PACKAGE_ACTIVE, true)
            .putString(KEY_PACKAGE_TYPE, "pro")
            .putString(KEY_PACKAGE_NAME, "Maxx Players PRO")
            .putBoolean(KEY_EXPIRED_TEST, false)
            .apply()
        refreshLicenseState()
    }

    fun resetTrial() {
        prefs.edit()
            .putLong(KEY_TRIAL_START, System.currentTimeMillis())
            .putBoolean(KEY_EXPIRED_TEST, false)
            .putBoolean(KEY_IS_PRO, false)
            .apply()
        refreshLicenseState()
    }

    fun canAddMorePlaylists(currentCount: Int): Boolean {
        val limit = getPlaylistLimit()
        return currentCount < limit
    }

    fun isOdooCustomerSynced(): Boolean {
        return if (::prefs.isInitialized) prefs.getBoolean(KEY_ODOO_SYNCED, false) else false
    }

    fun setOdooCustomerSynced(synced: Boolean) {
        if (!::prefs.isInitialized) return
        val nowTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        prefs.edit()
            .putBoolean(KEY_ODOO_SYNCED, synced)
            .putBoolean(KEY_PORTAL_DEVICE_REGISTERED, synced)
            .putString(KEY_PORTAL_REGISTERED_DEVICE_ID, getDeviceId())
            .putString(KEY_PORTAL_LAST_SYNC_TEXT, nowTime)
            .apply()
        refreshLicenseState()
    }

    fun markDeviceRegisteredOnPortal(email: String?) {
        if (!::prefs.isInitialized) return
        val nowTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        val effectiveEmail = email?.takeIf { it.isNotBlank() } ?: getCurrentUserEmail() ?: ""
        val editor = prefs.edit()
            .putBoolean(KEY_ODOO_SYNCED, true)
            .putBoolean(KEY_PORTAL_DEVICE_REGISTERED, true)
            .putString(KEY_PORTAL_REGISTERED_DEVICE_ID, getDeviceId())
            .putString(KEY_PORTAL_LAST_SYNC_TEXT, nowTime)
        if (effectiveEmail.isNotBlank()) {
            editor.putString(KEY_PORTAL_REGISTERED_EMAIL, effectiveEmail)
        }
        editor.apply()
        refreshLicenseState()
    }

    fun isDeviceRegisteredOnPortal(): Boolean {
        if (!::prefs.isInitialized) return false
        val isReg = prefs.getBoolean(KEY_PORTAL_DEVICE_REGISTERED, false) || prefs.getBoolean(KEY_ODOO_SYNCED, false)
        val regDevId = prefs.getString(KEY_PORTAL_REGISTERED_DEVICE_ID, null)
        return isReg && (regDevId.isNullOrBlank() || regDevId == getDeviceId())
    }

    fun getPortalRegisteredEmail(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_PORTAL_REGISTERED_EMAIL, getCurrentUserEmail()) else null

    fun getPortalLastSyncTime(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_PORTAL_LAST_SYNC_TEXT, null) else null

    fun updateActivePlaylistCount(count: Int) {
        if (!::prefs.isInitialized) return
        prefs.edit().putInt(KEY_ACTIVE_PLAYLIST_COUNT, count).apply()
        _activePlaylistCountState.value = count
    }

    private const val DEFAULT_ODOO_SERVER = "https://maxxplayers.com"
    private const val PACKAGE_SHOP_URL = "https://maxxplayers.com/shop/category/maxx-players-web-player-paket-3"
    private const val WEB_PORTAL_URL = "https://maxxplayers.com/shop/category/maxx-players-web-player-paket-3"
    private const val MAGAZA_URL = "https://maxxplayers.com/shop/category/maxx-players-web-player-paket-3"

    fun getOdooServerUrl(): String {
        val stored = prefs.getString(KEY_ODOO_SERVER, null)
        if (stored.isNullOrBlank() || !stored.equals("https://maxxplayers.com", ignoreCase = true)) {
            prefs.edit().putString(KEY_ODOO_SERVER, "https://maxxplayers.com").apply()
            return "https://maxxplayers.com"
        }
        return stored.trimEnd('/')
    }

    fun setOdooServerUrl(url: String) {
        prefs.edit().putString(KEY_ODOO_SERVER, url).apply()
    }

    fun getPackageShopUrl(): String = PACKAGE_SHOP_URL
    fun getMagazaUrl(): String = PACKAGE_SHOP_URL
    fun getQrUrl(): String = PACKAGE_SHOP_URL

    fun getMyDevicesUrl(): String {
        val server = getOdooServerUrl().trimEnd('/')
        return "$server/my/devices"
    }

    fun getWebPortalUrl(): String = PACKAGE_SHOP_URL

    fun setCurrentUser(id: String, name: String, email: String, photoUrl: String? = null) {
        prefs.edit()
            .putString("current_user_id", id)
            .putString("current_user_name", name)
            .putString("current_user_email", email)
            .putString(KEY_CURRENT_USER_PHOTO, photoUrl)
            .apply()
    }

    fun getCurrentUserId(): String? = if (::prefs.isInitialized) prefs.getString("current_user_id", null) else null
    fun getCurrentUserEmail(): String? = if (::prefs.isInitialized) prefs.getString("current_user_email", null) else null
    fun getCurrentUserName(): String? = if (::prefs.isInitialized) prefs.getString("current_user_name", null) else null
    fun getCurrentUserPhotoUrl(): String? = if (::prefs.isInitialized) prefs.getString(KEY_CURRENT_USER_PHOTO, null) else null

    fun getOdooShopTrialUrl(): String {
        return getMagazaUrl()
    }

    fun getOdooShopPackagesUrl(): String {
        val customUpgradeUrl = _upgradeUrl.value
        if (!customUpgradeUrl.isNullOrBlank()) return customUpgradeUrl
        return getMagazaUrl()
    }

    data class OdooPackageInfo(
        val id: String,
        val title: String,
        val duration: String,
        val description: String,
        val price: String,
        val checkoutUrl: String
    )

    private val _packageRequired = MutableStateFlow(false)
    val packageRequired: StateFlow<Boolean> = _packageRequired.asStateFlow()

    private val _upgradeUrl = MutableStateFlow<String?>(null)
    val upgradeUrl: StateFlow<String?> = _upgradeUrl.asStateFlow()

    private val _odooPackages = MutableStateFlow<List<OdooPackageInfo>>(emptyList())
    val odooPackages: StateFlow<List<OdooPackageInfo>> = _odooPackages.asStateFlow()

    fun setPackageRequired(required: Boolean, url: String?, packages: List<OdooPackageInfo>) {
        _packageRequired.value = required
        _upgradeUrl.value = url
        _odooPackages.value = packages
    }

    fun getCustomerName(): String? {
        return prefs.getString(KEY_CUSTOMER_NAME, null)
    }

    fun setCustomerName(name: String?) {
        prefs.edit().putString(KEY_CUSTOMER_NAME, name).apply()
        _customerNameState.value = name
    }

    fun clearDeviceRegistration() {
        prefs.edit()
            .remove(KEY_CUSTOMER_NAME)
            .remove(KEY_PORTAL_REGISTERED_EMAIL)
            .remove(KEY_PORTAL_REGISTERED_DEVICE_ID)
            .putBoolean(KEY_ODOO_SYNCED, false)
            .putBoolean(KEY_PORTAL_DEVICE_REGISTERED, false)
            .apply()
        _customerNameState.value = null
        refreshLicenseState()
    }

    fun updateTrialDays(days: Int) {
        if (days >= 0) {
            prefs.edit()
                .putInt(KEY_PACKAGE_DAYS_LEFT, days)
                .putLong(KEY_SERVER_SYNC_TIME, System.currentTimeMillis())
                .apply()
            val calculatedStart = System.currentTimeMillis() - ((15 - days).coerceAtLeast(0) * 24L * 60 * 60 * 1000)
            prefs.edit().putLong(KEY_TRIAL_START, calculatedStart).apply()
            refreshLicenseState()
        }
    }

    fun refreshLicenseState() {
        val days = getDaysRemaining()
        val pro = isProPurchased()
        val active = isPackageActive() && (pro || days > 0) && !isExpiredTestMode()
        val pkgName = getActivePackageName() ?: (if (pro) "PRO Paket" else if (days > 0) "15 Günlük Deneme" else "Süresi Dolmuş Paket")
        
        _customerNameState.value = getCustomerName()
        _activePackageNameState.value = pkgName
        _packageExpireDateState.value = getPackageExpireDate()
        _packageStartDateState.value = getPackageStartDate()
        _packageDurationState.value = getPackageDuration()
        _trialDaysLeft.value = days
        _isProState.value = pro
        _isProOrInTrialState.value = active
        _packageActiveState.value = active
        _packageTypeState.value = getPackageType()
        _deviceLimitState.value = getDeviceLimit()
        _playlistLimitState.value = getPlaylistLimit()
        _activeDeviceCountState.value = getActiveDeviceCount()
        _activePlaylistCountState.value = getActivePlaylistCount()
        _demoModeState.value = isDemoMode()
        _portalDeviceRegisteredState.value = isDeviceRegisteredOnPortal()
        _portalRegisteredEmailState.value = getPortalRegisteredEmail()
        _portalLastSyncTimeState.value = getPortalLastSyncTime()
    }
}
