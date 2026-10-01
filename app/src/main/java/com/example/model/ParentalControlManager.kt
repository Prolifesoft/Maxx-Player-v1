package com.example.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ParentalControlManager {
    private const val PREFS_NAME = "parental_control_prefs"
    private const val KEY_PIN = "pin_code"
    private const val KEY_SECRET_QUESTION = "secret_question"
    private const val KEY_SECRET_ANSWER = "secret_answer"
    private const val KEY_LOCKED_CHANNELS = "locked_channels"
    private const val KEY_LOCKED_GROUPS = "locked_groups"

    private lateinit var prefs: SharedPreferences

    private val _pinCode = MutableStateFlow<String?>(null)
    val pinCode: StateFlow<String?> = _pinCode.asStateFlow()

    private val _secretQuestion = MutableStateFlow<String?>(null)
    val secretQuestion: StateFlow<String?> = _secretQuestion.asStateFlow()

    private val _secretAnswer = MutableStateFlow<String?>(null)
    val secretAnswer: StateFlow<String?> = _secretAnswer.asStateFlow()

    private val _lockedChannels = MutableStateFlow<Set<String>>(emptySet())
    val lockedChannels: StateFlow<Set<String>> = _lockedChannels.asStateFlow()
    
    private val _lockedGroups = MutableStateFlow<Set<String>>(emptySet())
    val lockedGroups: StateFlow<Set<String>> = _lockedGroups.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pinCode.value = prefs.getString(KEY_PIN, null)
        _secretQuestion.value = prefs.getString(KEY_SECRET_QUESTION, null)
        _secretAnswer.value = prefs.getString(KEY_SECRET_ANSWER, null)
        _lockedChannels.value = prefs.getStringSet(KEY_LOCKED_CHANNELS, emptySet()) ?: emptySet()
        _lockedGroups.value = prefs.getStringSet(KEY_LOCKED_GROUPS, emptySet()) ?: emptySet()
    }

    fun setupPin(pin: String, question: String, answer: String) {
        _pinCode.value = pin
        _secretQuestion.value = question
        _secretAnswer.value = answer
        prefs.edit()
            .putString(KEY_PIN, pin)
            .putString(KEY_SECRET_QUESTION, question)
            .putString(KEY_SECRET_ANSWER, answer)
            .apply()
    }

    fun resetPin(newPin: String) {
        _pinCode.value = newPin
        prefs.edit().putString(KEY_PIN, newPin).apply()
    }
    
    fun removePin() {
        _pinCode.value = null
        _secretQuestion.value = null
        _secretAnswer.value = null
        _lockedChannels.value = emptySet()
        _lockedGroups.value = emptySet()
        prefs.edit().clear().apply()
    }

    fun toggleChannelLock(channelUrl: String) {
        val current = _lockedChannels.value.toMutableSet()
        if (current.contains(channelUrl)) {
            current.remove(channelUrl)
        } else {
            current.add(channelUrl)
        }
        _lockedChannels.value = current
        prefs.edit().putStringSet(KEY_LOCKED_CHANNELS, current).apply()
    }

    fun isChannelLocked(channelUrl: String): Boolean {
        if (_pinCode.value == null) return false
        return _lockedChannels.value.contains(channelUrl)
    }
    
    fun isItemLocked(item: com.example.parser.M3uItem): Boolean {
        if (_pinCode.value == null) return false
        return _lockedChannels.value.contains(item.url) || _lockedGroups.value.contains(item.group)
    }

    fun toggleGroupLock(groupName: String) {
        val current = _lockedGroups.value.toMutableSet()
        if (current.contains(groupName)) {
            current.remove(groupName)
        } else {
            current.add(groupName)
        }
        _lockedGroups.value = current
        prefs.edit().putStringSet(KEY_LOCKED_GROUPS, current).apply()
    }

    fun isGroupLocked(groupName: String): Boolean {
        if (_pinCode.value == null) return false
        return _lockedGroups.value.contains(groupName)
    }
}
