import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

bad_observe = """    private fun observeTerminalSessionState() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000); _connectionStatus.value = ShellConnectionStatus.READY
// { state ->
                _connectionStatus.value = when (state) {
                    TerminalSessionState.RUNNING -> ShellConnectionStatus.READY
                    TerminalSessionState.STARTING -> ShellConnectionStatus.CONNECTING
                    TerminalSessionState.FAILED -> ShellConnectionStatus.ERROR
                    TerminalSessionState.EXITED -> ShellConnectionStatus.DISCONNECTED
                    TerminalSessionState.STOPPING -> ShellConnectionStatus.CONNECTING
                }
            }
        }
    }"""

good_observe = """    private fun observeTerminalSessionState() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            _connectionStatus.value = ShellConnectionStatus.READY
        }
    }"""

text = text.replace(bad_observe, good_observe)

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)
print("Patched TerminalViewModel syntax error")
