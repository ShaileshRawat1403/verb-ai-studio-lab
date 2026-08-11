package com.example.verb.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.verb.terminal.JniPtyStreamController
import kotlinx.coroutines.flow.StateFlow

/**
 * A Composable terminal view that uses a StateFlow to stream data from the JNI PTY interface
 * and display it in a scrollable, styled text buffer.
 * Incorporates a hidden keyboard input field that captures soft keyboard events and passes
 * them through directly to the PTY master interface.
 * Supports long-press copy of text in the buffer and a context menu for clipboard paste into shell input.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JniPtyTerminalView(
    bufferFlow: StateFlow<AnnotatedString>,
    onSendInput: (String) -> Unit,
    onClearBuffer: () -> Unit,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = true
) {
    var inputText by remember { mutableStateOf("") }
    var hiddenTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val annotatedBuffer by bufferFlow.collectAsStateWithLifecycle()
    val verticalScrollState = rememberScrollState()

    var showContextMenu by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    // Auto-scroll to bottom on new stream content
    LaunchedEffect(annotatedBuffer.text.length) {
        if (annotatedBuffer.text.isNotEmpty()) {
            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E12))
            .testTag("jni_pty_terminal_container")
    ) {
        // Hidden TextField capturing direct soft keyboard input events for PTY master pass-through
        BasicTextField(
            value = hiddenTextFieldValue,
            onValueChange = { newValue ->
                val oldText = hiddenTextFieldValue.text
                val newText = newValue.text
                if (newText.length > oldText.length) {
                    val typedChar = newText.substring(oldText.length)
                    onSendInput(typedChar)
                } else if (newText.length < oldText.length) {
                    // Backspace captured from soft keyboard
                    onSendInput("\b")
                }
                hiddenTextFieldValue = TextFieldValue("")
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Unspecified
            ),
            keyboardActions = KeyboardActions(
                onAny = {
                    onSendInput("\n")
                    hiddenTextFieldValue = TextFieldValue("")
                }
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.Enter -> {
                                onSendInput("\n")
                                true
                            }
                            Key.Backspace -> {
                                onSendInput("\b")
                                true
                            }
                            Key.Tab -> {
                                onSendInput("\t")
                                true
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
                .testTag("jni_pty_hidden_input")
        )

        // Terminal Header Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("jni_pty_header_bar"),
            color = Color(0xFF161820),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "JNI PTY Terminal",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JNI PTY Stream",
                        color = Color(0xFFF1F5F9),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Live status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isStreaming) Color(0xFF22C55E) else Color(0xFFEF4444),
                                shape = CircleShape
                            )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (annotatedBuffer.text.isNotEmpty()) {
                                clipboardManager.setText(AnnotatedString(annotatedBuffer.text))
                            }
                        },
                        modifier = Modifier.testTag("jni_pty_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Terminal Buffer",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    IconButton(
                        onClick = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        modifier = Modifier.testTag("jni_pty_toggle_keyboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Toggle Soft Keyboard",
                            tint = Color(0xFF38BDF8)
                        )
                    }

                    IconButton(
                        onClick = onClearBuffer,
                        modifier = Modifier.testTag("jni_pty_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Clear Terminal Buffer",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        // Scrollable Text Buffer with Long-press Context Menu & Clipboard Copy
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(verticalScrollState)
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                    onLongClick = {
                        if (annotatedBuffer.text.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(annotatedBuffer.text))
                        }
                        showContextMenu = true
                    }
                )
                .testTag("jni_pty_scrollable_buffer")
        ) {
            SelectionContainer {
                Text(
                    text = annotatedBuffer.ifEmpty { AnnotatedString("$ \n") },
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jni_pty_text_content")
                )
            }

            // Context Menu on Long-Press
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                modifier = Modifier
                    .background(Color(0xFF1E2230))
                    .testTag("jni_pty_context_menu")
            ) {
                DropdownMenuItem(
                    text = { Text("Copy Terminal Buffer", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = Color(0xFF38BDF8)
                        )
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(annotatedBuffer.text))
                        showContextMenu = false
                    },
                    modifier = Modifier.testTag("jni_pty_context_copy")
                )

                DropdownMenuItem(
                    text = { Text("Paste into Shell Input", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Color(0xFF22C55E)
                        )
                    },
                    onClick = {
                        val pasted = clipboardManager.getText()?.text.orEmpty()
                        if (pasted.isNotEmpty()) {
                            inputText += pasted
                        }
                        showContextMenu = false
                    },
                    modifier = Modifier.testTag("jni_pty_context_paste")
                )

                DropdownMenuItem(
                    text = { Text("Paste & Execute Immediately", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Execute",
                            tint = Color(0xFFF59E0B)
                        )
                    },
                    onClick = {
                        val pasted = clipboardManager.getText()?.text.orEmpty()
                        if (pasted.isNotEmpty()) {
                            onSendInput(pasted + "\n")
                        }
                        showContextMenu = false
                    },
                    modifier = Modifier.testTag("jni_pty_context_paste_execute")
                )
            }
        }

        // Quick Key Shortcut Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161820))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickKeys = listOf(
                "Paste" to "PASTE_CLIPBOARD",
                "Ctrl+C" to "\u0003",
                "Tab" to "\t",
                "Clear" to "clear\n",
                "LS" to "ls -l\n",
                "PWD" to "pwd\n"
            )
            quickKeys.forEach { (label, value) ->
                Button(
                    onClick = {
                        if (value == "PASTE_CLIPBOARD") {
                            val pasted = clipboardManager.getText()?.text.orEmpty()
                            if (pasted.isNotEmpty()) {
                                inputText += pasted
                            }
                        } else if (value == "clear\n") {
                            onClearBuffer()
                        } else {
                            onSendInput(value)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF262936),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // Input Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("jni_pty_input_bar"),
            color = Color(0xFF161820)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Send shell command...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0D0E12),
                        unfocusedContainerColor = Color(0xFF0D0E12),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotEmpty()) {
                                onSendInput(inputText + "\n")
                                inputText = ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("jni_pty_input_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotEmpty()) {
                            onSendInput(inputText + "\n")
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .background(Color(0xFF38BDF8), CircleShape)
                        .size(44.dp)
                        .testTag("jni_pty_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

/**
 * Convenience Composable that binds directly with a [JniPtyStreamController].
 */
@Composable
fun JniPtyTerminalView(
    controller: JniPtyStreamController,
    modifier: Modifier = Modifier
) {
    val isStreaming by controller.isStreaming.collectAsStateWithLifecycle()
    JniPtyTerminalView(
        bufferFlow = controller.bufferFlow,
        onSendInput = { controller.sendInput(it) },
        onClearBuffer = { controller.clearBuffer() },
        isStreaming = isStreaming,
        modifier = modifier
    )
}

