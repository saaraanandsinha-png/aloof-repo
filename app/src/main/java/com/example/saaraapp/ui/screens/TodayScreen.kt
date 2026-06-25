package com.example.saaraapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saaraapp.NotificationViewModel
import com.example.saaraapp.ReminderItem
import com.example.saaraapp.WhatsAppNotificationScreen
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun TodayScreen(viewModel: NotificationViewModel = viewModel()) {
    val reminders by viewModel.reminders.collectAsState()
    val today = LocalDate.now()

    // Filter to show only today's important reminders
    val todayReminders = remember(reminders) {
        val today = LocalDate.now()
        reminders.filter { reminder ->
            val reminderDate = reminder.reminderDate
            val receivedDate = java.util.Date(reminder.time).toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            
            // Show if it is FOR today
            val isForToday = if (reminder.reminderDateEnd != null && reminderDate != null) {
                !today.isBefore(reminderDate) && !today.isAfter(reminder.reminderDateEnd)
            } else {
                reminderDate == today
            }

            // OR if it was RECEIVED today (so user sees new captures immediately)
            val isReceivedToday = receivedDate == today
            
            isForToday || isReceivedToday
        }.sortedByDescending { it.time }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Header ──────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Text(
                text = "Today",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // ── Content ─────────────────────────────────────────
        if (todayReminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 52.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No tasks for today!",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "${todayReminders.size} reminder${if (todayReminders.size > 1) "s" else ""} today",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(todayReminders) { reminder ->
                    CalendarReminderCard(reminder, highlight = true)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
