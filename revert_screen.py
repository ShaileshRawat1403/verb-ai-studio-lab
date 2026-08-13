import re
with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

old_text = """"Verb Local PTY Active [Universal Command Engine v2.0 Ready]\\nType 'help', 'curl -fsSL ... | sh', 'claude', 'codex', or tap a shortcut below.\\n$ " + commandInput"""
new_text = """"Verb Terminal Session Active\\n$ " + commandInput"""

if old_text in text:
    text = text.replace(old_text, new_text)
    with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
        f.write(text)
    print("Reverted screen successfully")
else:
    print("Could not find screen block")
