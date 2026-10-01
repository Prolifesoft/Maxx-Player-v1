package com.example.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CategoryManager {
    private const val PREFS_NAME = "category_prefs"
    private const val KEY_HIDDEN_CATEGORIES = "hidden_categories"

    private lateinit var prefs: SharedPreferences

    private val _hiddenCategories = MutableStateFlow<Set<String>>(emptySet())
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _hiddenCategories.value = prefs.getStringSet(KEY_HIDDEN_CATEGORIES, emptySet()) ?: emptySet()
    }

    fun toggleCategoryVisibility(categoryName: String) {
        val current = _hiddenCategories.value.toMutableSet()
        if (current.contains(categoryName)) {
            current.remove(categoryName)
        } else {
            current.add(categoryName)
        }
        _hiddenCategories.value = current
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, current).apply()
    }

    fun setCategoryVisibility(categoryName: String, isVisible: Boolean) {
        val current = _hiddenCategories.value.toMutableSet()
        if (isVisible) {
            current.remove(categoryName)
        } else {
            current.add(categoryName)
        }
        _hiddenCategories.value = current
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, current).apply()
    }

    fun isCategoryHidden(categoryName: String): Boolean {
        return _hiddenCategories.value.contains(categoryName)
    }

    fun showAllCategories() {
        _hiddenCategories.value = emptySet()
        prefs.edit().remove(KEY_HIDDEN_CATEGORIES).apply()
    }

    fun showCategoriesInList(categories: Collection<String>) {
        val current = _hiddenCategories.value.toMutableSet()
        current.removeAll(categories.toSet())
        _hiddenCategories.value = current
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, current).apply()
    }

    fun hideCategoriesInList(categories: Collection<String>) {
        val current = _hiddenCategories.value.toMutableSet()
        current.addAll(categories)
        _hiddenCategories.value = current
        prefs.edit().putStringSet(KEY_HIDDEN_CATEGORIES, current).apply()
    }
}
