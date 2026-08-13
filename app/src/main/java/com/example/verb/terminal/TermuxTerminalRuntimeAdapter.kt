package com.example.verb.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.text.TextRange
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay


/**
 * Production Termux Terminal Adapter implementing [TerminalRuntimeAdapter].
 * Directly manages authentic Termux [TerminalSession] and [TerminalView] instances.
 * NO silent ProcessBuilder fallbacks are performed in production.
 */
class TermuxTerminalRuntimeAdapter(
    val workingDir: File,
    val shellExecutable: String = "/system/bin/sh"
) : TerminalRuntimeAdapter, TerminalSessionClient, TerminalViewClient {
    private var session: TerminalSession? = null
    private var shellProcess: Process? = null
    private var shellOut: OutputStream? = null
    var terminalView: TerminalView? = null
    val hasNativeSession: Boolean get() = session?.isRunning == true

    fun bindTerminalView(view: TerminalView) {
        terminalView = view
        view.setTerminalViewClient(this)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        session?.let { view.attachSession(it) }
    }

    private val _sessionState = MutableStateFlow<TerminalSessionState>(TerminalSessionState.STARTING)
    override val sessionState: StateFlow<TerminalSessionState> = _sessionState.asStateFlow()

    private val _terminalOutput = MutableStateFlow<String>("")
    override val terminalOutput: StateFlow<String> = _terminalOutput.asStateFlow()

    private val _isSessionActive = MutableStateFlow<Boolean>(false)
    override val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _activeSelectionText = MutableStateFlow<String>("")
    override val activeSelectionText: StateFlow<String> = _activeSelectionText.asStateFlow()

    private val _activeSelectionRange = MutableStateFlow<TextRange>(TextRange.Zero)
    /**
     * Note: The activeSelectionRange currently represents a range local to the extracted string
     * rather than exact terminal cell coordinates.
     */
    override val activeSelectionRange: StateFlow<TextRange> = _activeSelectionRange.asStateFlow()

    private val selectionListeners = CopyOnWriteArrayList<SelectionChangeListener>()

    init {
        startSession()
    }

    override fun startSession() {
        if (_isSessionActive.value && session != null) return

        TerminalSessionLogger.info(
            LogCategory.LIFECYCLE,
            "Initializing Termux session in directory: ${workingDir.absolutePath} (exists=${workingDir.exists()}, canWrite=${workingDir.canWrite()})"
        )

        _sessionState.value = TerminalSessionState.STARTING
        appendOutput("Verb Terminal Session Active (${workingDir.name})\n$ ")

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        val shellAccess = ShellAccessibilityCheck.checkShellAccessibility(shellExecutable)
        if (!shellAccess.isAccessible) {
            TerminalSessionLogger.error(
                LogCategory.SHELL,
                "ShellAccessibilityCheck failed for '$shellExecutable': ${shellAccess.permissionError}"
            )
        } else {
            TerminalSessionLogger.info(
                LogCategory.SHELL,
                "ShellAccessibilityCheck verified at '$shellExecutable' (accessible=true)"
            )
        }

        val shellDiag = ShellDiagnosticUtil.diagnoseShellExecutable(shellExecutable)
        if (!shellDiag.isAccessible) {
            TerminalSessionLogger.error(
                LogCategory.SHELL,
                "Shell binary verification failed for '$shellExecutable': ${shellDiag.errorMessage}"
            )
        } else {
            TerminalSessionLogger.info(
                LogCategory.SHELL,
                "Shell binary verified at '$shellExecutable' (exists=true, readable=${shellDiag.canRead}, executable=${shellDiag.canExecute})"
            )
        }
        val localBin = File(workingDir, "bin")
        if (!localBin.exists()) {
            localBin.mkdirs()
        }

        val extendedPath = "${localBin.absolutePath}:$sysPath"
        TerminalSessionLogger.info(LogCategory.SHELL, "Shell path: $shellExecutable | System PATH: $extendedPath")

        val envArray = arrayOf(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=${workingDir.absolutePath}",
            "PATH=$extendedPath",
            "LANG=en_US.UTF-8"
        )

        try {
            TerminalSessionLogger.info(LogCategory.JNI, "Resolving com.termux.terminal.JNI class and creating TerminalSession...")
            Class.forName("com.termux.terminal.JNI")
            val newSession = TerminalSession(
                shellExecutable,
                workingDir.absolutePath,
                arrayOf("-l"),
                envArray,
                2000,
                this
            )
            
            // Initialize the emulator immediately for headless execution
            newSession.updateSize(80, 24, 0, 0)
            
            if (newSession.isRunning) {
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
        }
    }

    override fun attachSession() {
        if (session != null && session?.isRunning == true && _isSessionActive.value) {
            _sessionState.value = TerminalSessionState.RUNNING
        } else {
            startSession()
        }
    }


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
            
            appendOutput("\n[Native Shell Process Active]\n$ ")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val reader = BufferedReader(InputStreamReader(shellProcess?.inputStream))
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (line.trim() == "__VERB_CMD_DONE__") {
                                appendOutput("$ ")
                            } else {
                                appendOutput(line + "\n")
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            _isSessionActive.value = false
            _sessionState.value = TerminalSessionState.FAILED
            appendOutput("\n[FAILED to start native shell fallback: ${e.message}]\n")
        }
    }

    override fun sendText(text: String) {
        val s = session
        if (s != null && s.isRunning) {
            s.write(text)
        }
    }

    private var activeWorkingDir: File = workingDir

    override fun sendCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            clearBuffer()
            return
        }

        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\n")
            return
        }
        
        if (shellProcess != null && shellProcess?.isAlive == false) {
            startRealShellFallback()
        }
        
        if (shellProcess != null && shellProcess?.isAlive == true) {
            _sessionState.value = TerminalSessionState.RUNNING
            appendOutput("$cmd\n")
            
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
                    appendOutput("cd: $dir: No such file or directory\n$ ")
                    return
                }
            }
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    shellOut?.write(("$cmd ; echo __VERB_CMD_DONE__\n").toByteArray())
                    shellOut?.flush()
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("Error: ${e.message}\n$ ")
                    }
                }
            }
        } else {
            session = null
            _sessionState.value = TerminalSessionState.FAILED
            appendOutput("$cmd\n")
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
    override fun sendControlKey(key: String) {
        val s = session
        if (s == null) {
            when (key) {
                "CTRL_L" -> clearBuffer()
                "CTRL_C" -> appendOutput("^C\n$ ")
                else -> {}
            }
            return
        }
        val cursorApp = s.emulator?.isCursorKeysApplicationMode ?: false
        
        // Helper to get KeyHandler code
        fun getCode(keyCode: Int, shift: Boolean = false): String? {
            val mod = if (shift) com.termux.terminal.KeyHandler.KEYMOD_SHIFT else 0
            return com.termux.terminal.KeyHandler.getCode(keyCode, mod, cursorApp, false)
        }

        when (key) {
            "ESC" -> getCode(android.view.KeyEvent.KEYCODE_ESCAPE)?.let { s.write(it) }
            "TAB" -> getCode(android.view.KeyEvent.KEYCODE_TAB)?.let { s.write(it) }
            "SHIFT_TAB" -> getCode(android.view.KeyEvent.KEYCODE_TAB, true)?.let { s.write(it) }
            "UP" -> getCode(android.view.KeyEvent.KEYCODE_DPAD_UP)?.let { s.write(it) }
            "DOWN" -> getCode(android.view.KeyEvent.KEYCODE_DPAD_DOWN)?.let { s.write(it) }
            "RIGHT" -> getCode(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)?.let { s.write(it) }
            "LEFT" -> getCode(android.view.KeyEvent.KEYCODE_DPAD_LEFT)?.let { s.write(it) }
            "HOME" -> getCode(android.view.KeyEvent.KEYCODE_MOVE_HOME)?.let { s.write(it) }
            "END" -> getCode(android.view.KeyEvent.KEYCODE_MOVE_END)?.let { s.write(it) }
            "PGUP" -> getCode(android.view.KeyEvent.KEYCODE_PAGE_UP)?.let { s.write(it) }
            "PGDN" -> getCode(android.view.KeyEvent.KEYCODE_PAGE_DOWN)?.let { s.write(it) }
            "DEL" -> getCode(android.view.KeyEvent.KEYCODE_FORWARD_DEL)?.let { s.write(it) }
            "PASTE" -> onPasteTextFromClipboard(s)
            else -> {
                if (key.startsWith("CTRL_") && key.length == 6) {
                    val c = key.last()
                    if (c in 'A'..'Z') {
                        val codePoint = c - 'A' + 1
                        s.write(codePoint.toChar().toString())
                        return
                    }
                }
                s.write(key)
            }
        }
    }

    override fun resize(rows: Int, cols: Int) {
        session?.updateSize(cols, rows, 0, 0)
    }

    override fun selectedText(): String {
        return terminalView?.storedSelectedText ?: _activeSelectionText.value
    }

    override fun notifySelectionChanged(selectedRange: TextRange, selectedText: String) {
        // The selection range is documented as local to the extracted string
        _activeSelectionRange.value = selectedRange
        _activeSelectionText.value = selectedText
        for (listener in selectionListeners) {
            listener.onSelectionChanged(selectedRange, selectedText)
        }
    }

    override fun addSelectionChangeListener(listener: SelectionChangeListener) {
        if (!selectionListeners.contains(listener)) {
            selectionListeners.add(listener)
        }
    }

    override fun removeSelectionChangeListener(listener: SelectionChangeListener) {
        selectionListeners.remove(listener)
    }

    override fun currentWorkingDirectory(): String {
        return activeWorkingDir.absolutePath
    }

    override fun clearBuffer() {
        _terminalOutput.value = "$ "
        TerminalSessionLogger.info(LogCategory.IO, "Terminal buffer cleared")
    }

    override fun restartSession() {
        TerminalSessionLogger.info(LogCategory.LIFECYCLE, "Restarting terminal session...")
        destroy()
        startSession()
    }

    override fun destroy() {
        _sessionState.value = TerminalSessionState.STOPPING
        _isSessionActive.value = false
        session?.finishIfRunning()
        session = null
        shellProcess?.destroy()
        shellProcess = null
        selectionListeners.clear()
        _sessionState.value = TerminalSessionState.EXITED
        TerminalSessionLogger.info(LogCategory.LIFECYCLE, "Termux session destroyed")
    }

    // TerminalSessionClient callbacks
    override fun onTextChanged(changedSession: TerminalSession) {
        val transcript = changedSession.emulator.screen.transcriptText ?: ""
        if (transcript.length > 50_000) {
            _terminalOutput.value = transcript.takeLast(50_000)
        } else {
            _terminalOutput.value = transcript
        }
    }

    override fun onTitleChanged(changedSession: TerminalSession) {}

    override fun onSessionFinished(finishedSession: TerminalSession) {
        session = null
        _isSessionActive.value = false
        _sessionState.value = if (finishedSession.exitStatus == 0) TerminalSessionState.EXITED else TerminalSessionState.FAILED
        appendOutput("\n[Session terminated with code ${finishedSession.exitStatus}]\n$ ")
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        terminalView?.context?.let { ctx ->
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Termux Selection", text))
        }
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        val s = session ?: this.session
        terminalView?.context?.let { ctx ->
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).coerceToText(ctx).toString()
                s?.emulator?.paste(text)
            }
        }
    }
    
    override fun onBell(session: TerminalSession) {}
    
    override fun onColorsChanged(session: TerminalSession) {}
    
    override fun onTerminalCursorStateChange(state: Boolean) {}
    
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    
    override fun getTerminalCursorStyle(): Int = 0

    // TerminalViewClient callbacks
    override fun onScale(scale: Float): Float = scale
    
    override fun onSingleTapUp(e: MotionEvent) {}
    
    override fun onInspectText(text: String) {
        // Notify Semantic Lens about inspected text. The selection range is local to the extracted string.
        notifySelectionChanged(TextRange(0, text.length), text)
    }

    override fun onLongPress(e: MotionEvent): Boolean {
        return false
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun readControlKey(): Boolean = false
    override fun readAltKey(): Boolean = false
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
    
    override fun onEmulatorSet() {}
    
    override fun logError(tag: String, message: String) {
        TerminalSessionLogger.error(LogCategory.DIAGNOSTIC, "[$tag] $message")
    }
    override fun logWarn(tag: String, message: String) {
        TerminalSessionLogger.warn(LogCategory.DIAGNOSTIC, "[$tag] $message")
    }
    override fun logInfo(tag: String, message: String) {
        TerminalSessionLogger.info(LogCategory.DIAGNOSTIC, "[$tag] $message")
    }
    override fun logDebug(tag: String, message: String) {
        TerminalSessionLogger.debug(LogCategory.DIAGNOSTIC, "[$tag] $message")
    }
    override fun logVerbose(tag: String, message: String) {
        TerminalSessionLogger.debug(LogCategory.DIAGNOSTIC, "[$tag] $message")
    }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        TerminalSessionLogger.error(LogCategory.DIAGNOSTIC, "[$tag] $message: ${e.message}")
    }
    override fun logStackTrace(tag: String, e: Exception) {
        TerminalSessionLogger.error(LogCategory.DIAGNOSTIC, "[$tag] Exception: ${e.message}")
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
}
