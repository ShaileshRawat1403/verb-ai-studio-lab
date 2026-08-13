import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

imports_to_add = """
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
"""

text = text.replace('import java.io.File\n', 'import java.io.File\n' + imports_to_add)

with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
    f.write(text)
    print("Fixed imports 2")
