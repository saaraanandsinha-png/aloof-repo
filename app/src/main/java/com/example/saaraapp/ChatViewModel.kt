package com.example.saaraapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val gemma = FunctionGemmaHelper(application)
    private val repository = ReminderRepository(
        ReminderDatabase.getDatabase(application).reminderDao()
    )
    
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    init {
        viewModelScope.launch {
            val modelFile = ModelDownloadManager.getModelFileIfExists(application)
                ?: File(application.filesDir, "Qwen3-0.6B-Q4_K_M.gguf")
            gemma.initialize(modelFile)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isProcessing.value) return

        viewModelScope.launch {
            _chatHistory.value += ChatMessage(text, isUser = true)
            _isProcessing.value = true

            // Get context: Today's reminders
            val today = LocalDate.now()
            val allReminders = repository.allReminders.first()
            val todayReminders = allReminders.filter { reminder ->
                val start = reminder.reminderDate
                val end = reminder.reminderDateEnd
                if (start == null) false
                else if (end != null) !today.isBefore(start) && !today.isAfter(end)
                else start == today
            }

            val contextString = if (todayReminders.isEmpty()) {
                "You have no reminders for today."
            } else {
                "Today's reminders:\n" + todayReminders.joinToString("\n") { r ->
                    "- [${r.category.label}] ${r.originalMessage} (From: ${r.sender})"
                }
            }

            val response = gemma.askGemma(text, contextString)

            _chatHistory.value += ChatMessage(response, isUser = false)
            _isProcessing.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemma.close()
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)
