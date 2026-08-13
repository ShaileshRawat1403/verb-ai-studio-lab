import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

start_marker = "        // Interactive Command Input Field\n        Row("
end_marker = "        // Contextual Touch Control Strip for P0.3"

start_idx = text.find(start_marker)
end_idx = text.find(end_marker)

if start_idx != -1 and end_idx != -1:
    new_text = text[:start_idx] + text[end_idx:]
    with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
        f.write(new_text)
    print("Replaced!")
else:
    print("Markers not found!")
