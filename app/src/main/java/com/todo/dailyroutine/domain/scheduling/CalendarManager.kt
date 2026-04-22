package com.todo.dailyroutine.domain.scheduling

import android.content.Context
import android.provider.CalendarContract
import java.util.*

class CalendarManager(private val context: Context) {

    data class CalendarEvent(val title: String, val start: Long, val end: Long)

    fun getTodayEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis

        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startOfDay.toString(), endOfDay.toString())

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    events.add(
                        CalendarEvent(
                            it.getString(0),
                            it.getLong(1),
                            it.getLong(2)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        }

        return events
    }
}
