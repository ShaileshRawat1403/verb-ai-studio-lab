import re

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

# Add import for Settings icon
text = text.replace('import androidx.compose.material.icons.filled.Refresh', 'import androidx.compose.material.icons.filled.Refresh\nimport androidx.compose.material.icons.filled.Settings')

# Add showSettingsSheet state variable
state_var = "    var showNaturalLanguageSheet by remember { mutableStateOf(false) }"
new_state_var = "    var showSettingsSheet by remember { mutableStateOf(false) }\n" + state_var
text = text.replace(state_var, new_state_var)

# Add Settings button to UI
old_buttons = """                        IconButton(
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
                        }"""

new_buttons = old_buttons + """
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569),
                                modifier = Modifier.size(18.dp)
                            )
                        }"""
text = text.replace(old_buttons, new_buttons)

# Add TerminalSettingsSheet call
sheet_call = "    if (showNaturalLanguageSheet) {"
new_sheet_call = """    if (showSettingsSheet) {
        TerminalSettingsSheet(onDismiss = { showSettingsSheet = false })
    }
""" + sheet_call
text = text.replace(sheet_call, new_sheet_call)

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)

print("Updated TerminalScreen.kt")
