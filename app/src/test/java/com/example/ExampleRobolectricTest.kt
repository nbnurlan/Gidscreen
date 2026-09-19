package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Screen Lasso AI", appName)
    }

    @Test
    fun `selection modes are supported and rectangle is first`() {
        val modes = SelectionMode.values()
        assertEquals(SelectionMode.RECTANGLE, modes[0])
        assertTrue(modes.contains(SelectionMode.LASSO))
        assertTrue(modes.contains(SelectionMode.RECTANGLE))
        assertTrue(modes.contains(SelectionMode.CIRCLE))
    }

    @Test
    fun `locale helper active language returns expected flags and names`() {
        val uz = com.example.util.LocaleHelper.getActiveLanguage("uz")
        assertEquals("🇺🇿", uz.flag)
        assertEquals("O'zbekcha", uz.nativeName)

        val ru = com.example.util.LocaleHelper.getActiveLanguage("ru")
        assertEquals("🇷🇺", ru.flag)
        assertEquals("Русский", ru.nativeName)

        val en = com.example.util.LocaleHelper.getActiveLanguage("en")
        assertEquals("🇬🇧", en.flag)
        assertEquals("English", en.nativeName)
    }

    @Test
    fun `locale helper supported languages integrity`() {
        val languages = com.example.util.LocaleHelper.supportedLanguages
        assertEquals(3, languages.size)
        assertTrue(languages.any { it.code == "en" })
        assertTrue(languages.any { it.code == "uz" })
        assertTrue(languages.any { it.code == "ru" })
    }

    @Test
    fun `locale helper context wrapping provides localized strings`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        // Test Uzbek context localization
        RuntimeEnvironment.setQualifiers("uz")
        val uzControlCenter = baseContext.getString(R.string.tab_control_center)
        assertEquals("Boshqaruv markazi", uzControlCenter)

        // Test Russian context localization
        RuntimeEnvironment.setQualifiers("ru")
        val ruControlCenter = baseContext.getString(R.string.tab_control_center)
        assertEquals("Центр управления", ruControlCenter)

        // Test English context localization
        RuntimeEnvironment.setQualifiers("en")
        val enControlCenter = baseContext.getString(R.string.tab_control_center)
        assertEquals("Control Center", enControlCenter)
    }

    @Test
    fun `chat messages model integrity`() {
        val msg = ChatMessage(
            sender = MessageSender.AI,
            text = "Explanation from gemini-3.6-flash"
        )
        assertEquals(MessageSender.AI, msg.sender)
        assertEquals("Explanation from gemini-3.6-flash", msg.text)
        assertNotNull(msg.id)
        assertTrue(msg.isVisible)

        val hiddenMsg = ChatMessage(
            sender = MessageSender.USER,
            text = "Belgilangan ekran qismini batafsil tahlil qiling...",
            isVisible = false
        )
        assertFalse(hiddenMsg.isVisible)
    }

    @Test
    fun `analysis state transitions`() {
        val idleState: AnalysisState = AnalysisState.Idle
        assertEquals(AnalysisState.Idle, idleState)

        val capturingState: AnalysisState = AnalysisState.Capturing
        assertEquals(AnalysisState.Capturing, capturingState)
    }

    @Test
    fun `settings localized strings integrity`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        RuntimeEnvironment.setQualifiers("uz")
        assertEquals("Sozlamalar", baseContext.getString(R.string.tab_settings))
        assertEquals("Tillar", baseContext.getString(R.string.language_settings_title))
        assertEquals("Orqaga", baseContext.getString(R.string.btn_back))

        RuntimeEnvironment.setQualifiers("ru")
        assertEquals("Настройки", baseContext.getString(R.string.tab_settings))
        assertEquals("Языки", baseContext.getString(R.string.language_settings_title))
        assertEquals("Назад", baseContext.getString(R.string.btn_back))

        RuntimeEnvironment.setQualifiers("en")
        assertEquals("Settings", baseContext.getString(R.string.tab_settings))
        assertEquals("Languages", baseContext.getString(R.string.language_settings_title))
        assertEquals("Back", baseContext.getString(R.string.btn_back))
    }
}
