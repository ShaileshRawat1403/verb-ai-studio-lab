import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

old_send_command = """        val activeSession = session
        if (false && activeSession != null && activeSession.isRunning) {
            _sessionState.value = TerminalSessionState.RUNNING
            sendText("$cmd\\n")
        } else {"""

new_send_command = """        val activeSession = session
        if (false) {
            _sessionState.value = TerminalSessionState.RUNNING
        } else {"""

if old_send_command in text:
    text = text.replace(old_send_command, new_send_command)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Replaced successfully")
else:
    print("Could not find block")
