package com.example.verb.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("TerminalSettings", Context.MODE_PRIVATE) }
    

    var geminiKey by remember { mutableStateOf(sharedPrefs.getString("GEMINI_API_KEY", "") ?: "") }
    var openAiKey by remember { mutableStateOf(sharedPrefs.getString("OPENAI_API_KEY", "") ?: "") }
    var defaultAiProvider by remember { mutableStateOf(sharedPrefs.getString("DEFAULT_AI_PROVIDER", "gemini") ?: "gemini") }

    var geminiError by remember { mutableStateOf(false) }
    var openAiError by remember { mutableStateOf(false) }

    fun validateKeys(): Boolean {
        var isValid = true
        if (geminiKey.isNotBlank() && !geminiKey.startsWith("AIzaSy")) {
            geminiError = true
            isValid = false
        } else {
            geminiError = false
        }
        
        if (openAiKey.isNotBlank() && !openAiKey.startsWith("sk-")) {
            openAiError = true
            isValid = false
        } else {
            openAiError = false
        }
        return isValid
    }



    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Terminal AI Settings", style = MaterialTheme.typography.titleLarge)
            
            Text("Default AI Provider (used for generic 'ai' command)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = defaultAiProvider == "gemini",
                    onClick = { defaultAiProvider = "gemini" },
                    label = { Text("Gemini") }
                )
                FilterChip(
                    selected = defaultAiProvider == "openai",
                    onClick = { defaultAiProvider = "openai" },
                    label = { Text("OpenAI") }
                )
            }
            
            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it },
                label = { Text("OpenAI API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
                        Button(
                onClick = {
                    if (validateKeys()) {
                        sharedPrefs.edit()
                            .putString("GEMINI_API_KEY", geminiKey)
                            .putString("OPENAI_API_KEY", openAiKey)
                            .putString("DEFAULT_AI_PROVIDER", defaultAiProvider)
                            .apply()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
