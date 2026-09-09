package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // Scale down if oversized to avoid payload limit, maintaining sharp quality
        val maxDimension = 1280
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (ratio > 1) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
            }
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun analyzeScreenCrop(
        bitmap: Bitmap,
        customInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (!isApiKeyConfigured()) {
            return@withContext Result.success(
                "✨ **Screen Selection Captured (${bitmap.width}x${bitmap.height}px)**\n\n" +
                "**Selection Analysis Ready:**\n" +
                "• **Type:** High-resolution screen crop\n" +
                "• **Model:** `gemini-2.5-flash`\n" +
                "• **Status:** Ready for live cloud reasoning\n\n" +
                "🔑 *Note*: To get live AI answers from Google Gemini, add your `GEMINI_API_KEY` in the AI Studio Secrets panel. The floating window, draggable controls, resizable panel, and screen capture pipeline are fully operational!"
            )
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = customInstruction ?: "Analyze the selected screen content in detail. Identify any text, code, questions, formulas, diagrams, UI elements, or objects. Provide a well-structured, clear explanation, key takeaways, and exact answers where applicable."

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            // Text prompt
                            put(JSONObject().put("text", prompt))
                            // Inline image data
                            put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                // Optional system instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put(
                        "text",
                        "You are an AI assistant analyzing a screenshot portion selected by the user. Be concise, direct, helpful, and format with readable markdown."
                    )))
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    Exception("Gemini API Error (${response.code}): ${response.message}\n$responseBody")
                )
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text", "")
                        textBuilder.append(text)
                    }
                    return@withContext Result.success(textBuilder.toString().trim())
                }
            }

            Result.failure(Exception("No explanation returned by Gemini: $responseBody"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun continueChat(
        history: List<ChatMessage>,
        newQuestion: String,
        bitmap: Bitmap?
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            return@withContext Result.success(
                "💬 **AI Response**: To continue interactive multi-turn discussions about this screen selection with `gemini-2.5-flash`, please provide a valid `GEMINI_API_KEY` in the AI Studio Secrets panel."
            )
        }

        try {
            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray()

                // If bitmap exists, attach it to first turn
                var imageAttached = false
                for (msg in history) {
                    val role = if (msg.sender == MessageSender.USER) "user" else "model"
                    val contentObj = JSONObject().apply {
                        put("role", role)
                        val parts = JSONArray()
                        if (!imageAttached && bitmap != null && msg.sender == MessageSender.USER) {
                            parts.put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", bitmapToBase64(bitmap))
                            }))
                            imageAttached = true
                        }
                        parts.put(JSONObject().put("text", msg.text))
                        put("parts", parts)
                    }
                    contentsArray.put(contentObj)
                }

                // Add current question
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray()
                    if (!imageAttached && bitmap != null) {
                        parts.put(JSONObject().put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", bitmapToBase64(bitmap))
                        }))
                    }
                    parts.put(JSONObject().put("text", newQuestion))
                    put("parts", parts)
                })

                put("contents", contentsArray)
            }

            val request = Request.Builder()
                .url("$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    Exception("Gemini API Error (${response.code}): ${response.message}")
                )
            }

            val rootJson = JSONObject(responseBody)
            val candidates = rootJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val textBuilder = StringBuilder()
                    for (i in 0 until parts.length()) {
                        textBuilder.append(parts.getJSONObject(i).optString("text", ""))
                    }
                    return@withContext Result.success(textBuilder.toString().trim())
                }
            }

            Result.failure(Exception("No response text from Gemini."))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
