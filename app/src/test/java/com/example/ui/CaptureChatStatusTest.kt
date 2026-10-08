package com.example.ui

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.model.AnalysisState
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class CaptureChatStatusTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf<AnalysisState>(AnalysisState.Analyzing(null))
    private val messages = mutableStateListOf<ChatMessage>()
    private val loadingText = ApplicationProvider.getApplicationContext<Context>()
        .getString(R.string.analyzing_heading)

    private fun showLoadingChat() {
        compose.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                FloatingChatDialogContent(
                    analysisState = state.value, chatMessages = messages,
                    onSendFollowUp = {}, onRetry = {}, onClose = {},
                    onDragDelta = { _, _ -> }, onResizeDelta = { _, _ -> }
                )
            }
        }
        compose.onNodeWithText(loadingText).assertIsDisplayed()
    }

    @Test fun loadingIsVisibleBeforeAnswerAndThenReplacedByNewMessage() {
        showLoadingChat()
        compose.runOnIdle {
            messages.add(ChatMessage(sender = MessageSender.AI, text = "Captured region answer"))
            state.value = AnalysisState.Success("Captured region answer", null)
        }
        compose.onNodeWithText(loadingText).assertDoesNotExist()
        compose.onNodeWithText("Captured region answer").assertIsDisplayed()
    }

    @Test fun loadingIsReplacedByErrorInOpenChat() {
        showLoadingChat()
        compose.runOnIdle { state.value = AnalysisState.Error("Test request failed", null) }
        compose.onNodeWithText(loadingText).assertDoesNotExist()
        compose.onNodeWithText("Test request failed").assertIsDisplayed()
    }
}
