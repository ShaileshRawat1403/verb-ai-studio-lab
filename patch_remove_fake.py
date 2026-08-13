import re

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

# We want to remove everything from `if (trimmed == "curl` down to the end of the `matchedCommand != null` block.
# Actually, let's just find the start of `override fun sendCommand` and replace it entirely up to the shell check.

match = re.search(r'(override fun sendCommand\(cmd: String\) \{.*?)(\s*val activeSession = session)', text, re.DOTALL)
if match:
    old_method_start = match.group(1)
    
    new_method_start = """override fun sendCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed == "clear") {
            clearBuffer()
            return
        }
"""
    text = text.replace(old_method_start, new_method_start)
    
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Removed fake interceptors in TermuxTerminalRuntimeAdapter.")
else:
    print("Could not find sendCommand block to patch.")
