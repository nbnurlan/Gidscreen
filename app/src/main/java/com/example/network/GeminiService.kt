package com.example.network

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.util.LocaleHelper
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

sealed class GeminiApiException(message: String) : Exception(message)
class ApiKeyLeakedException(message: String) : GeminiApiException(message)
class ApiKeyInvalidException(message: String) : GeminiApiException(message)

object GeminiService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    fun getActiveModelId(): String {
        return GeminiModelManager.selectedModelId.value.removePrefix("models/")
    }

    fun getActiveModelDisplayName(): String {
        return GeminiModelManager.getSelectedModel().displayName
    }

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

    private fun parseApiError(code: Int, httpMessage: String, responseBody: String?): Exception {
        if (responseBody.isNullOrBlank()) {
            return Exception("Gemini API Error ($code): $httpMessage")
        }
        return try {
            val root = JSONObject(responseBody)
            val errorObj = root.optJSONObject("error")
            val message = errorObj?.optString("message", "")
            if (!message.isNullOrBlank()) {
                if (message.contains("leaked", ignoreCase = true)) {
                    ApiKeyLeakedException("Your API key was reported as leaked and revoked by Google. Please enter a new GEMINI_API_KEY in the AI Studio Secrets panel.")
                } else if (code == 400 && (message.contains("API_KEY_INVALID", ignoreCase = true) || message.contains("API key not valid", ignoreCase = true))) {
                    ApiKeyInvalidException("Invalid Gemini API key. Please check GEMINI_API_KEY in the AI Studio Secrets panel.")
                } else {
                    Exception("Gemini API Error ($code): $message")
                }
            } else {
                Exception("Gemini API Error ($code): $httpMessage")
            }
        } catch (_: Exception) {
            Exception("Gemini API Error ($code): $httpMessage")
        }
    }

    private fun getSystemInstruction(): String {
        val currentLang = LocaleHelper.currentLanguage.value
        return when (currentLang) {
            LocaleHelper.LANG_RU ->
                "Вы — экспертный AI-ассистент, анализирующий снимок экрана, выделенный пользователем. Отвечайте строго на русском языке, точно, кратко, полезно и структурированно с помощью markdown."
            LocaleHelper.LANG_EN ->
                "You are an AI assistant analyzing a screenshot portion selected by the user. Be concise, direct, helpful, and format with readable markdown."
            else -> // Default / LANG_UZ
                "Siz foydalanuvchi tomonidan ekranda belgilab olingan qismni tahlil qiluvchi aqlli AI yordamchisiz. Barcha tushuntirish va javoblarni ALBATTA TOZA, TUSHUNARLI VA ANIQ O'ZBEK TILIDA qaytaring. Matn, dastur kodi, test savollari, formulalar, jadvallar yoki obyektlarni aniqlab, batafsil, to'liq va ravon o'zbek tilida tushuntirib bering. Markdown formatidan foydalaning."
        }
    }

    fun getDefaultAnalysisPrompt(): String {
        val currentLang = LocaleHelper.currentLanguage.value
        return when (currentLang) {
            LocaleHelper.LANG_RU ->
                "Подробно проанализируйте выделенный фрагмент экрана. Определите текст, код, вопросы, формулы или объекты. Предоставьте четкое объяснение и точные ответы на русском языке."
            LocaleHelper.LANG_EN ->
                "Analyze the selected screen content in detail. Identify any text, code, questions, formulas, diagrams, UI elements, or objects. Provide a well-structured, clear explanation, key takeaways, and exact answers where applicable."
            else -> // Default / LANG_UZ
                "Belgilangan ekran qismini batafsil tahlil qiling. Matn, dastur kodi, savollar, formulalar, jadvallar yoki obyektlarni aniqlang. Barcha ma'lumotlar bo'yicha aniq, to'liq va tushunarli qilib O'zbek tilida javob va tushuntirish bering."
        }
    }

    suspend fun analyzeScreenCrop(
        bitmap: Bitmap,
        customInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (!isApiKeyConfigured()) {
            val currentLang = LocaleHelper.currentLanguage.value
            val modelName = getActiveModelDisplayName()
            val offlineMsg = when (currentLang) {
                LocaleHelper.LANG_RU ->
                    "✨ **Фрагмент экрана успешно зафиксирован (${bitmap.width}x${bitmap.height}px)**\n\n" +
                    "**Готов к анализу:**\n" +
                    "• **Тип:** Снимок выделенной области\n" +
                    "• **Модель:** `$modelName`\n" +
                    "• **Статус:** Готов к облачному анализу\n\n" +
                    "🔑 *Примечание*: Чтобы получать живые ответы от Google Gemini, укажите `GEMINI_API_KEY` в панели Secrets в AI Studio."
                LocaleHelper.LANG_EN ->
                    "✨ **Screen Selection Captured (${bitmap.width}x${bitmap.height}px)**\n\n" +
                    "**Selection Analysis Ready:**\n" +
                    "• **Type:** High-resolution screen crop\n" +
                    "• **Model:** `$modelName`\n" +
                    "• **Status:** Ready for live cloud reasoning\n\n" +
                    "🔑 *Note*: To get live AI answers from Google Gemini, add your `GEMINI_API_KEY` in the AI Studio Secrets panel."
                else -> // Uzbek
                    "✨ **Ekrandan belgilangan qism saqlandi (${bitmap.width}x${bitmap.height}px)**\n\n" +
                    "**Tahlilga tayyor:**\n" +
                    "• **Turi:** Yuqori aniqlikdagi ekran parchasi\n" +
                    "• **Model:** `$modelName`\n" +
                    "• **Holat:** Bulutli AI tahliliga tayyor\n\n" +
                    "🔑 *Eslatma*: Google Gemini'dan jonli o'zbek tilidagi tahlillarni olish uchun AI Studio Secrets panelida `GEMINI_API_KEY` kalitini kiriting. Suzuvchi tugma va ekranni belgilash tizimi to'liq faol!"
            }
            return@withContext Result.success(offlineMsg)
        }

        try {
            val base64Image = bitmapToBase64(bitmap)
            val prompt = customInstruction ?: getDefaultAnalysisPrompt()
            val activeModel = getActiveModelId()

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
                        getSystemInstruction()
                    )))
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/$activeModel:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    parseApiError(response.code, response.message, responseBody)
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
            val currentLang = LocaleHelper.currentLanguage.value
            val modelName = getActiveModelDisplayName()
            val offlineChatMsg = when (currentLang) {
                LocaleHelper.LANG_RU ->
                    "💬 **Ответ ИИ**: Для продолжения диалога по этому снимку с `$modelName`, пожалуйста, укажите `GEMINI_API_KEY` в панели AI Studio Secrets."
                LocaleHelper.LANG_EN ->
                    "💬 **AI Response**: To continue interactive multi-turn discussions about this screen selection with `$modelName`, please provide a valid `GEMINI_API_KEY` in the AI Studio Secrets panel."
                else -> // Uzbek
                    "💬 **AI Javobi**: `$modelName` orqali tanlangan ekran parchasi bo'yicha o'zbek tilida suhbatni davom ettirish uchun AI Studio Secrets panelida `GEMINI_API_KEY` kalitini kiriting."
            }
            return@withContext Result.success(offlineChatMsg)
        }

        try {
            val activeModel = getActiveModelId()
            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray()

                // Build full multi-turn history with all historical images and text
                for (msg in history) {
                    if (msg.sender == MessageSender.SYSTEM) continue
                    val role = if (msg.sender == MessageSender.USER) "user" else "model"
                    val contentObj = JSONObject().apply {
                        put("role", role)
                        val parts = JSONArray()
                        if (msg.image != null) {
                            parts.put(JSONObject().put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", bitmapToBase64(msg.image))
                            }))
                        }
                        if (msg.text.isNotBlank()) {
                            parts.put(JSONObject().put("text", msg.text))
                        }
                        put("parts", parts)
                    }
                    contentsArray.put(contentObj)
                }

                // Add current turn (with new bitmap if provided)
                contentsArray.put(JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray()
                    if (bitmap != null) {
                        parts.put(JSONObject().put("inlineData", JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", bitmapToBase64(bitmap))
                        }))
                    }
                    parts.put(JSONObject().put("text", newQuestion))
                    put("parts", parts)
                })

                put("contents", contentsArray)

                // Enforce Uzbek (or active language) system instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put(
                        "text",
                        getSystemInstruction()
                    )))
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL/$activeModel:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return@withContext Result.failure(
                    parseApiError(response.code, response.message, responseBody)
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
