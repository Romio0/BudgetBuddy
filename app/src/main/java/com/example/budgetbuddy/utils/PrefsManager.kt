package com.example.budgetbuddy.utils

import android.content.Context
import android.content.SharedPreferences

object IntentKeys {
    const val EXTRA_GROUP_ID = "extra_group_id"
    const val EXTRA_EXPENSE_ID = "extra_expense_id"
}

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("budget_buddy_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PHONE = "user_phone"
    }

    fun saveUserSession(userId: String, fullName: String, phone: String) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, fullName)
            putString(KEY_USER_PHONE, phone)
            apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "") ?: ""

    fun saveProfileImage(uriString: String) {
        prefs.edit().putString("profile_image_uri", uriString).apply()
    }

    fun getProfileImage(): String = prefs.getString("profile_image_uri", "") ?: ""

    fun logout() {
        prefs.edit().clear().apply()
    }

}