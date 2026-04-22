package com.todo.dailyroutine.data.session

import android.content.Context

class SessionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    fun saveSession(userId: String, token: String, email: String = "") {
        prefs.edit()
            .putString("user_id", userId)
            .putString("auth_token", token)
            .putString("email", email)
            .apply()
    }

    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    fun getEmail(): String? {
        return prefs.getString("email", null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun isLoggedIn(): Boolean = getUserId() != null && getToken() != null
    
    // Compatibility aliases
    fun userId(): String = getUserId().orEmpty()
    fun token(): String = getToken().orEmpty()
    fun email(): String = getEmail().orEmpty()
}
