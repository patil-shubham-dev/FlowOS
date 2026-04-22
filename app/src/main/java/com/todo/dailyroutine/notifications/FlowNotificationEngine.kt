package com.todo.dailyroutine.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

object FlowNotificationEngine {
    const val CHANNEL_FOCUS = "channel_focus"
    const val CHANNEL_HABIT = "channel_habit"
    const val CHANNEL_JOURNAL = "channel_journal"
    const val CHANNEL_BRIEFING = "channel_briefing"
    const val CHANNEL_MOOD = "channel_mood"
    const val CHANNEL_GENERAL = "channel_general"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channels = listOf(
                NotificationChannel(CHANNEL_FOCUS, "Focus Windows", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_HABIT, "Ritual Guardian", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_JOURNAL, "Reflection Sync", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_BRIEFING, "Oracle Briefing", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_MOOD, "Vibe Intelligence", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_GENERAL, "Oracle", NotificationManager.IMPORTANCE_DEFAULT)
            )
            
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }
}
