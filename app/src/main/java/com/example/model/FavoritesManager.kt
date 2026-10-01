package com.example.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FavoritesManager {
    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_FAVORITE_URLS = "favorite_urls"

    private lateinit var prefs: SharedPreferences

    private val _favoriteUrls = MutableStateFlow<Set<String>>(emptySet())
    val favoriteUrls: StateFlow<Set<String>> = _favoriteUrls.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _favoriteUrls.value = prefs.getStringSet(KEY_FAVORITE_URLS, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(url: String) {
        val current = _favoriteUrls.value.toMutableSet()
        if (current.contains(url)) {
            current.remove(url)
        } else {
            current.add(url)
        }
        _favoriteUrls.value = current
        prefs.edit().putStringSet(KEY_FAVORITE_URLS, current).apply()
    }

    fun isFavorite(url: String): Boolean {
        return _favoriteUrls.value.contains(url)
    }
}
