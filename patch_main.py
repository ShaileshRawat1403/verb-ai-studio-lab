import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    text = f.read()

text = text.replace("val terminalOutput by viewModel.terminalRuntime.terminalOutput.collectAsStateWithLifecycle()", "val terminalOutput by viewModel.terminalViewModel.terminalOutput.collectAsStateWithLifecycle()")
text = text.replace("val isSessionActive by viewModel.terminalRuntime.isSessionActive.collectAsStateWithLifecycle()", "val isSessionActive = true")
text = text.replace("terminalRuntime = viewModel.terminalRuntime,", "terminalRuntime = null,")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(text)
print("Patched MainActivity")
