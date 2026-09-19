package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class AppLanguage(
    val code: String,
    val nameKey: String,
    val nativeName: String,
    val flag: String
)

object LocaleHelper {
    private const val PREFS_NAME = "lasso_locale_prefs"
    private const val KEY_LANGUAGE = "key_app_language"

    const val LANG_EN = "en"
    const val LANG_UZ = "uz"
    const val LANG_RU = "ru"

    val supportedLanguages = listOf(
        AppLanguage(code = LANG_UZ, nameKey = "Uzbek", nativeName = "O'zbekcha", flag = "🇺🇿"),
        AppLanguage(code = LANG_EN, nameKey = "English", nativeName = "English", flag = "🇬🇧"),
        AppLanguage(code = LANG_RU, nameKey = "Russian", nativeName = "Русский", flag = "🇷🇺")
    )

    fun getActiveLanguage(code: String): AppLanguage {
        return supportedLanguages.firstOrNull { it.code == code } ?: supportedLanguages.first()
    }

    private val _currentLanguage = MutableStateFlow(LANG_UZ)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun init(context: Context) {
        val savedLang = getSavedLanguage(context)
        _currentLanguage.value = savedLang
        applyLocale(savedLang)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = getPrefs(context)
        return prefs.getString(KEY_LANGUAGE, null) ?: run {
            // If not saved, detect system locale if uz or ru, else fallback to uz
            val currentAppLocales = AppCompatDelegate.getApplicationLocales()
            if (!currentAppLocales.isEmpty) {
                currentAppLocales.get(0)?.language?.lowercase()?.let { lang ->
                    if (supportedLanguages.any { it.code == lang }) return@run lang
                }
            }
            val systemLang = Locale.getDefault().language.lowercase()
            if (supportedLanguages.any { it.code == systemLang }) systemLang else LANG_UZ
        }
    }

    fun setLanguage(context: Context, languageCode: String) {
        if (!supportedLanguages.any { it.code == languageCode }) return

        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        _currentLanguage.value = languageCode
        applyLocale(languageCode)
    }

    private fun applyLocale(languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun wrapContext(context: Context, languageCode: String? = null): Context {
        val lang = languageCode ?: _currentLanguage.value
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        val localeList = android.os.LocaleList(locale)
        config.setLocales(localeList)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun getDisplayName(code: String): String {
        return supportedLanguages.firstOrNull { it.code == code }?.nativeName ?: code
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
