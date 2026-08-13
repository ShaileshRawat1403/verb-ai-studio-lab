import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("val hasNativePty = termuxAdapter != null && sessionState == com.example.verb.terminal.TerminalSessionState.RUNNING", "val hasNativePty = false // FORCED TO FALSE FOR HYBRID APPROACH")

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
print("Forced hasNativePty to false")
