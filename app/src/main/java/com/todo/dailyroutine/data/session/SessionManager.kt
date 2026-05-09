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
        return "user"
    }

    fun getToken(): String? {
        return "local_token"
    }

    fun getEmail(): String? {
        return "local@flowos.ai"
    }

    fun clearSession() {
        // No-op for local-only mode
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    fun isLoggedIn(): Boolean = true
    
    // Compatibility aliases
    fun userId(): String = "user"
    fun token(): String = "local_token"
    fun email(): String = "local@flowos.ai"
}
