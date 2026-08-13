import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()
if 'OptIn' not in text:
    text = text.replace('import android.util.Log', 'import android.util.Log\nimport kotlinx.coroutines.DelicateCoroutinesApi')
    text = text.replace('class TermuxTerminalRuntimeAdapter', '@OptIn(DelicateCoroutinesApi::class)\nclass TermuxTerminalRuntimeAdapter')
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
        print("Fixed opt in")
