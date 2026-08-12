package com.example.verb.terminal

import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TerminalAiHelper {
    private val model by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    suspend fun analyzeTerminalOutput(output: String): String {
        return withContext(Dispatchers.IO) {
            if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
                return@withContext "AI Assistance is unavailable because the GEMINI_API_KEY is not set in the Secrets panel."
            }

            val prompt = """
                You are a senior Linux and Android sysadmin assistant. The user is using a local Android native terminal (sh) and needs help understanding the recent terminal output.
                
                Please interpret the following terminal buffer. Keep your answer brief, highlight any obvious errors, and provide 1-2 actionable suggestions or commands they should try next.
                Format your response clearly.

                Terminal Output:
                ```
                ${output.takeLast(2000)}
                ```
            """.trimIndent()

            try {
                val response = model.generateContent(prompt)
                response.text ?: "Could not generate an analysis."
            } catch (e: Exception) {
                "Error analyzing terminal: ${e.message}"
            }
        }
    }
}
