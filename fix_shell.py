with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

old_cd_logic = """            // Handle cd natively in our process
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
                    shellOut?.write(("$cmd\\n").toByteArray())
                    shellOut?.flush()
                    
                    kotlinx.coroutines.delay(100) // Give it a moment to produce output
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("$ ")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("Error: ${e.message}\\n$ ")
                    }
                }
            }"""

new_cd_logic = """            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    // Send command, and append a marker to know when it finishes so we can print the prompt
                    shellOut?.write(("$cmd ; echo __VERB_CMD_DONE__\\n").toByteArray())
                    shellOut?.flush()
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("Error: ${e.message}\\n$ ")
                    }
                }
            }"""
text = text.replace(old_cd_logic, new_cd_logic)

old_reader = """                    while (true) {
                        val line = reader.readLine() ?: break
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput(line + "\\n")
                        }
                    }"""

new_reader = """                    while (true) {
                        val line = reader.readLine() ?: break
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (line.trim() == "__VERB_CMD_DONE__") {
                                appendOutput("$ ")
                            } else {
                                appendOutput(line + "\\n")
                            }
                        }
                    }"""
text = text.replace(old_reader, new_reader)

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
