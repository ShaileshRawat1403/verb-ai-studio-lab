package com.example.verb.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.verb.db.VerbRepository
import com.example.verb.terminal.LogCategory
import com.example.verb.terminal.ShellAccessibilityCheck
import com.example.verb.terminal.ShellAccessibilityResult
import com.example.verb.terminal.ShellDiagnosticsReport
import com.example.verb.terminal.TerminalDiagnostics
import com.example.verb.terminal.TerminalRuntime
import com.example.verb.terminal.TerminalRuntimeAdapter
import com.example.verb.terminal.TerminalSessionLogger
import com.example.verb.terminal.TerminalSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ShellConnectionStatus {
    READY,          // Green indicator - Shell session ready / universal engine operational
    CONNECTING,     // Yellow indicator - Initializing
    ERROR,          // Red indicator - Shell error or fallback
    DISCONNECTED    // Gray indicator - Stopped
}

enum class TerminalTheme {
    MIDNIGHT,       // High contrast dark terminal canvas
    LIGHT           // High contrast light terminal canvas
}

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    
    private val _connectionStatus = MutableStateFlow(ShellConnectionStatus.READY)
    val connectionStatus: StateFlow<ShellConnectionStatus> = _connectionStatus.asStateFlow()

    private val _terminalTheme = MutableStateFlow(TerminalTheme.MIDNIGHT)
    val terminalTheme: StateFlow<TerminalTheme> = _terminalTheme.asStateFlow()

    private val _commandHistory = MutableStateFlow<List<String>>(emptyList())
    val commandHistory: StateFlow<List<String>> = _commandHistory.asStateFlow()
    private var historyIndex = -1

    private val _diagnosticsReport = MutableStateFlow<ShellDiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<ShellDiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _shellAccessibilityResult = MutableStateFlow<ShellAccessibilityResult?>(null)
    val shellAccessibilityResult: StateFlow<ShellAccessibilityResult?> = _shellAccessibilityResult.asStateFlow()

    private val _rawDiagnosticOutput = MutableStateFlow<String?>(null)
    val rawDiagnosticOutput: StateFlow<String?> = _rawDiagnosticOutput.asStateFlow()

    private val _isExecutingDiagnostics = MutableStateFlow(false)
    val isExecutingDiagnostics: StateFlow<Boolean> = _isExecutingDiagnostics.asStateFlow()

    private val commonCommands = listOf(
        "git status", "git log", "git add .", "git commit -m \"\"", "git push", "git pull", "git checkout", "git init",
        "node index.js", "node -v", "node -e \"console.log('hi')\"",
        "npm install", "npm start", "npm run dev", "npm test",
        "python main.py", "python --version", "python3 script.py",
        "bun run index.ts", "bun -v",
        "ls -la", "ls", "clear", "exit", "help", "pwd", "mkdir", "cd", "cat", "touch", "echo", "ps", "top"
    )

    private val _terminalOutput = MutableStateFlow("Verb Native Shell Initialized\n$ ")
    val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private var shellProcess: Process? = null
    private var shellOut: java.io.OutputStream? = null
    private var activeWorkingDir: java.io.File

    init {
        activeWorkingDir = application.applicationContext.filesDir
        startShellProcess()

        observeTerminalSessionState()
        runDiagnostics()
    }


    private fun bootstrapBinaries() {
        val app = getApplication<Application>()
        val binDir = java.io.File(app.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        
        val certFile = java.io.File(binDir, "cacert.pem")
        if (!certFile.exists()) {
            try {
                val inputStream = app.assets.open("cacert.pem")
                val outputStream = java.io.FileOutputStream(certFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val tools = listOf("curl", "jq", "busybox")
        val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" }
        
        tools.forEach { tool ->
            val toolFile = java.io.File(binDir, tool)
            if (!toolFile.exists()) {
                try {
                    if (arch != null) {
                        val inputStream = app.assets.open("$arch/$tool")
                        val outputStream = java.io.FileOutputStream(toolFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        toolFile.setExecutable(true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // Let busybox install its symlinks if they don't exist yet
        val busyboxSymlinkInstalled = java.io.File(binDir, "vi").exists()
        if (!busyboxSymlinkInstalled && java.io.File(binDir, "busybox").exists()) {
            try {
                val pb = ProcessBuilder(java.io.File(binDir, "busybox").absolutePath, "--install", "-s", ".")
                pb.directory(binDir)
                pb.start().waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startShellProcess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bootstrapBinaries()
                val pb = ProcessBuilder("/system/bin/sh")
                    .directory(activeWorkingDir)
                    
                val env = pb.environment()
                val binPath = java.io.File(getApplication<Application>().filesDir, "bin").absolutePath
                val certPath = java.io.File(getApplication<Application>().filesDir, "bin/cacert.pem").absolutePath
                env["PATH"] = "$binPath:" + (System.getenv("PATH") ?: "/system/bin:/system/xbin")
                env["CURL_CA_BUNDLE"] = certPath
                
                shellProcess = pb.start()
                shellOut = shellProcess?.outputStream

                // Read stdout
                launch {
                    val reader = shellProcess?.inputStream?.bufferedReader()
                    reader?.forEachLine { line ->
                        if (line.trim() == "__VERB_CMD_DONE__") {
                            appendOutput("$ ")
                        } else {
                            appendOutput(line + "\n")
                        }
                    }
                }

                // Read stderr and highlight in red
                launch {
                    val reader = shellProcess?.errorStream?.bufferedReader()
                    reader?.forEachLine { line ->
                        appendOutput("\u001B[31m$line\u001B[0m\n")
                    }
                }
            } catch (e: Exception) {
                appendOutput("\u001B[31mFailed to start shell: ${e.message}\u001B[0m\n")
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


    fun toggleTheme() {
        _terminalTheme.value = if (_terminalTheme.value == TerminalTheme.MIDNIGHT) TerminalTheme.LIGHT else TerminalTheme.MIDNIGHT
    }

    fun setTheme(theme: TerminalTheme) {
        _terminalTheme.value = theme
    }

    fun navigateHistoryUp(): String? {
        val history = _commandHistory.value
        if (history.isEmpty()) return null
        if (historyIndex == -1) {
            historyIndex = history.size - 1
        } else if (historyIndex > 0) {
            historyIndex--
        }
        return history.getOrNull(historyIndex)
    }

    fun navigateHistoryDown(): String? {
        val history = _commandHistory.value
        if (history.isEmpty() || historyIndex == -1) return ""
        if (historyIndex < history.size - 1) {
            historyIndex++
            return history[historyIndex]
        } else {
            historyIndex = -1
            return ""
        }
    }

    fun getAutocompleteSuggestions(input: String): List<String> {
        val trimmed = input.trimStart().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        return commonCommands.filter { cmd ->
            cmd.lowercase().startsWith(trimmed) && cmd.lowercase() != trimmed
        }.take(6)
    }

    private fun observeTerminalSessionState() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _connectionStatus.value = ShellConnectionStatus.READY
        }
    }

    fun runDiagnostics() {
        _isExecutingDiagnostics.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val accessCheck = ShellAccessibilityCheck.checkShellAccessibility("/system/bin/sh")
                _shellAccessibilityResult.value = accessCheck

                if (accessCheck.permissionError != null) {
                    _connectionStatus.value = ShellConnectionStatus.ERROR
                    TerminalSessionLogger.error(
                        LogCategory.SHELL,
                        "Shell Accessibility Check Failed: ${accessCheck.permissionError}"
                    )
                }

                val report = TerminalDiagnostics.executeShellVerification(app.filesDir)
                _diagnosticsReport.value = report

                if (report.isAccessible) {
                    TerminalSessionLogger.info(
                        LogCategory.SHELL,
                        "TerminalViewModel Diagnostics: Shell verified accessible (${report.binaryCount} binaries found in ${report.executionTimeMs}ms)"
                    )
                } else {
                    TerminalSessionLogger.error(
                        LogCategory.SHELL,
                        "TerminalViewModel Diagnostics: Shell reachability check failed: ${report.errorDetails}"
                    )
                }
            } catch (e: Exception) {
                TerminalSessionLogger.error(
                    LogCategory.SHELL,
                    "TerminalViewModel Diagnostics exception: ${e.message}"
                )
            } finally {
                _isExecutingDiagnostics.value = false
            }
        }
    }

    fun runEnvironmentDiagnostics() {
        _isExecutingDiagnostics.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val accessCheck = ShellAccessibilityCheck.checkShellAccessibility("/system/bin/sh")
                _shellAccessibilityResult.value = accessCheck

                val process = ProcessBuilder("/system/bin/sh", "-c", "echo '=== PATH REACHABILITY ==='; echo \"\$PATH\"; echo ''; echo '=== ENVIRONMENT VARIABLES ==='; env")
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                val rawResult = buildString {
                    append("=== SHELL ACCESSIBILITY CHECK ===\n")
                    append("Executable: ${accessCheck.executablePath}\n")
                    append("Accessible: ${accessCheck.isAccessible}\n")
                    if (accessCheck.permissionError != null) {
                        append("Permission Error: ${accessCheck.permissionError}\n")
                    }
                    append("\n=== RAW SHELL ENVIRONMENT & PATH ===\n")
                    append(if (output.isNotBlank()) output else "[No output returned from env command]")
                }
                _rawDiagnosticOutput.value = rawResult
                TerminalSessionLogger.info(LogCategory.SHELL, "Executed environment diagnostics command (env / \$PATH)")
            } catch (e: Exception) {
                val errOutput = "Diagnostic Command Error: ${e.message}"
                _rawDiagnosticOutput.value = errOutput
                TerminalSessionLogger.error(LogCategory.SHELL, errOutput)
            } finally {
                _isExecutingDiagnostics.value = false
            }
        }
    }

    fun clearDiagnosticOutput() {
        _rawDiagnosticOutput.value = null
    }

    fun executeCommand(cmd: String) {
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
        
        appendOutput("$cmd\n")

        if (trimmed.startsWith("cd ")) {
            val dir = trimmed.substringAfter("cd ").trim()
            val targetDir = if (dir.startsWith("/")) java.io.File(dir) else java.io.File(activeWorkingDir, dir)
            if (targetDir.exists() && targetDir.isDirectory) {
                activeWorkingDir = targetDir
                shellProcess?.destroy()
                startShellProcess()
                return
            } else {
                appendOutput("cd: $dir: No such file or directory\n$ ")
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (shellProcess?.isAlive == false) {
                    startShellProcess()
                }
                shellOut?.write(("$cmd ; echo __VERB_CMD_DONE__\n").toByteArray())
                shellOut?.flush()
                
                val repository = com.example.verb.db.VerbRepository.getInstance(getApplication())
                repository.recordTerminalOutput(
                    command = cmd,
                    output = _terminalOutput.value,
                    workingDir = activeWorkingDir.absolutePath
                )
            } catch (e: Exception) {
                appendOutput("\u001B[31mError: ${e.message}\u001B[0m\n$ ")
            }
        }
    }

    fun sendControlKey(key: String) {
    }

    fun clearTerminal() {
        _terminalOutput.value = "$ "
    }
}