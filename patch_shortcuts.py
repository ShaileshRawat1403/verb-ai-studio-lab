import re

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    text = f.read()

old_shortcuts = """        val shortcuts = listOf(
            "curl -fsSL https://claude.ai/install.sh | sh",
            "curl -fsSL https://codex.openai.com/install.sh | sh",
            "claude --version",
            "codex --version",
            "git status",
            "clear",
            "ls -la",
            "pwd",
            "help",
            "top",
            "whoami",
            "date",
            "uname -a"
        )"""

new_shortcuts = """        val shortcuts = listOf(
            "clear",
            "ls -la",
            "pwd",
            "top -n 1",
            "whoami",
            "date",
            "uname -a",
            "df -h"
        )"""

text = text.replace(old_shortcuts, new_shortcuts)

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(text)
print("Removed fake shortcuts in TerminalScreen.")
