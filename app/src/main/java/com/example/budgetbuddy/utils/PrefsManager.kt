package com.example.budgetbuddy.utils

import android.content.Context

object IntentKeys {
    const val EXTRA_GROUP_ID = "extra_group_id"
    const val EXTRA_EXPENSE_ID = "extra_expense_id"
}

class PrefsManager(context: Context)
{
    private val prefs = context.getSharedPreferences("BudgetBuddyPrefs", Context.MODE_PRIVATE)

    fun saveUserProfile(name: String, phone: String) {
        prefs.edit()
            .putString("user_name", name)
            .putString("user_phone", phone)
            .putBoolean("is_onboarded", true)
            .apply()
    }

    fun isLogged(): Boolean = prefs.getBoolean("Logged", false)

    fun getUserName(): String = prefs.getString("user_name", "Amira") ?: "Amira"

    fun getUserPhone(): String = prefs.getString("user_phone", "") ?: ""
}