import re

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

# Fix the scrollable box
text = text.replace("Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).pointerInput(Unit) {", "Box(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).pointerInput(Unit) {")

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
print("Patched ScrollState in TerminalScreen")
