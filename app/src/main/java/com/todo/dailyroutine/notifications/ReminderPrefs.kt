package com.todo.dailyroutine.notifications

import android.content.Context

class ReminderPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ai_reminders", Context.MODE_PRIVATE)

    fun save(hour24: Int, minute: Int, title: String, body: String, enabled: Boolean) {
        prefs.edit()
            .putInt(KEY_HOUR, hour24)
            .putInt(KEY_MINUTE, minute)
            .putString(KEY_TITLE, title)
            .putString(KEY_BODY, body)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun load(): ReminderConfig {
        return ReminderConfig(
            hour24 = prefs.getInt(KEY_HOUR, 8),
            minute = prefs.getInt(KEY_MINUTE, 0),
            title = prefs.getString(KEY_TITLE, "Daily Routine Check-in").orEmpty(),
            body = prefs.getString(KEY_BODY, "Open the app and complete your next action.").orEmpty(),
            enabled = prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    data class ReminderConfig(
        val hour24: Int,
        val minute: Int,
        val title: String,
        val body: String,
        val enabled: Boolean
    )

    companion object {
        private const val KEY_HOUR = "hour24"
        private const val KEY_MINUTE = "minute"
        private const val KEY_TITLE = "title"
        private const val KEY_BODY = "body"
        private const val KEY_ENABLED = "enabled"
    }
}
