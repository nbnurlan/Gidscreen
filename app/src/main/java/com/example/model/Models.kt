package com.example.model

import android.graphics.Bitmap

enum class SelectionMode {
    LASSO,
    RECTANGLE,
    CIRCLE
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    AI,
    SYSTEM
}

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Capturing : AnalysisState
    data class Analyzing(val thumbnail: Bitmap?) : AnalysisState
    data class Success(val response: String, val thumbnail: Bitmap?) : AnalysisState
    data class Error(val message: String, val thumbnail: Bitmap?) : AnalysisState
}
