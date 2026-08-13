import re

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

# Add imports
imports = """
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
"""

if "import java.io.OutputStream" not in text:
    text = text.replace('import java.io.File\n', 'import java.io.File\n' + imports)

# Add class variables
vars = """    private var session: TerminalSession? = null
    private var shellProcess: Process? = null
    private var shellOut: OutputStream? = null"""
text = text.replace('    private var session: TerminalSession? = null', vars)

# Replace the fallback in startSession()
old_fallback = """            if (newSession.isRunning) {
                session = newSession
                _isSessionActive.value = true
                _sessionState.value = TerminalSessionState.RUNNING
                TerminalSessionLogger.info(LogCategory.LIFECYCLE, "Native PTY TerminalSession running successfully [PID=${newSession.pid}]")
                terminalView?.attachSession(newSession)
            } else {
                session = null
                _isSessionActive.value = false
                _sessionState.value = TerminalSessionState.FAILED
                val failReason = when {
                    !shellDiag.exists -> "Shell binary not found at $shellExecutable"
                    !shellDiag.canExecute -> "Missing binary execution permission for $shellExecutable"
                    else -> "libtermux.so or PTY allocation failed"
                }
                TerminalSessionLogger.error(LogCategory.JNI, "FAILED to start Termux PTY session: $failReason")
                appendOutput("\\n[FAILED to start Termux PTY session: $failReason]\\n[Universal Command Engine Active - Type 'help' or commands (git, node, bun, python, cd, ls) below]\\n")
            }
        } catch (t: Throwable) {
            session = null
            _isSessionActive.value = false
            _sessionState.value = TerminalSessionState.FAILED
            val failReason = when {
                !shellDiag.exists -> "Shell binary not found at $shellExecutable"
                !shellDiag.canExecute -> "Missing binary execution permission for $shellExecutable"
                else -> t.message ?: "libtermux.so or PTY allocation failed"
            }
            TerminalSessionLogger.error(LogCategory.JNI, "FAILED to start Termux PTY session: $failReason")
            appendOutput("\\n[FAILED to start Termux PTY session: $failReason]\\n[Universal Command Engine Active - Type 'help' or commands (git, node, bun, python, cd, ls) below]\\n")
        }"""

new_fallback = """            if (newSession.isRunning) {
                session = newSession
                _isSessionActive.value = true
                _sessionState.value = TerminalSessionState.RUNNING
                TerminalSessionLogger.info(LogCategory.LIFECYCLE, "Native PTY TerminalSession running successfully [PID=${newSession.pid}]")
                terminalView?.attachSession(newSession)
            } else {
                startRealShellFallback()
            }
        } catch (t: Throwable) {
            startRealShellFallback()
        }"""
text = text.replace(old_fallback, new_fallback)

# Add startRealShellFallback()
real_shell = """
    private fun startRealShellFallback() {
        try {
            val pb = ProcessBuilder(shellExecutable)
                .directory(activeWorkingDir)
                .redirectErrorStream(true)
            
            val env = pb.environment()
            env["PATH"] = System.getenv("PATH") ?: "/system/bin:/system/xbin"
            
            shellProcess = pb.start()
            shellOut = shellProcess?.outputStream
            
            _isSessionActive.value = true
            _sessionState.value = TerminalSessionState.RUNNING
            
            appendOutput("\\n[Native Shell Process Active]\\n$ ")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val reader = BufferedReader(InputStreamReader(shellProcess?.inputStream))
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput(line + "\\n")
                        }
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            _isSessionActive.value = false
            _sessionState.value = TerminalSessionState.FAILED
            appendOutput("\\n[FAILED to start native shell fallback: ${e.message}]\\n")
        }
    }
"""
text = text.replace('    override fun sendText(text: String) {', real_shell + '\n    override fun sendText(text: String) {')

# Modify sendCommand to use the shellProcess if activeSession is null
old_send_command = """        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\\n")
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

new_send_command = """        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\\n")
        } else if (shellProcess != null && shellProcess?.isAlive == true) {
            _sessionState.value = TerminalSessionState.RUNNING
            appendOutput("$cmd\\n")
            
            // Handle cd natively in our process
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
text = text.replace(old_send_command, new_send_command)

# Fix destroy()
old_destroy = """        session?.finishIfRunning()
        session = null
        selectionListeners.clear()"""
new_destroy = """        session?.finishIfRunning()
        session = null
        shellProcess?.destroy()
        shellProcess = null
        selectionListeners.clear()"""
text = text.replace(old_destroy, new_destroy)

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)

print("Patched for real process fallback")
