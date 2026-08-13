import re

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

old_send = """        } else if (shellProcess != null && shellProcess?.isAlive == true) {
            _sessionState.value = TerminalSessionState.RUNNING
            appendOutput("$cmd\\n")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Send command, and append a marker to know when it finishes so we can print the prompt
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
                sendText(res.output)
            }
            sendText("$ ")
        }"""

new_send = """        } else {
            if (shellProcess != null && shellProcess?.isAlive == false) {
                startRealShellFallback()
            }
            
            if (shellProcess != null && shellProcess?.isAlive == true) {
                _sessionState.value = TerminalSessionState.RUNNING
                appendOutput("$cmd\\n")
                
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
                    sendText(res.output)
                }
                sendText("$ ")
            }
        }"""

text = text.replace(old_send, new_send)

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
