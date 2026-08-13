package com.example.verb.terminal

import com.example.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context

object TerminalAiHelper {


    suspend fun analyzeTerminalOutput(context: Context, output: String): String {
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

            val prompt =  """
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
