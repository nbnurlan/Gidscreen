package com.example.network

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.model.GeminiModelInfo
import com.example.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiModelManager {
    private const val PREFS_NAME = "gemini_model_preferences"
    private const val KEY_SELECTED_MODEL = "key_selected_gemini_model"
    private const val KEY_CACHED_MODELS_JSON = "key_cached_models_json"

    const val DEFAULT_MODEL_ID = "gemini-3.6-flash"
    private const val MODELS_LIST_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Default built-in models matching the UI design in user's specification
    private val defaultModels = listOf(
        GeminiModelInfo(
            id = "gemini-3.6-flash",
            displayName = "3.6 Flash",
            description = "All-around help",
            category = "Gemini 3"
        ),
        GeminiModelInfo(
            id = "gemini-3.6-thinking",
            displayName = "3.6 Думающая",
            description = "Решает сложные задачи",
            category = "Gemini 3",
            isThinking = true
        ),
        GeminiModelInfo(
            id = "gemini-3.1-pro",
            displayName = "3.1 Pro",
            description = "Расширенные возможности рассуждения",
            category = "Gemini 3"
        ),
        GeminiModelInfo(
            id = "gemini-2.5-flash",
            displayName = "2.5 Flash",
            description = "All-around help • Fast multimodal visual understanding",
            category = "Gemini 2.5"
        ),
        GeminiModelInfo(
            id = "gemini-2.5-pro",
            displayName = "2.5 Pro",
            description = "Advanced reasoning & deep thinking",
            category = "Gemini 2.5"
        )
    )

    private val _selectedModelId = MutableStateFlow(DEFAULT_MODEL_ID)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _availableModels = MutableStateFlow<List<GeminiModelInfo>>(defaultModels)
    val availableModels: StateFlow<List<GeminiModelInfo>> = _availableModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun init(context: Context) {
        val prefs = getPrefs(context)
        val savedModel = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        _selectedModelId.value = savedModel

        // Load cached JSON if available
        val cachedJson = prefs.getString(KEY_CACHED_MODELS_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            try {
                val parsed = parseModelsFromJson(cachedJson)
                if (parsed.isNotEmpty()) {
                    _availableModels.value = parsed
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Trigger dynamic refresh if API key is present
        if (isApiKeyValid()) {
            CoroutineScope(Dispatchers.IO).launch {
                fetchAvailableModels(context, forceRefresh = false)
            }
        }
    }

    fun isApiKeyValid(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun getSelectedModel(): GeminiModelInfo {
        val currentId = _selectedModelId.value
        return _availableModels.value.firstOrNull { it.id == currentId || it.apiEndpointId == currentId }
            ?: defaultModels.firstOrNull { it.id == currentId }
            ?: GeminiModelInfo(
                id = currentId,
                displayName = formatDisplayName(currentId),
                description = "Selected Gemini Model",
                category = formatCategory(currentId)
            )
    }

    fun setSelectedModel(context: Context, modelId: String) {
        val cleanId = modelId.removePrefix("models/")
        _selectedModelId.value = cleanId
        getPrefs(context).edit().putString(KEY_SELECTED_MODEL, cleanId).apply()
    }

    suspend fun fetchAvailableModels(
        context: Context,
        forceRefresh: Boolean = false
    ): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyValid()) {
            return@withContext Result.success(_availableModels.value)
        }

        _isLoading.value = true
        try {
            val url = "$MODELS_LIST_URL?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                _isLoading.value = false
                return@withContext Result.failure(
                    Exception("Failed to fetch models: HTTP ${response.code} ${response.message}")
                )
            }

            val dynamicModels = parseModelsApiRawResponse(body)
            if (dynamicModels.isNotEmpty()) {
                // Ensure default prominent models are retained/merged gracefully
                val mergedMap = LinkedHashMap<String, GeminiModelInfo>()

                // First put our prominent models so the UI hierarchy is clean
                defaultModels.forEach { defModel ->
                    mergedMap[defModel.apiEndpointId] = defModel
                }

                // Merge in any dynamically discovered models from Google's endpoint
                dynamicModels.forEach { dynModel ->
                    val key = dynModel.apiEndpointId
                    if (mergedMap.containsKey(key)) {
                        // Keep our clean localized display styling if present
                        val existing = mergedMap[key]!!
                        mergedMap[key] = existing.copy(
                            description = if (existing.description.isNotBlank()) existing.description else dynModel.description
                        )
                    } else {
                        mergedMap[key] = dynModel
                    }
                }

                val finalList = mergedMap.values.toList()
                _availableModels.value = finalList

                // Cache serialized JSON
                saveModelsCache(context, finalList)
                _isLoading.value = false
                return@withContext Result.success(finalList)
            } else {
                _isLoading.value = false
                return@withContext Result.success(_availableModels.value)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isLoading.value = false
            return@withContext Result.failure(e)
        }
    }

    private fun parseModelsApiRawResponse(rawJson: String): List<GeminiModelInfo> {
        val list = mutableListOf<GeminiModelInfo>()
        try {
            val root = JSONObject(rawJson)
            val modelsArray = root.optJSONArray("models") ?: return emptyList()

            for (i in 0 until modelsArray.length()) {
                val item = modelsArray.getJSONObject(i)
                val rawName = item.optString("name", "") // e.g. "models/gemini-2.5-flash"
                val cleanId = rawName.removePrefix("models/")

                // Filter for Gemini models that support generateContent
                if (!cleanId.contains("gemini", ignoreCase = true)) continue

                val supportedMethods = item.optJSONArray("supportedGenerationMethods")
                var supportsGenerateContent = false
                if (supportedMethods != null) {
                    for (j in 0 until supportedMethods.length()) {
                        if (supportedMethods.optString(j) == "generateContent") {
                            supportsGenerateContent = true
                            break
                        }
                    }
                }
                if (!supportsGenerateContent) continue

                // Exclude embedding/imagen/aqa or internal test models
                if (cleanId.contains("embedding") || cleanId.contains("imagen") || cleanId.contains("aqa")) {
                    continue
                }

                val rawDisplayName = item.optString("displayName", "")
                val rawDescription = item.optString("description", "")
                val isThinking = cleanId.contains("thinking") || cleanId.contains("reasoning")

                val displayName = formatDisplayName(if (rawDisplayName.isNotBlank()) rawDisplayName else cleanId)
                val description = formatDescription(cleanId, rawDescription, isThinking)
                val category = formatCategory(cleanId)

                list.add(
                    GeminiModelInfo(
                        id = cleanId,
                        displayName = displayName,
                        description = description,
                        category = category,
                        isThinking = isThinking
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun formatDisplayName(raw: String): String {
        var name = raw.removePrefix("models/").trim()
        if (name.startsWith("Gemini ", ignoreCase = true)) {
            name = name.substring(7).trim()
        } else if (name.startsWith("gemini-", ignoreCase = true)) {
            name = name.substring(7).trim()
        }

        return when {
            name.equals("3.6-flash", ignoreCase = true) -> "3.6 Flash"
            name.equals("3.6-thinking", ignoreCase = true) -> "3.6 Думающая"
            name.equals("3.1-pro", ignoreCase = true) -> "3.1 Pro"
            name.equals("2.5-flash", ignoreCase = true) -> "2.5 Flash"
            name.equals("2.5-pro", ignoreCase = true) -> "2.5 Pro"
            name.equals("2.0-flash", ignoreCase = true) -> "2.0 Flash"
            name.equals("1.5-flash", ignoreCase = true) -> "1.5 Flash"
            name.equals("1.5-pro", ignoreCase = true) -> "1.5 Pro"
            else -> name.replace("-", " ").capitalizeWords()
        }
    }

    private fun formatDescription(cleanId: String, rawDesc: String, isThinking: Boolean): String {
        val currentLang = LocaleHelper.currentLanguage.value
        return when {
            isThinking || cleanId.contains("thinking") -> {
                when (currentLang) {
                    LocaleHelper.LANG_RU -> "Решает сложные задачи"
                    LocaleHelper.LANG_EN -> "Solves complex reasoning tasks"
                    else -> "Murakkab mantiqiy vazifalarni chuqur hal qiladi"
                }
            }
            cleanId.contains("flash") -> {
                when (currentLang) {
                    LocaleHelper.LANG_RU -> "All-around help • Универсальный быстрый помощник"
                    LocaleHelper.LANG_EN -> "All-around help"
                    else -> "Har tomonlama yordamchi • Yuqori tezlik va aniqlik"
                }
            }
            cleanId.contains("pro") -> {
                when (currentLang) {
                    LocaleHelper.LANG_RU -> "Расширенные возможности рассуждения"
                    LocaleHelper.LANG_EN -> "Expanded reasoning capabilities"
                    else -> "Kengaytirilgan chuqur tahlil va mantiqiy fikrlash"
                }
            }
            rawDesc.isNotBlank() && rawDesc.length < 90 -> rawDesc
            else -> "Google Gemini multimodal intelligence"
        }
    }

    private fun formatCategory(cleanId: String): String {
        return when {
            cleanId.contains("3.") || cleanId.contains("3-") || cleanId.contains("gemini-3") -> "Gemini 3"
            cleanId.contains("2.5") || cleanId.contains("2-5") -> "Gemini 2.5"
            cleanId.contains("2.0") || cleanId.contains("2-0") || cleanId.contains("gemini-2") -> "Gemini 2.0"
            cleanId.contains("1.5") || cleanId.contains("1-5") -> "Gemini 1.5"
            else -> "Gemini Models"
        }
    }

    private fun saveModelsCache(context: Context, list: List<GeminiModelInfo>) {
        try {
            val array = JSONArray()
            list.forEach { model ->
                val obj = JSONObject().apply {
                    put("id", model.id)
                    put("displayName", model.displayName)
                    put("description", model.description)
                    put("category", model.category)
                    put("isThinking", model.isThinking)
                }
                array.put(obj)
            }
            getPrefs(context).edit().putString(KEY_CACHED_MODELS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseModelsFromJson(jsonStr: String): List<GeminiModelInfo> {
        val list = mutableListOf<GeminiModelInfo>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                GeminiModelInfo(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    description = obj.getString("description"),
                    category = obj.optString("category", "Gemini 3"),
                    isThinking = obj.optBoolean("isThinking", false)
                )
            )
        }
        return list
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
