package com.example.verb.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    val terminalRuntime: TerminalRuntimeAdapter = TerminalRuntime(application.applicationContext.filesDir)

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

    init {
        observeTerminalSessionState()
        runDiagnostics()
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
            terminalRuntime.sessionState.collect { state ->
                _connectionStatus.value = when (state) {
                    TerminalSessionState.RUNNING -> ShellConnectionStatus.READY
                    TerminalSessionState.STARTING -> ShellConnectionStatus.CONNECTING
                    TerminalSessionState.FAILED -> ShellConnectionStatus.ERROR
                    TerminalSessionState.EXITED -> ShellConnectionStatus.DISCONNECTED
                    TerminalSessionState.STOPPING -> ShellConnectionStatus.CONNECTING
                }
            }
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
        terminalRuntime.sendCommand(cmd)
    }

    fun sendControlKey(key: String) {
        terminalRuntime.sendControlKey(key)
    }

    fun clearTerminal() {
        terminalRuntime.clearBuffer()
    }
}
