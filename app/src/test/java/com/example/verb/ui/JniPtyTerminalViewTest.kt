package com.example.verb.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.verb.terminal.JniPtyStreamController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JniPtyTerminalViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `terminal view displays buffer text and sends input`() {
        val controller = JniPtyStreamController()
        var lastSentInput = ""

        controller.appendRawText("Terminal Ready\n$ ")

        composeTestRule.setContent {
            JniPtyTerminalView(
                bufferFlow = controller.bufferFlow,
                onSendInput = { lastSentInput = it },
                onClearBuffer = { controller.clearBuffer() }
            )
        }

        composeTestRule.onNodeWithTag("jni_pty_terminal_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("jni_pty_scrollable_buffer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("jni_pty_input_field").assertIsDisplayed()

        composeTestRule.onNodeWithTag("jni_pty_input_field").performTextInput("echo hello")
        composeTestRule.onNodeWithTag("jni_pty_send_button").performClick()

        assertEquals("echo hello\n", lastSentInput)
    }

    @Test
    fun `clear button empties the streaming buffer`() {
        val controller = JniPtyStreamController()
        controller.appendRawText("Output to clear")

        composeTestRule.setContent {
            JniPtyTerminalView(
                controller = controller
            )
        }

        composeTestRule.onNodeWithTag("jni_pty_clear_button").performClick()
        assertEquals("", controller.bufferFlow.value.text)
    }

    @Test
    fun `hidden keyboard input field passes soft keyboard text events to PTY master`() {
        val controller = JniPtyStreamController()
        val receivedInputs = mutableListOf<String>()

        composeTestRule.setContent {
            JniPtyTerminalView(
                bufferFlow = controller.bufferFlow,
                onSendInput = { receivedInputs.add(it) },
                onClearBuffer = { controller.clearBuffer() }
            )
        }

        composeTestRule.onNodeWithTag("jni_pty_hidden_input").performTextInput("a")
        assertEquals(listOf("a"), receivedInputs)
    }
}
