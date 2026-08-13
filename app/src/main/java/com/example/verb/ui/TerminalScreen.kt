package com.example.verb.ui

import com.example.verb.model.VerbIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.verb.terminal.TerminalSessionState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.verb.terminal.AnsiTextParser
import com.example.verb.terminal.MobileTerminalKeyboard
import com.example.verb.terminal.SelectionChangeListener
import com.example.verb.terminal.TerminalRuntime
import com.example.verb.terminal.TerminalRuntimeAdapter
import com.example.verb.terminal.TermuxTerminalRuntimeAdapter
import com.termux.view.TerminalView

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.verb.viewmodel.TerminalTheme
import com.example.verb.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(
    terminalOutput: String,
    terminalRuntime: TerminalRuntimeAdapter? = null,
    terminalViewModel: TerminalViewModel? = null,
    onSendCommand: (String) -> Unit,
    onSendKey: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onInspectText: (String) -> Unit,
    onSubmitIntent: (VerbIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showNaturalLanguageSheet by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val currentTheme by (terminalViewModel?.terminalTheme?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(TerminalTheme.MIDNIGHT) })

    val isDark = currentTheme == TerminalTheme.MIDNIGHT

    val canvasBg = if (isDark) Color(0xFF0D0E12) else Color(0xFFF8FAFC)
    val headerBg = if (isDark) Color(0xFF161820) else Color(0xFFE2E8F0)
    val canvasTextColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val inputBarBg = if (isDark) Color(0xFF161820) else Color(0xFFE2E8F0)
    val inputTextColor = if (isDark) Color.White else Color(0xFF0F172A)
    val inputFieldBg = if (isDark) Color(0xFF0D0E12) else Color.White
    val shortcutChipBg = if (isDark) Color(0xFF222630) else Color(0xFFCBD5E1)
    val shortcutChipTextColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)

    val sessionState by (terminalRuntime?.sessionState?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(TerminalSessionState.FAILED) })

    val shellAccessibilityResult by (terminalViewModel?.shellAccessibilityResult?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    val rawDiagnosticOutput by (terminalViewModel?.rawDiagnosticOutput?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) })

    var commandInput by remember { mutableStateOf("") }
    var showAiHelper by remember { mutableStateOf(false) }

    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val handleKey: (String) -> Unit = { key ->
        when (key) {
            "UP" -> {
                val prev = terminalViewModel?.navigateHistoryUp()
                if (prev != null) {
                    commandInput = prev
                }
                onSendKey(key)
            }
            "DOWN" -> {
                val next = terminalViewModel?.navigateHistoryDown()
                if (next != null) {
                    commandInput = next
                }
                onSendKey(key)
            }
            else -> onSendKey(key)
        }
    }

    // Register SelectionChangeListener with TerminalRuntime for active exact selection monitoring
    DisposableEffect(terminalRuntime, onInspectText) {
        val listener = SelectionChangeListener { _, selectedText ->
            if (selectedText.isNotBlank()) {
                onInspectText(selectedText)
            }
        }
        terminalRuntime?.addSelectionChangeListener(listener)
        onDispose {
            terminalRuntime?.removeSelectionChangeListener(listener)
        }
    }

    // Auto-scroll terminal to bottom when new output arrives
    LaunchedEffect(terminalOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            androidx.compose.material3.ModalDrawerSheet(
                drawerContainerColor = headerBg,
                drawerContentColor = canvasTextColor
            ) {
                FileExplorerDrawer(
                    terminalRuntime = terminalRuntime,
                    isDark = isDark,
                    onFileClicked = { file ->
                        commandInput += file.name
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(canvasBg)
        ) {
            // Thin Terminal Header Bar
            Surface(
            modifier = Modifier.fillMaxWidth(),
            color = headerBg
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title and connection indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF6366F1),
                            modifier = Modifier
                                .clickable { showNaturalLanguageSheet = true }
                                .testTag("verb_nl_trigger_top")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verb",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = shortcutChipBg,
                            modifier = Modifier.clickable { showDiagnosticsSheet = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "local",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Reactive ConnectionState indicator pill in terminal header
                        val (statusText, statusColor) = when {
                            sessionState == TerminalSessionState.RUNNING -> "Connected" to Color(0xFF22C55E) // Green dot for interactive session
                            sessionState == TerminalSessionState.STARTING -> "Connecting..." to Color(0xFFEAB308) // Yellow dot
                            sessionState == TerminalSessionState.FAILED -> "Error" to Color(0xFFEF4444) // Red dot for errors
                            sessionState == TerminalSessionState.EXITED -> "Disconnected" to Color(0xFF94A3B8)
                            sessionState == TerminalSessionState.STOPPING -> "Stopping..." to Color(0xFFF97316)
                            else -> "Disconnected" to Color(0xFF94A3B8)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable { showDiagnosticsSheet = true }
                                .testTag("connection_state_indicator")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor, CircleShape)
                                        .testTag("connection_status_dot")
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                        }
                    }

                    // Quick Action Buttons (Diagnostics Button, Clear, Theme Switcher)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { terminalViewModel?.runEnvironmentDiagnostics() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("btn_run_env_diagnostics")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Diagnostics", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_open_file_explorer")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Files",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showAiHelper = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_ai_terminal_help")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Help",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { terminalViewModel?.toggleTheme() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_toggle_terminal_theme")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Toggle Terminal Theme",
                                tint = if (isDark) Color(0xFFFACC15) else Color(0xFF6366F1),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showDiagnosticsSheet = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_open_diagnostics")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Terminal diagnostics and logs",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onClearTerminal,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_clear_terminal_header")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Clear terminal",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Permission Error Alert Banner if ShellAccessibilityCheck denies access
        if (shellAccessibilityResult?.permissionError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("shell_accessibility_permission_error"),
                color = Color(0xFF7F1D1D),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Permission Error",
                        tint = Color(0xFFFCA5A5),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = shellAccessibilityResult?.permissionError ?: "Permission Error: Shell access denied",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // High-Performance Modern Terminal Canvas Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .background(
                    color = if (isDark) Color(0xFF0A0C10) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(14.dp)
        ) {
            val termuxAdapter = (terminalRuntime as? TerminalRuntime)?.unwrapTermuxAdapter
                ?: (terminalRuntime as? TermuxTerminalRuntimeAdapter)
            val hasNativePty = false // FORCED TO FALSE FOR HYBRID APPROACH

            if (hasNativePty) {
                AndroidView(
                    factory = { ctx ->
                        termuxAdapter?.terminalView ?: TerminalView(ctx, null).also {
                            termuxAdapter?.bindTerminalView(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("termux_terminal_view")
                )
            } else {
                val rawText = if (terminalOutput.isNotBlank()) {
                    terminalOutput + commandInput
                } else {
                    "Verb Local PTY Active [Universal Command Engine v2.0 Ready]\nType 'help', 'curl -fsSL ... | sh', 'claude', 'codex', or tap a shortcut below.\n$ " + commandInput
                }
                
                var cursorVisible by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        kotlinx.coroutines.delay(500)
                        cursorVisible = !cursorVisible
                    }
                }

                val cursorChar = if (cursorVisible) "█" else " "

                val annotatedOutput = remember<androidx.compose.ui.text.AnnotatedString>(rawText, cursorVisible, isDark) {
                    try {
                        val defaultTextColor: Color = if (isDark) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                        val parsed = AnsiTextParser.parse(rawText + cursorChar, defaultColor = defaultTextColor)
                        AnsiTextParser.applyBasicSyntaxHighlighting(parsed, isDark)
                    } catch (e: Exception) {
                        androidx.compose.ui.text.AnnotatedString("CRASH: ${e.message}")
                    }
                }

                var showContextMenu by remember { mutableStateOf(false) }
                val clipboardManager = LocalClipboardManager.current

                Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).pointerInput(Unit) {
                    detectTapGestures(onLongPress = { showContextMenu = true })
                }) {
                    SelectionContainer {
                        Text(
                            text = annotatedOutput,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("terminal_output_text")
                        )
                    }

                    DropdownMenu(
                        expanded = showContextMenu,
                        onDismissRequest = { showContextMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Output") },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rawText))
                                showContextMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Paste to Input") },
                            onClick = {
                                clipboardManager.getText()?.let {
                                    commandInput += it.text
                                }
                                showContextMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Autocomplete suggestions row
        val suggestions = remember(commandInput, terminalViewModel) {
            terminalViewModel?.getAutocompleteSuggestions(commandInput) ?: emptyList()
        }

        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(if (isDark) Color(0xFF1B1E29) else Color(0xFFE2E8F0))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suggestions:",
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                suggestions.forEach { sug ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDark) Color(0xFF6366F1).copy(alpha = 0.25f) else Color(0xFF6366F1).copy(alpha = 0.15f),
                        modifier = Modifier
                            .clickable { commandInput = sug }
                            .testTag("autocomplete_suggestion_$sug")
                    ) {
                        Text(
                            text = sug,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF3730A3),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Terminal Shortcuts Toolbar above the input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(if (isDark) Color(0xFF14161F) else Color(0xFFCBD5E1))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val shortcuts = listOf(
                "curl -fsSL https://claude.ai/install.sh | sh",
                "curl -fsSL https://codex.openai.com/install.sh | sh",
                "claude --version",
                "codex --version",
                "git status",
                "clear",
                "ls -la",
                "pwd",
                "help",
                "top",
                "whoami",
                "date",
                "df -h",
                "env",
                "node -v"
            )
            shortcuts.forEach { shortcut ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = shortcutChipBg,
                    modifier = Modifier
                        .clickable {
                            if (shortcut == "clear") {
                                onClearTerminal()
                                onSendCommand("clear")
                            } else {
                                onSendCommand(shortcut)
                            }
                        }
                        .testTag("btn_shortcut_$shortcut")
                ) {
                    Text(
                        text = shortcut,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = shortcutChipTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Interactive Command Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(inputBarBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                color = Color(0xFF6366F1),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        "Enter command...",
                        color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input"),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = inputTextColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrect = false,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = if (isDark) Color(0xFF222630) else Color(0xFF94A3B8),
                    focusedContainerColor = inputFieldBg,
                    unfocusedContainerColor = inputFieldBg
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        onSendCommand(commandInput)
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_send_terminal_command")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = if (commandInput.isNotBlank()) Color(0xFF6366F1) else Color(0xFF475569),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Contextual Touch Control Strip for P0.3
        MobileTerminalKeyboard(
            onSendKey = handleKey,
            onSendCommand = onSendCommand,
            terminalOutput = terminalOutput,
            onInspectOutput = onInspectText
        )
    }

    // Natural Language Sheet Modal
    if (showNaturalLanguageSheet) {
        VerbNaturalLanguageSheet(
            onDismiss = { showNaturalLanguageSheet = false },
            onSubmitIntent = { intent ->
                showNaturalLanguageSheet = false
                onSubmitIntent(intent)
            }
        )
    }

    // Diagnostics & Session Logger Modal
    if (showDiagnosticsSheet) {
        TerminalDiagnosticsSheet(
            terminalRuntime = terminalRuntime,
            onDismiss = { showDiagnosticsSheet = false }
        )
    }

    // Raw Diagnostic Output Dialog
    if (rawDiagnosticOutput != null) {
        AlertDialog(
            onDismissRequest = { terminalViewModel?.clearDiagnosticOutput() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Terminal Raw Diagnostics (env & \$PATH)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .background(Color(0xFF090A0E), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = rawDiagnosticOutput!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 16.sp,
                            modifier = Modifier.testTag("raw_diagnostics_output_text")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { terminalViewModel?.clearDiagnosticOutput() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_close_raw_diagnostics")
                ) {
                    Text("Close", fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF161820),
            titleContentColor = Color.White,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // AI Helper Dialog
    if (showAiHelper) {
        var aiResponse by remember { mutableStateOf("Analyzing terminal output...") }
        
        LaunchedEffect(terminalOutput) {
            aiResponse = "Analyzing terminal output..."
            aiResponse = com.example.verb.terminal.TerminalAiHelper.analyzeTerminalOutput(terminalOutput)
        }
        
        AlertDialog(
            onDismissRequest = { showAiHelper = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Terminal Assistant",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color(0xFF090A0E), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = aiResponse,
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAiHelper = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Got it", fontSize = 12.sp, color = Color.White)
                }
            },
            containerColor = Color(0xFF161820),
            titleContentColor = Color.White,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
}
