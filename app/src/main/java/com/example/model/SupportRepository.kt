package com.example.model

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SupportTicket(
    val id: String,
    val title: String,
    val channelName: String?,
    val message: String,
    val status: String,
    val statusCode: String?,
    val adminReply: String?,
    val timestamp: String
)

object SupportRepository {
    private const val TAG = "SupportRepository"
    private const val CREATE_ENDPOINT = "https://maxxplayers.com/api/v1/support/create"
    private const val GET_ENDPOINT = "https://maxxplayers.com/api/v1/support/get"

    private val _tickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val tickets: StateFlow<List<SupportTicket>> = _tickets

    private val _unreadNotification = MutableStateFlow<SupportTicket?>(null)
    val unreadNotification: StateFlow<SupportTicket?> = _unreadNotification

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    // Deduplication tracker: prevents repeatedly notifying the same admin reply
    private val notifiedReplyKeys = mutableSetOf<String>()

    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    fun createTicket(
        title: String,
        channelName: String?,
        message: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            createTicketSuspend(title, channelName, message)
        }
    }

    suspend fun createTicketSuspend(
        title: String,
        channelName: String?,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val deviceId = DeviceManager.getDeviceId()
        val deviceKey = DeviceManager.getDeviceKey()

        if (deviceId.isBlank() || deviceKey.isBlank()) {
            return@withContext Result.failure(Exception("Cihaz kimliği bulunamadı. Lütfen cihazınızı portal üzerinden eşleştirin."))
        }

        try {
            val url = URL(CREATE_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val params = JSONObject().apply {
                put("device_id", deviceId)
                put("device_key", deviceKey)
                put("title", title.trim())
                put("channel_name", if (channelName.isNullOrBlank()) "Genel" else channelName.trim())
                put("message", message.trim())
            }

            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode

            if (responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()

                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result") ?: rootJson
                val status = resultObj.optString("status")

                if (status.equals("success", ignoreCase = true) || resultObj.optBoolean("success", false)) {
                    // Sync tickets immediately to update list from server
                    syncTicketsFromOdoo()
                    Result.success(resultObj.optString("message", "Talebiniz başarıyla iletildi."))
                } else {
                    val errorDesc = resultObj.optString("message", resultObj.optString("error", "Destek talebi oluşturulamadı."))
                    Result.failure(Exception(errorDesc))
                }
            } else {
                conn.disconnect()
                val message = when (responseCode) {
                    401 -> "Yetkilendirme hatası (401). Lütfen tekrar giriş yapın."
                    403 -> "Erişim engellendi (403)."
                    404 -> "Destek servisi bulunamadı (404)."
                    500 -> "Sunucu hatası oluştu (500). Lütfen daha sonra tekrar deneyin."
                    else -> "Sunucu hatası oluştu (HTTP $responseCode)."
                }
                Result.failure(Exception(message))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Support ticket creation failed: ${e.message}")
            Result.failure(Exception("Sunucuya bağlanılamadı. Lütfen internet bağlantınızı kontrol edin."))
        }
    }

    suspend fun syncTicketsFromOdoo(): Result<List<SupportTicket>> = withContext(Dispatchers.IO) {
        val deviceId = DeviceManager.getDeviceId()
        val deviceKey = DeviceManager.getDeviceKey()

        if (deviceId.isBlank() || deviceKey.isBlank()) {
            return@withContext Result.failure(Exception("Cihaz kimliği bulunamadı."))
        }

        _isSyncing.value = true
        _lastError.value = null

        try {
            val url = URL(GET_ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val params = JSONObject().apply {
                put("device_id", deviceId)
                put("device_key", deviceKey)
            }

            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("method", "call")
                put("params", params)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val responseCode = conn.responseCode

            if (responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()

                val rootJson = JSONObject(responseStr)
                val resultObj = rootJson.optJSONObject("result") ?: rootJson
                val status = resultObj.optString("status")

                if (status.equals("success", ignoreCase = true) || resultObj.has("tickets")) {
                    val ticketsArray = resultObj.optJSONArray("tickets")
                    val list = mutableListOf<SupportTicket>()

                    if (ticketsArray != null) {
                        for (i in 0 until ticketsArray.length()) {
                            val item = ticketsArray.getJSONObject(i)
                            val tId = item.optString("id", item.optString("ticket_id", (i + 1).toString()))
                            val title = item.optString("title", "Destek Talebi")
                            val chName = item.optString("channel_name", item.optString("channelName", "Genel"))
                            val msg = item.optString("message", item.optString("msg", ""))
                            val rawStatus = item.optString("status", item.optString("state", "Açık"))
                            val statusCode = item.optString("status_code", item.optString("statusCode", "open"))
                            val rawAdminReply = item.optString("admin_reply", item.optString("adminReply", item.optString("reply", "")))
                            val adminReply = if (rawAdminReply.isNotBlank() && rawAdminReply != "null") rawAdminReply.trim() else null
                            val timestamp = item.optString("timestamp", item.optString("create_date", getCurrentTime()))

                            val displayStatus = when {
                                !adminReply.isNullOrBlank() || statusCode.equals("answered", ignoreCase = true) || rawStatus.equals("answered", ignoreCase = true) || rawStatus.equals("Yanıtlandı", ignoreCase = true) -> "Yanıtlandı"
                                statusCode.equals("closed", ignoreCase = true) || rawStatus.equals("closed", ignoreCase = true) -> "Kapandı"
                                else -> "Açık"
                            }

                            list.add(
                                SupportTicket(
                                    id = tId,
                                    title = title,
                                    channelName = if (chName.isBlank()) null else chName,
                                    message = msg,
                                    status = displayStatus,
                                    statusCode = statusCode,
                                    adminReply = adminReply,
                                    timestamp = timestamp
                                )
                            )
                        }
                    }

                    // Update state - clean if empty response
                    _tickets.value = list

                    // Check for answered tickets that haven't been notified yet
                    val newlyAnswered = list.firstOrNull { ticket ->
                        ticket.adminReply != null && !notifiedReplyKeys.contains("${ticket.id}_${ticket.adminReply.hashCode()}")
                    }

                    if (newlyAnswered != null) {
                        _unreadNotification.value = newlyAnswered
                    }

                    Result.success(list)
                } else {
                    val msg = resultObj.optString("message", "Destek talepleri alınamadı.")
                    _lastError.value = msg
                    Result.failure(Exception(msg))
                }
            } else {
                conn.disconnect()
                val msg = when (responseCode) {
                    401 -> "Yetkilendirme hatası (401)."
                    403 -> "Erişim engellendi (403)."
                    404 -> "Destek servisi bulunamadı (404)."
                    500 -> "Sunucu hatası (500)."
                    else -> "HTTP $responseCode hatası."
                }
                _lastError.value = msg
                // Keep last successful list on network error
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Support sync failed: ${e.message}")
            _lastError.value = "Sunucuya bağlanılamadı."
            // Keep last successful list on network error
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    fun dismissNotification(ticket: SupportTicket? = null) {
        val target = ticket ?: _unreadNotification.value
        if (target?.adminReply != null) {
            notifiedReplyKeys.add("${target.id}_${target.adminReply.hashCode()}")
        }
        _unreadNotification.value = null
    }

    fun clearAllTickets() {
        _tickets.value = emptyList()
        _unreadNotification.value = null
    }
}

