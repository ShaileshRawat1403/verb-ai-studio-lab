import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("termuxAdapter.terminalView", "termuxAdapter?.terminalView")
text = text.replace("termuxAdapter.bindTerminalView(it)", "termuxAdapter?.bindTerminalView(it)")

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
print("Fixed nullability issue")
