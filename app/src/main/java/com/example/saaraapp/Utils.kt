package com.example.saaraapp

import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

fun Long.toLocalDate(): LocalDate =
    Date(this).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

fun groupRemindersByDate(reminders: List<ReminderItem>): Map<LocalDate, List<ReminderItem>> {
    val map = mutableMapOf<LocalDate, MutableList<ReminderItem>>()
    reminders.forEach { reminder ->
        val start = reminder.reminderDate ?: reminder.time.toLocalDate()
        val end = reminder.reminderDateEnd
        if (end != null && !end.isBefore(start)) {
            var d = start
            while (!d.isAfter(end)) {
                map.getOrPut(d) { mutableListOf() }.add(reminder)
                d = d.plusDays(1)
            }
        } else {
            map.getOrPut(start) { mutableListOf() }.add(reminder)
        }
    }
    return map
}
