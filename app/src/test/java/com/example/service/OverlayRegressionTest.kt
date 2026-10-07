package com.example.service

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import com.example.model.MessageSender
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.robolectric.Robolectric
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
        assertEquals(View.VISIBLE, bubble.visibility)
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
    private fun field(service: LassoOverlayService, name: String): Any? =
        LassoOverlayService::class.java.getDeclaredField(name).apply { isAccessible = true }.get(service)

    private fun call(service: LassoOverlayService, name: String) {
        LassoOverlayService::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(service)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun bubbleSelectsImmediatelyAndRetainsExistingChatOnSecondSelection() {
        val service = Robolectric.buildService(LassoOverlayService::class.java).get()
        val context = ApplicationProvider.getApplicationContext<Context>()
        setField(service, "windowManager", context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        setField(service, "restoringSession", false)
        listOf("bubbleLifecycleOwner", "selectionLifecycleOwner", "chatLifecycleOwner").forEach {
            (field(service, it) as OverlayLifecycleOwner).onCreate()
        }
        MediaProjectionHolder.resultCode = Activity.RESULT_OK
        MediaProjectionHolder.resultData = Intent()
        try {
            call(service, "showFloatingBubble")
            val bubble = field(service, "bubbleView") as ComposeView
            bubble.performClick()
            assertNotNull(field(service, "selectionView"))
            assertNull(field(service, "chatView"))
            LassoOverlayService::class.java.getDeclaredMethod("dismissSelectionOverlay",
                Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(service, true)

            val messages = field(service, "chatMessages") as MutableList<ChatMessage>
            val crop = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            messages.add(ChatMessage(sender = MessageSender.USER, text = "capture", image = crop, isVisible = false))
            messages.add(ChatMessage(sender = MessageSender.AI, text = "Previous answer"))
            call(service, "showFloatingChatDialog")
            val chat = field(service, "chatView") as ComposeView
            val params = field(service, "chatParams")
            assertEquals(View.VISIBLE, bubble.visibility)
            bubble.performClick()
            assertSame(chat, field(service, "chatView"))
            assertEquals(View.GONE, chat.visibility)
            assertEquals(2, messages.size)
            assertSame(crop, messages.first().image)
            assertSame(params, field(service, "chatParams"))
            LassoOverlayService::class.java.getDeclaredMethod("dismissSelectionOverlay",
                Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(service, true)
            assertSame(chat, field(service, "chatView"))
            assertEquals(View.VISIBLE, chat.visibility)
            assertEquals(View.VISIBLE, bubble.visibility)
        } finally {
            service.onDestroy()
            MediaProjectionHolder.clear()
        }
    }

}
