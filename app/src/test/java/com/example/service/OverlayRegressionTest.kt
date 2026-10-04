package com.example.service

import android.content.Context
import android.view.View
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class OverlayRegressionTest {
    private fun setField(service: LassoOverlayService, name: String, value: Any) {
        LassoOverlayService::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(service, value)
        }
    }

    @Test
    fun cancelNewSelectionRestoresExistingChat() {
        val service = LassoOverlayService()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val chat = ComposeView(context).apply { visibility = View.GONE }
        val bubble = ComposeView(context).apply { visibility = View.GONE }
        setField(service, "chatView", chat)
        setField(service, "bubbleView", bubble)
        LassoOverlayService::class.java.getDeclaredMethod(
            "dismissSelectionOverlay", Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, true)
        assertEquals(View.VISIBLE, chat.visibility)
        assertEquals(View.GONE, bubble.visibility)
    }

    @Test
    fun cancelFirstSelectionRestoresBubble() {
        val service = LassoOverlayService()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bubble = ComposeView(context).apply { visibility = View.GONE }
        setField(service, "bubbleView", bubble)
        LassoOverlayService::class.java.getDeclaredMethod(
            "dismissSelectionOverlay", Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, true)
        assertEquals(View.VISIBLE, bubble.visibility)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun busyFollowUpDoesNotQueueAnotherMessage() {
        val service = LassoOverlayService()
        val state = LassoOverlayService::class.java.getDeclaredField("analysisState").apply {
            isAccessible = true
        }.get(service) as MutableState<AnalysisState>
        val messages = LassoOverlayService::class.java.getDeclaredField("chatMessages").apply {
            isAccessible = true
        }.get(service) as List<ChatMessage>
        val send = LassoOverlayService::class.java.getDeclaredMethod("handleFollowUp", String::class.java)
            .apply { isAccessible = true }
        for (busy in listOf(AnalysisState.Analyzing(null), AnalysisState.Capturing)) {
            state.value = busy
            send.invoke(service, "Second question")
            assertTrue(messages.isEmpty())
            assertEquals(busy, state.value)
        }
    }
}
