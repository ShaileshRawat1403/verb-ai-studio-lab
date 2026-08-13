import re

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

# Fix curl codex intercept
old_curl = 'if (trimmed == "curl -fsSL https://claude.ai/install.sh | sh" || trimmed == "curl -fsSL https://codex.ai/install.sh | sh") {'
new_curl = 'if (trimmed == "curl -fsSL https://claude.ai/install.sh | sh" || trimmed == "curl -fsSL https://codex.openai.com/install.sh | sh") {'
text = text.replace(old_curl, new_curl)

# Fix prefix matching
old_prefix = """        val aiPrefixes = listOf("openai ", "chatgpt ", "claude ", "gemini ", "ai ", "codex ")
        val matchedPrefix = aiPrefixes.find { trimmed.startsWith(it) }

        if (matchedPrefix != null) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            val prefs = terminalView?.context?.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE)
            val defaultProvider = prefs?.getString("DEFAULT_AI_PROVIDER", "gemini") ?: "gemini"
            
            // Determine provider based on prefix or default setting
            val useOpenAI = when (matchedPrefix) {
                "openai ", "chatgpt " -> true
                "claude ", "gemini ", "codex " -> false
                else -> defaultProvider == "openai" // For "ai "
            }"""

new_prefix = """        val aiCommands = listOf("openai", "chatgpt", "claude", "gemini", "ai", "codex")
        val matchedCommand = aiCommands.find { trimmed == it || trimmed.startsWith("$it ") }

        if (matchedCommand != null) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            
            val prompt = if (trimmed.length > matchedCommand.length) {
                trimmed.substring(matchedCommand.length).trim()
            } else {
                ""
            }
            
            if (prompt.isEmpty()) {
                appendOutput("Usage: $matchedCommand <your prompt>\\n$ ")
                return
            }
            
            val prefs = terminalView?.context?.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE)
            val defaultProvider = prefs?.getString("DEFAULT_AI_PROVIDER", "gemini") ?: "gemini"
            
            // Determine provider based on prefix or default setting
            val useOpenAI = when (matchedCommand) {
                "openai", "chatgpt" -> true
                "claude", "gemini", "codex" -> false
                else -> defaultProvider == "openai" // For "ai"
            }"""

if old_prefix in text:
    text = text.replace(old_prefix, new_prefix)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Fixed AI routing successfully!")
else:
    print("Failed to find old prefix string.")
