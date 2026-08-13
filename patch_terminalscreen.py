import re

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

if "import androidx.compose.ui.platform.LocalContext" not in text:
    text = text.replace("import androidx.compose.ui.platform.LocalClipboardManager", "import androidx.compose.ui.platform.LocalClipboardManager\nimport androidx.compose.ui.platform.LocalContext")

text = text.replace("var showSettingsSheet by remember { mutableStateOf(false) }", "val context = LocalContext.current\n    var showSettingsSheet by remember { mutableStateOf(false) }")

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
print("Patched TerminalScreen")
