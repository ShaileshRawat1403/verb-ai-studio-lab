with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

text = text.replace("'${aiName.lowercase()} \"Hello\"'", "'${aiName.lowercase()} \\\"Hello\\\"'")

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
