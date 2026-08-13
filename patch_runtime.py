with open('app/src/main/java/com/example/verb/terminal/TerminalRuntime.kt', 'r') as f:
    text = f.read()

text = text.replace("class TerminalRuntime(\n    private val workingDir: File,\n    useFakeForTesting: Boolean = false\n) : TerminalRuntimeAdapter {\n    private val delegate: TerminalRuntimeAdapter = if (useFakeForTesting) com.example.verb.terminal.FakeTerminalRuntimeAdapter(workingDir) else com.example.verb.terminal.TermuxTerminalRuntimeAdapter(workingDir)", "class TerminalRuntime(\n    private val workingDir: File\n) : TerminalRuntimeAdapter {\n    private val delegate: TerminalRuntimeAdapter = com.example.verb.terminal.TermuxTerminalRuntimeAdapter(workingDir)")

with open('app/src/main/java/com/example/verb/terminal/TerminalRuntime.kt', 'w') as f:
    f.write(text)
