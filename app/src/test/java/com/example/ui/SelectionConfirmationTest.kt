package com.example.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SelectionConfirmationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dragRequiresConfirmationAndRetryDiscardsSelection() {
        var captures = 0
        var dismissals = 0
        compose.setContent {
            LassoSelectionContent(onSelectionConfirmed = { _, _ -> captures++ }, onDismiss = { dismissals++ })
        }
        fun drag() {
            compose.onNodeWithTag("lasso_selection_screen").performTouchInput {
                swipe(Offset(width * .2f, height * .35f), Offset(width * .7f, height * .65f))
            }
            compose.waitForIdle()
        }
        drag()
        assertEquals(0, captures)
        compose.onNodeWithTag("lasso_undo_button").performClick()
        compose.onNodeWithTag("selection_confirm").assertIsNotEnabled()
        assertEquals(0, dismissals)
        drag()
        compose.onNodeWithTag("selection_confirm").performClick()
        assertEquals(1, captures)
        compose.onNodeWithTag("lasso_close_button").performClick()
        assertEquals(1, dismissals)
    }
}
