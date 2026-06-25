package com.example.saaraapp

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppNotificationService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gemma by lazy { FunctionGemmaHelper(applicationContext) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Try to load model. If it's in assets, initialize will copy it.
        serviceScope.launch {
            val modelFile = ModelDownloadManager.getModelFileIfExists(applicationContext)
                ?: File(applicationContext.filesDir, "Qwen3-0.6B-Q4_K_M.gguf")
            
            gemma.initialize(modelFile)
            Log.i("AloofService", "Model initialization attempted")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val isWhatsApp = sbn.packageName == "com.whatsapp" ||
                         sbn.packageName == "com.whatsapp.w4b"
        if (!isWhatsApp) return

        // Skip group summary notifications (the "X messages from Y chats" rollup)
        val isGroupSummary = sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0
        if (isGroupSummary) return

        val extras  = sbn.notification.extras
        val sender  = extras.getString("android.title") ?: "Unknown"
        val message = extras.getCharSequence("android.text")?.toString() ?: ""

        // 1. Efficient Pre-filter: Ignore junk but keep anything with dates/keywords
        if (!KeywordExtractor.isRelevant(message)) return

        Log.d("AloofService", "Relevant notification detected: $message")

        // 2. Process with LLM on IO thread
        serviceScope.launch {
            val result = gemma.analyze(message)

            // 3. Date extraction: Prioritize AI date, fall back to regex parser
            val (reminderDate, reminderDateEnd) = if (result.dateText != null && result.dateText != "null") {
                Pair(DateParser.parse(result.dateText), null)
            } else {
                DateParser.extractRangeFrom(message)
            }

            // 4. Capture everything that has a date or is marked as a reminder
            // This ensures "Meeting at 5" and "No class tomorrow" are both saved.
            val reminder = ReminderItem(
                id              = "${sbn.packageName}_${System.currentTimeMillis()}_${(sender + message).hashCode()}",
                sender          = sender,
                originalMessage = message,
                tags            = if (result.fromFallback) KeywordExtractor.extractTags(message) else result.tags,
                category        = result.category,
                time            = sbn.postTime,
                reminderDate    = reminderDate,
                reminderDateEnd = reminderDateEnd
            )

            ReminderDatabase.getDatabase(applicationContext)
                .reminderDao()
                .insertReminder(reminder.toEntity())
            
            Log.d("AloofService", "Saved captured message: $message (Date: $reminderDate)")
        }
    }

    // Reminders stay in the database even after notification is dismissed
    override fun onNotificationRemoved(sbn: StatusBarNotification) { }

    override fun onDestroy() {
        super.onDestroy()
        gemma.close()
        serviceScope.cancel()
    }
}
