import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

# Remove .verticalScroll(scrollState) from the wrapper Box
text = text.replace("                .padding(14.dp)\n                .verticalScroll(scrollState)\n        ) {", "                .padding(14.dp)\n        ) {")

# Add .verticalScroll(scrollState) to the fallback inner Box
old_box = "                Box(modifier = Modifier.pointerInput(Unit) {\n                    detectTapGestures(onLongPress = { showContextMenu = true })\n                }) {"
new_box = "                Box(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).pointerInput(Unit) {\n                    detectTapGestures(onLongPress = { showContextMenu = true })\n                }) {"

text = text.replace(old_box, new_box)

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
