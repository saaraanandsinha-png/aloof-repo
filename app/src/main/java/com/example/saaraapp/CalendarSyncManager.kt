package com.example.saaraapp

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

object CalendarSyncManager {

    private const val TAG = "CalendarSync"

    fun syncReminder(context: Context, reminder: ReminderItem) {
        val reminderDate = reminder.reminderDate ?: return
        
        try {
            val startMillis = reminderDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = (reminder.reminderDateEnd ?: reminderDate)
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "[Aloof] ${reminder.category.label}: ${reminder.sender}")
                put(CalendarContract.Events.DESCRIPTION, reminder.originalMessage)
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.ALL_DAY, 1)
            }

            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                Log.i(TAG, "Event synced to Google Calendar: ${uri.lastPathSegment}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for calendar sync: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync to calendar: ${e.message}")
        }
    }
}
