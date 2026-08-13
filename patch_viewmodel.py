import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

match_runtime = re.search(r'val terminalRuntime: TerminalRuntimeAdapter = TerminalRuntime\(.*?\)\n', text)
if match_runtime:
    text = text.replace(match_runtime.group(0), "")

add_fields = """    private val _terminalOutput = MutableStateFlow("Verb Native Shell Initialized\\n$ ")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private var shellProcess: Process? = null
    private var shellOut: java.io.OutputStream? = null
    private var activeWorkingDir: java.io.File

    init {
        activeWorkingDir = application.applicationContext.filesDir
        startShellProcess()
"""
text = text.replace("    init {", add_fields, 1)

methods_to_add = """
    private fun startShellProcess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("/system/bin/sh")
                    .directory(activeWorkingDir)
                
                val env = pb.environment()
                env["PATH"] = System.getenv("PATH") ?: "/system/bin:/system/xbin"
                
                shellProcess = pb.start()
                shellOut = shellProcess?.outputStream

                // Read stdout
                launch {
                    val reader = shellProcess?.inputStream?.bufferedReader()
                    reader?.forEachLine { line ->
                        if (line.trim() == "__VERB_CMD_DONE__") {
                            appendOutput("$ ")
                        } else {
                            appendOutput(line + "\\n")
                        }
                    }
                }

                // Read stderr and highlight in red
                launch {
                    val reader = shellProcess?.errorStream?.bufferedReader()
                    reader?.forEachLine { line ->
                        appendOutput("\\u001B[31m$line\\u001B[0m\\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("\\u001B[31mFailed to start shell: ${e.message}\\u001B[0m\\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        val current = _terminalOutput.value
        val updated = if (current.length > 50_000) {
            current.takeLast(25_000) + text
        } else {
            current + text
        }
        _terminalOutput.value = updated
    }
"""
text = text.replace("        runDiagnostics()\n    }", "        runDiagnostics()\n    }\n" + methods_to_add)

new_execute = """    fun executeCommand(cmd: String) {
        if (cmd.isBlank()) return
        val currentList = _commandHistory.value.toMutableList()
        if (currentList.lastOrNull() != cmd) {
            currentList.add(cmd)
            _commandHistory.value = currentList
        }
        historyIndex = -1
        
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            _terminalOutput.value = "$ "
            return
        }
        
        appendOutput("$cmd\\n")

        if (trimmed.startsWith("cd ")) {
            val dir = trimmed.substringAfter("cd ").trim()
            val targetDir = if (dir.startsWith("/")) java.io.File(dir) else java.io.File(activeWorkingDir, dir)
            if (targetDir.exists() && targetDir.isDirectory) {
                activeWorkingDir = targetDir
                shellProcess?.destroy()
                startShellProcess()
                return
            } else {
                appendOutput("cd: $dir: No such file or directory\\n$ ")
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (shellProcess?.isAlive == false) {
                    startShellProcess()
                }
                shellOut?.write(("$cmd ; echo __VERB_CMD_DONE__\\n").toByteArray())
                shellOut?.flush()
                
                val repository = com.example.verb.db.VerbRepository.getInstance(getApplication())
                repository.recordTerminalOutput(
                    command = cmd,
                    output = _terminalOutput.value,
                    workingDir = activeWorkingDir.absolutePath
                )
            } catch (e: Exception) {
                appendOutput("\\u001B[31mError: ${e.message}\\u001B[0m\\n$ ")
            }
        }
    }

    fun sendControlKey(key: String) {
    }

    fun clearTerminal() {
        _terminalOutput.value = "$ "
    }
}"""
match = re.search(r'    fun executeCommand\(cmd: String\) \{.*', text, re.DOTALL)
text = text.replace(match.group(0), new_execute)

# Need to fix observeTerminalSessionState since terminalRuntime is gone
text = text.replace("terminalRuntime.sessionState.collect", "kotlinx.coroutines.delay(1000); _connectionStatus.value = ShellConnectionStatus.READY\n//")

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)
print("Patched TerminalViewModel")
