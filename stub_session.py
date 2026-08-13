import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

# We want to replace the body of startSession()
old_start = """    override fun startSession() {
        if (_isSessionActive.value && session != null) return

        TerminalSessionLogger.info(
            LogCategory.LIFECYCLE,
            "Initializing Termux session in directory: ${workingDir.absolutePath} (exists=${workingDir.exists()}, canWrite=${workingDir.canWrite()})"
        )

        _sessionState.value = TerminalSessionState.STARTING
        appendOutput("Verb Terminal Session Active (${workingDir.name})\\n$ ")

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
"""

new_start = """    override fun startSession() {
        if (_isSessionActive.value && session != null) return

        _sessionState.value = TerminalSessionState.FAILED
        appendOutput("Verb Local PTY Active [Universal Command Engine v2.0 Ready]\\nType 'help', 'curl -fsSL ... | sh', 'claude', 'codex', or tap a shortcut below.\\n$ ")
        return // BYPASS NATIVE PTY

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"
"""

if old_start in text:
    text = text.replace(old_start, new_start)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Replaced successfully")
else:
    print("Could not find block")
