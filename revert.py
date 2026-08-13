import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

old_start = """    override fun startSession() {
        if (_isSessionActive.value && session != null) return

        _sessionState.value = TerminalSessionState.FAILED
        appendOutput("Verb Local PTY Active [Universal Command Engine v2.0 Ready]\\nType 'help', 'curl -fsSL ... | sh', 'claude', 'codex', or tap a shortcut below.\\n$ ")
        return // BYPASS NATIVE PTY

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
"""
new_start = """    override fun startSession() {
        if (_isSessionActive.value && session != null) return

        TerminalSessionLogger.info(
            LogCategory.LIFECYCLE,
            "Initializing Termux session in directory: ${workingDir.absolutePath} (exists=${workingDir.exists()}, canWrite=${workingDir.canWrite()})"
        )

        _sessionState.value = TerminalSessionState.STARTING
        appendOutput("Verb Terminal Session Active (${workingDir.name})\\n$ ")

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
"""

text = text.replace(old_start, new_start)

old_cmd = """        val activeSession = session
        if (false) {
            _sessionState.value = TerminalSessionState.RUNNING
        } else {"""
new_cmd = """        val activeSession = session
        if (activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\\n")
        } else {"""

text = text.replace(old_cmd, new_cmd)

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
print("Reverted successfully")
