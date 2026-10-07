package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.ChatMessage
import com.example.model.MessageSender
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ChatAutoScrollTest {
    @get:Rule val compose = createComposeRule()
    private val messages = mutableStateListOf<ChatMessage>().apply {
        repeat(20) { add(ChatMessage(sender = MessageSender.AI, text = "Message $it")) }
    }
    private var height by mutableStateOf(240.dp)
    private var captureId by mutableStateOf<String?>(null)
    private var pending by mutableStateOf(false)
    private lateinit var state: LazyListState

    private fun showChat() {
        compose.setContent {
            state = rememberLazyListState()
            val scroll = rememberChatAutoScroll(state, messages.lastOrNull(), captureId,
                messages.lastIndex, messages.size + if (pending) 1 else 0, true)
            LazyColumn(state = state, modifier = Modifier.height(height).then(scroll).testTag("list")) {
                items(messages, key = { it.id }) {
                    Box(Modifier.fillMaxWidth().height(if (it.text == "LONG") 900.dp else 56.dp)) {
                        Text(it.text)
                    }
                }
                if (pending) item(key = "chat_status") { Text("Analyzing") }
            }
        }
        compose.waitForIdle()
    }

    private fun readOlderMessages() {
        compose.onNodeWithTag("list").performTouchInput { swipeDown(durationMillis = 800) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(state.canScrollForward) }
    }

    @Test fun followsNewAnswerWhenNearBottom() {
        showChat()
        compose.runOnIdle { messages.add(ChatMessage(sender = MessageSender.AI, text = "New answer")) }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test fun longAnswerStartsAtItsBeginning() {
        showChat()
        compose.runOnIdle { messages.add(ChatMessage(sender = MessageSender.AI, text = "LONG")) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(messages.lastIndex, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertTrue(state.canScrollForward)
        }
    }

    @Test fun readerOfOldMessagesIsNotPulledDownByAnswerOrStatusRemoval() {
        pending = true
        showChat()
        readOlderMessages()
        val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        compose.runOnIdle {
            pending = false
            messages.add(ChatMessage(sender = MessageSender.AI, text = "Arrived while reading"))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(before, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
    }

    @Test fun ownQuestionResumesFollowingFromOldHistory() {
        showChat()
        readOlderMessages()
        compose.runOnIdle { messages.add(ChatMessage(sender = MessageSender.USER, text = "My new question")) }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test fun explicitCaptureResumesFollowingEvenWhenAnswerArrivesWhileChatHidden() {
        showChat()
        readOlderMessages()
        compose.runOnIdle {
            captureId = "new-capture"
            messages.add(ChatMessage(sender = MessageSender.AI, text = "New capture answer"))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test fun keyboardSizedViewportKeepsShortLastMessageVisible() {
        showChat()
        compose.runOnIdle { height = 130.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(state.canScrollForward) }
    }

    @Test fun viewportResizeDoesNotMoveReaderOfOldMessages() {
        showChat()
        readOlderMessages()
        val before = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        compose.runOnIdle { height = 130.dp }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(before, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
    }
}
