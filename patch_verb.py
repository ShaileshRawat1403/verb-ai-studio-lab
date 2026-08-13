import re

with open('app/src/main/java/com/example/verb/viewmodel/VerbViewModel.kt', 'r') as f:
    text = f.read()

# Replace terminalRuntime usages
text = text.replace("terminalRuntime.terminalOutput.value", "terminalViewModel.terminalOutput.value")
text = text.replace("terminalRuntime.destroy()", "")
text = text.replace("val terminalRuntime get() = terminalViewModel.terminalRuntime", "")

with open('app/src/main/java/com/example/verb/viewmodel/VerbViewModel.kt', 'w') as f:
    f.write(text)
print("Patched VerbViewModel")
