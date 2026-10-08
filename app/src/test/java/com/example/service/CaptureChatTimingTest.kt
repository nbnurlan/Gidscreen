package com.example.service

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import android.media.projection.MediaProjection
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.network.GeminiKeyStore
import com.example.network.GeminiModelManager
import com.example.network.GeminiService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [CaptureChatTimingTest.PendingCapture::class])
class CaptureChatTimingTest {
    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var service: LassoOverlayService
    private lateinit var client: OkHttpClient
    private lateinit var originalInterceptors: List<Interceptor>
    private var originalKey = ""
    private var requestStarted = CompletableDeferred<Unit>()
    private var response = CompletableDeferred<Pair<Int, String>>()

    // Hold capture and HTTP completion independently, exercising the actual service entry point
    // without a device projection, real credentials, network access or production test hooks.
    @Implements(value = ScreenCaptureHelper::class, isInAndroidSdk = false)
    class PendingCapture {
        @Implementation
        @Suppress("UNUSED_PARAMETER")
        fun captureArea(context: Context, projection: MediaProjection, path: Path?, bounds: RectF,
            minTimestamp: Long, continuation: Continuation<Bitmap?>): Any? {
            started.complete(Unit)
            val waitForCapture: suspend () -> Bitmap? = { result.await() }
            return waitForCapture.startCoroutineUninterceptedOrReturn(continuation)
        }

        companion object {
            var started = CompletableDeferred<Unit>()
            var result = CompletableDeferred<Bitmap?>()
        }
    }

    private fun field(target: Any, name: String) = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }
    private fun get(name: String): Any? = field(service, name).get(service)
    private fun call(name: String) = service.javaClass.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(service)
    @Suppress("UNCHECKED_CAST")
    private val state get() = get("analysisState") as MutableState<AnalysisState>
    @Suppress("UNCHECKED_CAST")
    private val messages get() = get("chatMessages") as List<ChatMessage>
    private val chat get() = get("chatView") as ComposeView?
    private val bubble get() = get("bubbleView") as ComposeView
    private val windows get() = Shadow.extract<ShadowWindowManagerImpl>(get("windowManager")).views

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        File(context.noBackupFilesDir, "floating-chat").deleteRecursively()
        context.getSharedPreferences("floating-chat-window", Context.MODE_PRIVATE).edit().clear().commit()
        PendingCapture.started = CompletableDeferred()
        PendingCapture.result = CompletableDeferred()
        GeminiModelManager.init(context)
        GeminiModelManager.setSelectedModel(context, "gemini-2.5-flash")
        originalKey = GeminiKeyStore.get()
        field(GeminiKeyStore, "value").set(null, "test-only-placeholder")
        client = field(GeminiService, "client").get(null) as OkHttpClient
        originalInterceptors = client.interceptors
        field(client, "interceptors").set(client, listOf(Interceptor { chain ->
            val pendingResponse = response
            requestStarted.complete(Unit)
            val (code, body) = runBlocking { pendingResponse.await() }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("Test response")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }))

        service = Robolectric.buildService(LassoOverlayService::class.java).get()
        field(service, "windowManager").set(service, context.getSystemService(Context.WINDOW_SERVICE))
        field(service, "sessionStore").set(service, ChatSessionStore(context))
        field(service, "restoringSession").set(service, false)
        listOf("bubbleLifecycleOwner", "selectionLifecycleOwner", "chatLifecycleOwner").forEach {
            (get(it) as OverlayLifecycleOwner).onCreate()
        }
        MediaProjectionHolder.mediaProjection = Shadow.newInstanceOf(MediaProjection::class.java)
        call("showFloatingBubble")
    }

    @After fun tearDown() {
        response.complete(500 to "{}")
        PendingCapture.result.complete(null)
        if (::service.isInitialized) service.onDestroy()
        if (::client.isInitialized) field(client, "interceptors").set(client, originalInterceptors)
        field(GeminiKeyStore, "value").set(null, originalKey)
        Dispatchers.resetMain()
    }

    private fun confirm(): Job {
        bubble.performClick()
        val parent = (get("serviceScope") as CoroutineScope).coroutineContext[Job]!!
        val previous = parent.children.toSet()
        service.javaClass.getDeclaredMethod("onLassoSelected", Path::class.java, RectF::class.java)
            .apply { isAccessible = true }.invoke(service, null, RectF(0f, 0f, 4f, 4f))
        return parent.children.single { it !in previous }
    }

    private suspend fun finishCapture(): Bitmap {
        PendingCapture.started.await()
        val crop = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        PendingCapture.result.complete(crop)
        requestStarted.await()
        assertEquals(AnalysisState.Analyzing(crop), state.value)
        assertEquals(View.VISIBLE, chat!!.visibility)
        assertEquals(View.VISIBLE, bubble.visibility)
        assertNull(get("selectionView"))
        return crop
    }

    private fun answer(text: String = "New answer") {
        response.complete(200 to """{"candidates":[{"content":{"parts":[{"text":"$text"}]}}]}""")
    }

    @Test fun opensOnlyAfterCaptureAndKeepsSameWindowThroughSlowAnswerAndSecondSelection() = runTest(dispatcher) {
        val firstRequest = confirm()
        PendingCapture.started.await()
        assertEquals(AnalysisState.Capturing, state.value)
        assertNull(chat)
        assertFalse(requestStarted.isCompleted)

        val firstCrop = finishCapture()
        val firstChat = chat!!
        val params = get("chatParams")
        assertEquals(1, messages.size)
        assertSame(firstCrop, messages.single().image)
        assertTrue(ChatSessionStore(context).load().interrupted)
        delay(60_000) // Virtual time: a slow response must leave the loading chat open.
        assertEquals(AnalysisState.Analyzing(firstCrop), state.value)
        assertSame(firstChat, chat)
        answer()
        firstRequest.join()
        assertEquals(AnalysisState.Success("New answer", firstCrop), state.value)
        assertEquals(MessageSender.AI, messages.last().sender)
        assertSame(firstChat, chat)
        assertEquals(2, windows.size) // One bubble and one chat, no duplicate response window.

        val originalMessages = messages.toList()
        context.getSharedPreferences("floating-chat-window", Context.MODE_PRIVATE)
            .edit().putString("draft", "Keep my draft").commit()
        PendingCapture.started = CompletableDeferred()
        PendingCapture.result = CompletableDeferred()
        requestStarted = CompletableDeferred()
        response = CompletableDeferred()
        val secondRequest = confirm()
        PendingCapture.started.await()
        assertSame(firstChat, chat)
        assertEquals(View.GONE, firstChat.visibility)
        val secondCrop = finishCapture()
        assertSame(firstChat, chat)
        assertSame(params, get("chatParams"))
        assertEquals(originalMessages, messages.take(2))
        assertSame(firstCrop, messages.first().image)
        answer("Second answer")
        secondRequest.join()
        assertEquals(AnalysisState.Success("Second answer", secondCrop), state.value)
        assertEquals(4, messages.size)
        assertSame(firstChat, chat)
        assertEquals(2, windows.size)
        assertEquals("Keep my draft", context.getSharedPreferences("floating-chat-window", Context.MODE_PRIVATE)
            .getString("draft", null))
        val restored = ChatSessionStore(context).load()
        assertFalse(restored.interrupted)
        assertEquals(messages.map { it.id }, restored.messages.map { it.id })
    }

    @Test fun requestFailureAppearsInAlreadyOpenChat() = runTest(dispatcher) {
        val request = confirm()
        val crop = finishCapture()
        val openChat = chat
        response.complete(500 to """{"error":{"message":"Test failure"}}""")
        request.join()
        assertTrue((state.value as AnalysisState.Error).message.contains("Test failure"))
        assertSame(crop, (state.value as AnalysisState.Error).thumbnail)
        assertSame(openChat, chat)
        assertEquals(View.VISIBLE, chat!!.visibility)
        assertEquals(1, messages.size)
        assertEquals(2, windows.size)
    }

    private suspend fun closeBeforeResponse(fail: Boolean) {
        val request = confirm()
        finishCapture()
        context.getSharedPreferences("floating-chat-window", Context.MODE_PRIVATE)
            .edit().putBoolean("open", false).commit()
        call("hideFloatingChatDialog")
        if (fail) response.complete(500 to "{}") else answer()
        request.join()
        assertNull(chat)
        assertEquals(listOf(bubble), windows)
        assertFalse(context.getSharedPreferences("floating-chat-window", Context.MODE_PRIVATE).getBoolean("open", true))
        assertTrue(if (fail) state.value is AnalysisState.Error else state.value is AnalysisState.Success)
    }

    @Test fun lateAnswerDoesNotReopenClosedChat() = runTest(dispatcher) { closeBeforeResponse(fail = false) }
    @Test fun lateErrorDoesNotReopenClosedChat() = runTest(dispatcher) { closeBeforeResponse(fail = true) }

    @Test fun nullCaptureKeepsExistingErrorFlowWithoutSendingAiRequest() = runTest(dispatcher) {
        val request = confirm()
        PendingCapture.started.await()
        assertNull(chat)
        PendingCapture.result.complete(null)
        request.join()
        assertTrue(state.value is AnalysisState.Error)
        assertEquals(View.VISIBLE, chat!!.visibility)
        assertTrue(messages.isEmpty())
        assertFalse(requestStarted.isCompleted)
    }
}
