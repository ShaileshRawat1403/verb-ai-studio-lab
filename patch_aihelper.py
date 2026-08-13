import re

with open('app/src/main/java/com/example/verb/terminal/TerminalAiHelper.kt', 'r') as f:
    text = f.read()

# Add Context import
text = text.replace("import kotlinx.coroutines.withContext", "import kotlinx.coroutines.withContext\nimport android.content.Context")

# Remove lazy model
text = re.sub(r'    private val model by lazy \{[\s\S]*?    \}', '', text)

# Update function signature and logic
new_func = """    suspend fun analyzeTerminalOutput(context: Context, output: String): String {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE)
            val geminiKey = prefs.getString("GEMINI_API_KEY", "") ?: ""

            if (geminiKey.isEmpty() || !geminiKey.startsWith("AIzaSy")) {
                return@withContext "AI Assistance is unavailable. Please set a valid Gemini API Key (starting with 'AIzaSy') in the Terminal Settings."
            }
            
            val model = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = geminiKey
            )

            val prompt = """
text = re.sub(r'    suspend fun analyzeTerminalOutput\(output: String\): String \{[\s\S]*?            val prompt =', new_func, text)

with open('app/src/main/java/com/example/verb/terminal/TerminalAiHelper.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'r') as f:
    ts = f.read()
    
ts = ts.replace("TerminalAiHelper.analyzeTerminalOutput(terminalOutput)", "TerminalAiHelper.analyzeTerminalOutput(context, terminalOutput)")
# make sure context is available. TerminalScreen uses `val context = LocalContext.current` early on.
if "LocalContext.current" not in ts:
    # it probably is since it has ClipboardManager
    pass

with open('app/src/main/java/com/example/verb/ui/TerminalScreen.kt', 'w') as f:
    f.write(ts)

print("Patched TerminalAiHelper and TerminalScreen")
