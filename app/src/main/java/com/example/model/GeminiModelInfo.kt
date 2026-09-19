package com.example.model

data class GeminiModelInfo(
    val id: String,                  // e.g. "gemini-3.6-flash", "gemini-2.5-flash"
    val displayName: String,         // e.g. "3.6 Flash", "3.6 Думающая", "3.1 Pro"
    val description: String,         // e.g. "All-around help", "Решает сложные задачи"
    val category: String = "Gemini 3", // Group / Family header as in screenshot ("Gemini 3", "Gemini 2.5")
    val isThinking: Boolean = false
) {
    /**
     * Sanitized model name to pass to the REST API endpoint:
     * https://generativelanguage.googleapis.com/v1beta/models/{cleanId}:generateContent
     */
    val apiEndpointId: String
        get() = id.removePrefix("models/")
}
