import re

with open('app/src/main/java/com/example/verb/ui/TerminalSettingsSheet.kt', 'r') as f:
    text = f.read()

# I want to add error states and validation logic
add_imports = """
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
"""
# Assuming already there, let's just use it inline.

validation_logic = """
    var geminiError by remember { mutableStateOf(false) }
    var openAiError by remember { mutableStateOf(false) }

    fun validateKeys(): Boolean {
        var isValid = true
        if (geminiKey.isNotBlank() && !geminiKey.startsWith("AIzaSy")) {
            geminiError = true
            isValid = false
        } else {
            geminiError = false
        }
        
        if (openAiKey.isNotBlank() && !openAiKey.startsWith("sk-")) {
            openAiError = true
            isValid = false
        } else {
            openAiError = false
        }
        return isValid
    }
"""

text = text.replace("    var geminiKey", validation_logic + "\n    var geminiKey")

# Update Gemini Text field
gemini_tf = """            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it; geminiError = false },
                label = { Text("Gemini API Key") },
                isError = geminiError,
                supportingText = { if (geminiError) Text("Invalid Gemini API key format (must start with AIzaSy)", color = Color.Red) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )"""
text = re.sub(r'OutlinedTextField\([\s\S]*?geminiKey.*?singleLine = true\s*\)', gemini_tf, text)

# Update OpenAI Text field
openai_tf = """            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it; openAiError = false },
                label = { Text("OpenAI API Key") },
                isError = openAiError,
                supportingText = { if (openAiError) Text("Invalid OpenAI API key format (must start with sk-)", color = Color.Red) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )"""
text = re.sub(r'OutlinedTextField\([\s\S]*?openAiKey.*?singleLine = true\s*\)', openai_tf, text)

# Update Button onClick
btn_click = """            Button(
                onClick = {
                    if (validateKeys()) {
                        sharedPrefs.edit()
                            .putString("GEMINI_API_KEY", geminiKey)
                            .putString("OPENAI_API_KEY", openAiKey)
                            .putString("DEFAULT_AI_PROVIDER", defaultAiProvider)
                            .apply()
                        onDismiss()
                    }
                },"""
text = re.sub(r'Button\(\s*onClick = \{\s*sharedPrefs\.edit\(\)[\s\S]*?onDismiss\(\)\s*\},', btn_click, text)

with open('app/src/main/java/com/example/verb/ui/TerminalSettingsSheet.kt', 'w') as f:
    f.write(text)
print("Patched TerminalSettingsSheet")
