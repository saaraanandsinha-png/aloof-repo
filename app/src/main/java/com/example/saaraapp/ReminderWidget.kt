package com.example.saaraapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ReminderWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ReminderRepository(
            ReminderDatabase.getDatabase(context).reminderDao()
        )
        val allReminders = repository.allReminders.first()
        val today = LocalDate.now()
        val todayReminders = allReminders.filter {
            it.reminderDate == today
        }

        provideContent {
            WidgetContent(todayReminders)
        }
    }

    @Composable
    private fun WidgetContent(reminders: List<ReminderItem>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(android.R.color.white))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Today's Tasks",
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(8.dp))
            if (reminders.isEmpty()) {
                Text(text = "No tasks for today!")
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
                    items(reminders) { reminder ->
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(4.dp)) {
                            Text(text = "${reminder.category.emoji} ${reminder.originalMessage}")
                        }
                    }
                }
            }
        }
    }
}

class ReminderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReminderWidget()
}
