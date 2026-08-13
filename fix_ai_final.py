import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

# Fix the OptIn issue if it exists
text = text.replace('@OptIn(DelicateCoroutinesApi::class)\nclass', 'class')
text = text.replace('import kotlinx.coroutines.DelicateCoroutinesApi\n', '')
text = text.replace('kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {', 'kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {')

# Remove the bad imports if they exist
text = text.replace('import kotlinx.coroutines.launch\n', '')
text = text.replace('import kotlinx.coroutines.withContext\n', '')
text = text.replace('import kotlinx.coroutines.Dispatchers\n', '')
text = text.replace('import kotlinx.coroutines.GlobalScope\n', '')
text = text.replace('import com.example.BuildConfig\n', '')

# add imports
imports = """import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
"""
text = text.replace('import android.util.Log\n', 'import android.util.Log\n' + imports)

# replace BuildConfig
text = text.replace('BuildConfig.GEMINI_API_KEY', 'com.example.BuildConfig.GEMINI_API_KEY')

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
    print("Fixed final AI CLI code")
