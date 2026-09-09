package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.SelectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    fun `selection modes are supported`() {
        val modes = SelectionMode.values()
        assertTrue(modes.contains(SelectionMode.LASSO))
        assertTrue(modes.contains(SelectionMode.RECTANGLE))
        assertTrue(modes.contains(SelectionMode.CIRCLE))
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

        // Test Uzbek context wrapping
        val uzContext = com.example.util.LocaleHelper.wrapContext(baseContext, "uz")
        val uzControlCenter = uzContext.getString(R.string.tab_control_center)
        assertEquals("Boshqaruv markazi", uzControlCenter)

        // Test Russian context wrapping
        val ruContext = com.example.util.LocaleHelper.wrapContext(baseContext, "ru")
        val ruControlCenter = ruContext.getString(R.string.tab_control_center)
        assertEquals("Центр управления", ruControlCenter)

        // Test English context wrapping
        val enContext = com.example.util.LocaleHelper.wrapContext(baseContext, "en")
        val enControlCenter = enContext.getString(R.string.tab_control_center)
        assertEquals("Control Center", enControlCenter)
    }

    @Test
    fun `chat messages model integrity`() {
        val msg = ChatMessage(
            sender = MessageSender.AI,
            text = "Explanation from gemini-2.5-flash"
        )
        assertEquals(MessageSender.AI, msg.sender)
        assertEquals("Explanation from gemini-2.5-flash", msg.text)
        assertNotNull(msg.id)
    }

    @Test
    fun `analysis state transitions`() {
        val idleState: AnalysisState = AnalysisState.Idle
        assertEquals(AnalysisState.Idle, idleState)

        val capturingState: AnalysisState = AnalysisState.Capturing
        assertEquals(AnalysisState.Capturing, capturingState)
    }
}
