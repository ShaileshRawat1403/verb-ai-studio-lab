package com.example.verb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.verb.terminal.TerminalRuntimeAdapter
import java.io.File

@Composable
fun FileExplorerDrawer(
    terminalRuntime: TerminalRuntimeAdapter?,
    isDark: Boolean,
    onFileClicked: (File) -> Unit
) {
    var currentDir by remember { mutableStateOf(File(terminalRuntime?.currentWorkingDirectory() ?: "/")) }
    var files by remember { mutableStateOf(emptyList<File>()) }

    LaunchedEffect(currentDir, terminalRuntime?.currentWorkingDirectory()) {
        val actualDir = File(terminalRuntime?.currentWorkingDirectory() ?: currentDir.absolutePath)
        if (actualDir.exists() && actualDir.isDirectory) {
            currentDir = actualDir
        }
        val list = currentDir.listFiles()?.toList() ?: emptyList()
        files = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = if (isDark) Color(0xFF161820) else Color(0xFFE2E8F0),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                if (currentDir.parentFile != null) {
                    IconButton(
                        onClick = { currentDir = currentDir.parentFile!! },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Up",
                            tint = if (isDark) Color.White else Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = currentDir.absolutePath,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isDark) Color.White else Color.Black
                )
            }
        }
        
        Divider(color = if (isDark) Color(0xFF333333) else Color(0xFFCCCCCC))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (file.isDirectory) {
                                currentDir = file
                            } else {
                                onFileClicked(file)
                            }
                        }
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (file.isDirectory) Color(0xFF3B82F6) else (if (isDark) Color.LightGray else Color.Gray),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = file.name,
                        fontSize = 14.sp,
                        color = if (isDark) Color.White else Color.Black
                    )
                }
            }
        }
    }
}
