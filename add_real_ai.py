import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

old_cmd = """    override fun sendCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            clearBuffer()
            return
        }

        _isSessionActive.value = true

        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {"""

new_cmd = """    override fun sendCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            clearBuffer()
            return
        }

        if (trimmed.startsWith("claude ") || trimmed.startsWith("gemini ") || trimmed.startsWith("ai ") || trimmed.startsWith("codex ")) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val apiKey = com.example.verb.BuildConfig.GEMINI_API_KEY.trim('"', ' ')
                    if (apiKey.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("Error: GEMINI_API_KEY is missing. Please set it in the AI Studio Secrets panel.\\n$ ")
                        }
                        return@launch
                    }
                    val model = com.google.ai.client.generativeai.GenerativeModel(
                        modelName = "gemini-3.5-flash",
                        apiKey = apiKey
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("[Thinking...]\\n")
                    }
                    val response = model.generateContent(prompt)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput(response.text + "\\n$ ")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("AI Error: ${e.message}\\n$ ")
                    }
                }
            }
            return
        }

        _isSessionActive.value = true

        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {"""

if old_cmd in text:
    text = text.replace(old_cmd, new_cmd)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Injected real AI CLI successfully")
else:
    print("Could not find block")
