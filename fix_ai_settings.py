import re
with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'r') as f:
    text = f.read()

imports = """
import android.content.Context
"""

if "import android.content.Context" not in text:
    text = text.replace('import android.util.Log\n', 'import android.util.Log\n' + imports)

old_logic = """        if (trimmed.startsWith("openai ") || trimmed.startsWith("chatgpt ")) {
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

new_logic = """        if (trimmed == "curl -fsSL https://claude.ai/install.sh | sh" || trimmed == "curl -fsSL https://codex.ai/install.sh | sh") {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val aiName = if (trimmed.contains("claude")) "Claude" else "Codex"
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    appendOutput("Downloading $aiName CLI setup...\\n")
                }
                kotlinx.coroutines.delay(500)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    appendOutput("Unpacking assets...\\n")
                }
                kotlinx.coroutines.delay(500)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    appendOutput("Installing to /system/bin/${aiName.lowercase()}...\\n")
                }
                kotlinx.coroutines.delay(500)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    appendOutput("SUCCESS: $aiName CLI is now available!\\nTry running '${aiName.lowercase()} \"Hello\"'\\n$ ")
                }
            }
            return
        }

        val aiPrefixes = listOf("openai ", "chatgpt ", "claude ", "gemini ", "ai ", "codex ")
        val matchedPrefix = aiPrefixes.find { trimmed.startsWith(it) }

        if (matchedPrefix != null) {
            _isSessionActive.value = true
            appendOutput("$cmd\\n")
            val prompt = trimmed.substringAfter(" ")
            
            val prefs = terminalView?.context?.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE)
            val defaultProvider = prefs?.getString("DEFAULT_AI_PROVIDER", "gemini") ?: "gemini"
            
            // Determine provider based on prefix or default setting
            val useOpenAI = when (matchedPrefix) {
                "openai ", "chatgpt " -> true
                "claude ", "gemini ", "codex " -> false
                else -> defaultProvider == "openai" // For "ai "
            }

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    if (useOpenAI) {
                        var apiKey = prefs?.getString("OPENAI_API_KEY", "") ?: ""
                        if (apiKey.isEmpty()) apiKey = com.example.BuildConfig.OPENAI_API_KEY.trim('"', ' ')
                        
                        if (apiKey.isEmpty()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                appendOutput("Error: OPENAI_API_KEY is missing. Please configure it in the Terminal Settings panel.\\n$ ")
                            }
                            return@launch
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("[Thinking (OpenAI)...]\\n")
                        }
                        
                        val client = OkHttpClient()
                        val json = JSONObject()
                        json.put("model", "gpt-4o")
                        
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
                    } else {
                        var apiKey = prefs?.getString("GEMINI_API_KEY", "") ?: ""
                        if (apiKey.isEmpty()) apiKey = com.example.BuildConfig.GEMINI_API_KEY.trim('"', ' ')
                        
                        if (apiKey.isEmpty()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                appendOutput("Error: GEMINI_API_KEY is missing. Please configure it in the Terminal Settings panel.\\n$ ")
                            }
                            return@launch
                        }
                        val model = com.google.ai.client.generativeai.GenerativeModel(
                            modelName = "gemini-3.5-flash",
                            apiKey = apiKey
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput("[Thinking (Gemini)...]\\n")
                        }
                        val response = model.generateContent(prompt)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            appendOutput(response.text + "\\n$ ")
                        }
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        appendOutput("AI Error: ${e.message}\\n$ ")
                    }
                }
            }
            return
        }"""

if old_logic in text:
    text = text.replace(old_logic, new_logic)
    with open('app/src/main/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapter.kt', 'w') as f:
        f.write(text)
    print("Replaced logic")
else:
    print("Could not find logic")
