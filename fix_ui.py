import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

old_code = """                val cursorChar = if (cursorVisible) "█" else " "
                val annotatedOutput = remember<androidx.compose.ui.text.AnnotatedString>(rawText, cursorVisible, isDark) {
                    val defaultTextColor: Color = if (isDark) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                    val parsed = AnsiTextParser.parse(rawText + cursorChar, defaultColor = defaultTextColor)
                    AnsiTextParser.applyBasicSyntaxHighlighting(parsed, isDark)
                }"""

new_code = """                val cursorChar = if (cursorVisible) "█" else " "
                val annotatedOutput = remember<androidx.compose.ui.text.AnnotatedString>(rawText, cursorVisible, isDark) {
                    try {
                        val defaultTextColor: Color = if (isDark) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                        val parsed = AnsiTextParser.parse(rawText + cursorChar, defaultColor = defaultTextColor)
                        AnsiTextParser.applyBasicSyntaxHighlighting(parsed, isDark)
                    } catch (e: Exception) {
                        androidx.compose.ui.text.AnnotatedString(rawText + cursorChar)
                    }
                }"""

if old_code in text:
    text = text.replace(old_code, new_code)
    with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
        f.write(text)
    print("Replaced successfully")
else:
    print("Could not find block")
