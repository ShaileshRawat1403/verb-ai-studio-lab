import re

with open('app/src/main/java/com/example/verb/terminal/TerminalRuntime.kt', 'r') as f:
    text = f.read()

# Replace the delegate assignment
match = re.search(r'private val delegate: TerminalRuntimeAdapter = if \(useFakeForTesting\) com\.example\.verb\.terminal\.FakeTerminalRuntimeAdapter\(workingDir\) else com\.example\.verb\.terminal\.TermuxTerminalRuntimeAdapter\(workingDir\)', text)
if match:
    text = text[:match.start()] + 'private val delegate: TerminalRuntimeAdapter = com.example.verb.terminal.TermuxTerminalRuntimeAdapter(workingDir)' + text[match.end():]

# Also remove useFakeForTesting from constructor
text = text.replace(",\n    useFakeForTesting: Boolean = false", "")

with open('app/src/main/java/com/example/verb/terminal/TerminalRuntime.kt', 'w') as f:
    f.write(text)
