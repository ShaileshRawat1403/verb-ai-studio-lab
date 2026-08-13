with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

text = text.replace('import kotlinx.coroutines.CoroutineScope', 'import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.delay')

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
