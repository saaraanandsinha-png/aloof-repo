package com.example.saaraapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val gemma = FunctionGemmaHelper(application)
    
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    init {
        viewModelScope.launch {
            val modelFile = ModelDownloadManager.getModelFileIfExists(application)
                ?: File(application.filesDir, "functiongemma-270m-it-Q4_K_M.gguf")
            gemma.initialize(modelFile)
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isProcessing.value) return

        viewModelScope.launch {
            _chatHistory.value += ChatMessage(text, isUser = true)
            _isProcessing.value = true

            val response = gemma.askGemma(text)

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
