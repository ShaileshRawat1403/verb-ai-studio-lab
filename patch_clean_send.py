import re

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

match = re.search(r'    override fun sendCommand\(cmd: String\) \{.*?(?=    override fun sendControlKey)', text, re.DOTALL)
if match:
    old_method = match.group(0)
    new_method = """    override fun sendCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            clearBuffer()
            return
        }

        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\\n")
            return
        }
        
        if (shellProcess != null && shellProcess?.isAlive == false) {
            startRealShellFallback()
        }
        
        if (shellProcess != null && shellProcess?.isAlive == true) {
            _sessionState.value = TerminalSessionState.RUNNING
            appendOutput("$cmd\\n")
            
            // Handle cd natively in our process so we can track directory changes
            if (trimmed.startsWith("cd ")) {
                val dir = trimmed.substringAfter("cd ").trim()
                val targetDir = if (dir.startsWith("/")) java.io.File(dir) else java.io.File(activeWorkingDir, dir)
                if (targetDir.exists() && targetDir.isDirectory) {
                    activeWorkingDir = targetDir
                    // We must restart the shell in the new directory for ProcessBuilder
                    shellProcess?.destroy()
                    startRealShellFallback()
                    return
                } else {
                    appendOutput("cd: $dir: No such file or directory\\n$ ")
                    return
                }
            }
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    shellOut?.write(("$cmd ; echo __VERB_CMD_DONE__\\n").toByteArray())
                    shellOut?.flush()
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("Error: ${e.message}\\n$ ")
                    }
                }
            }
        } else {
            session = null
            _sessionState.value = TerminalSessionState.FAILED
            appendOutput("$cmd\\n")
            val res = TerminalCommandEngine.executeCommand(cmd, activeWorkingDir)
            if (res.shouldClearBuffer) {
                clearBuffer()
                return
            }
            if (res.newWorkingDir != null) {
                activeWorkingDir = res.newWorkingDir
            }
            if (res.output.isNotEmpty()) {
                appendOutput(res.output)
            }
            appendOutput("$ ")
        }
    }
"""
    text = text.replace(old_method, new_method)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Re-added shellProcess logic to sendCommand")
else:
    print("Failed to find sendCommand block")
