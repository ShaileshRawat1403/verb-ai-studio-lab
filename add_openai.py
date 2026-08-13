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

text = text.replace('import android.util.Log\n', 'import android.util.Log\n' + imports_to_add)

old_ai_block = """        if (trimmed.startsWith("claude ") || trimmed.startsWith("gemini ") || trimmed.startsWith("ai ") || trimmed.startsWith("codex ")) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val apiKey = com.example.BuildConfig.GEMINI_API_KEY.trim('"', ' ')
                    if (apiKey.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("Error: GEMINI_API_KEY is missing. Please set it in the AI Studio Secrets panel.\\n$ ")
                        }
                        return@launch
                    }
                    val model = com.google.ai.client.generativeai.GenerativeModel(
                        modelName = "gemini-3.5-flash",
                        apiKey = apiKey
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("[Thinking...]\\n")
                    }
                    val response = model.generateContent(prompt)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput(response.text + "\\n$ ")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("AI Error: ${e.message}\\n$ ")
                    }
                }
            }
            return
        }"""

new_ai_block = """        if (trimmed.startsWith("openai ") || trimmed.startsWith("chatgpt ")) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val apiKey = com.example.BuildConfig.OPENAI_API_KEY.trim('"', ' ')
                    if (apiKey.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("Error: OPENAI_API_KEY is missing. Please set it in the AI Studio Secrets panel.\\n$ ")
                        }
                        return@launch
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("[Thinking (OpenAI)...]\\n")
                    }
                    
                    val client = OkHttpClient()
                    val json = JSONObject()
                    json.put("model", "gpt-4o") // Assuming gpt-4o as a solid default
                    
                    val message = JSONObject()
                    message.put("role", "user")
                    message.put("content", prompt)
                    
                    val messages = JSONArray()
                    messages.put(message)
                    json.put("messages", messages)
                    
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(body)
                        .build()
                        
                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    
                    if (response.isSuccessful) {
                        val responseJson = JSONObject(responseBody)
                        val text = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput(text + "\\n$ ")
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("OpenAI API Error: ${response.code} $responseBody\\n$ ")
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("OpenAI Error: ${e.message}\\n$ ")
                    }
                }
            }
            return
        }

        if (trimmed.startsWith("claude ") || trimmed.startsWith("gemini ") || trimmed.startsWith("ai ") || trimmed.startsWith("codex ")) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val apiKey = com.example.BuildConfig.GEMINI_API_KEY.trim('"', ' ')
                    if (apiKey.isEmpty()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("Error: GEMINI_API_KEY is missing. Please set it in the AI Studio Secrets panel.\\n$ ")
                        }
                        return@launch
                    }
                    val model = com.google.ai.client.generativeai.GenerativeModel(
                        modelName = "gemini-3.5-flash",
                        apiKey = apiKey
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("[Thinking...]\\n")
                    }
                    val response = model.generateContent(prompt)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput(response.text + "\\n$ ")
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("AI Error: ${e.message}\\n$ ")
                    }
                }
            }
            return
        }"""

if old_ai_block in text:
    text = text.replace(old_ai_block, new_ai_block)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Injected OpenAI CLI successfully")
else:
    print("Could not find block")
