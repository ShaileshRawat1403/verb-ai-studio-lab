import re

with open('app/src/main/java/com/example/verb/ui/TerminalSettingsSheet.kt', 'r') as f:
    text = f.read()

# I'll just move the variable declarations up.
# From:
#     fun validateKeys(): Boolean { ... }
#     var geminiKey by ...
#     var openAiKey by ...
#     var defaultAiProvider by ...

to_move = """    var geminiKey by remember { mutableStateOf(sharedPrefs.getString("GEMINI_API_KEY", "") ?: "") }
    var openAiKey by remember { mutableStateOf(sharedPrefs.getString("OPENAI_API_KEY", "") ?: "") }
    var defaultAiProvider by remember { mutableStateOf(sharedPrefs.getString("DEFAULT_AI_PROVIDER", "gemini") ?: "gemini") }"""

text = text.replace(to_move, "")
text = text.replace("    var geminiError", to_move + "\n\n    var geminiError")

with open('app/src/main/java/com/example/verb/ui/TerminalSettingsSheet.kt', 'w') as f:
    f.write(text)
print("Fixed Settings order")
