package com.example.verb.terminal

import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandExecutionResult(
    val output: String,
    val newWorkingDir: File? = null,
    val shouldClearBuffer: Boolean = false
)

object TerminalCommandEngine {

    fun executeCommand(
        command: String,
        currentDir: File,
        environment: Map<String, String> = emptyMap()
    ): CommandExecutionResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return CommandExecutionResult("")
        }

        if (trimmed == "clear") {
            return CommandExecutionResult("", shouldClearBuffer = true)
        }

        // Handle 'cd' builtin explicitly
        if (trimmed == "cd" || trimmed.startsWith("cd ")) {
            val pathArg = trimmed.removePrefix("cd").trim()
            val targetDir = when {
                pathArg.isEmpty() || pathArg == "~" -> currentDir
                pathArg == ".." -> currentDir.parentFile ?: currentDir
                pathArg.startsWith("/") -> File(pathArg)
                else -> File(currentDir, pathArg)
            }

            return if (targetDir.exists() && targetDir.isDirectory) {
                CommandExecutionResult("", newWorkingDir = targetDir)
            } else {
                CommandExecutionResult("cd: no such file or directory: $pathArg\n")
            }
        }

        // Handle 'pwd'
        if (trimmed == "pwd") {
            return CommandExecutionResult("${currentDir.absolutePath}\n")
        }

        // Handle 'ls'
        if (trimmed == "ls" || trimmed.startsWith("ls ")) {
            val showAll = trimmed.contains("-a") || trimmed.contains("-la") || trimmed.contains("-al")
            val longFormat = trimmed.contains("-l") || trimmed.contains("-la") || trimmed.contains("-al")
            
            val files = currentDir.listFiles()?.sortedBy { it.name } ?: emptyList()
            val sb = StringBuilder()
            
            for (f in files) {
                if (!showAll && f.name.startsWith(".")) continue
                if (longFormat) {
                    val type = if (f.isDirectory) "d" else "-"
                    val permissions = "rwxr-xr-x"
                    val size = f.length()
                    val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(f.lastModified()))
                    sb.append(String.format("%s%s 1 user group %8d %s %s\n", type, permissions, size, date, f.name))
                } else {
                    sb.append("${f.name}  ")
                }
            }
            if (!longFormat && files.isNotEmpty()) {
                sb.append("\n")
            }
            return CommandExecutionResult(sb.toString())
        }

        
        val systemResult = tryExecuteSystemProcess(trimmed, currentDir, environment)
        if (systemResult != null) {
            return systemResult
        }
        
        return CommandExecutionResult("sh: $trimmed: command not found\n")
    }
    
    private fun tryExecuteSystemProcess(
        command: String,
        currentDir: File,
        environment: Map<String, String>
    ): CommandExecutionResult? {
        return try {
            val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin:/vendor/bin:/data/local/tmp:/usr/bin:/bin"
            val procBuilder = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(currentDir)
                .redirectErrorStream(true)
                
            procBuilder.environment().putAll(environment)
            if (!procBuilder.environment().containsKey("PATH")) {
                procBuilder.environment()["PATH"] = sysPath
            }

            val process = procBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            // If system process succeeded and produced clean output without "inaccessible or not found"
            val isOutputError = output.contains("inaccessible or not found", ignoreCase = true) ||
                    output.contains("not found", ignoreCase = true) ||
                    output.contains("Permission denied", ignoreCase = true)

            if (exitCode == 0 && output.isNotBlank() && !isOutputError) {
                val formatted = if (!output.endsWith("\n")) "$output\n" else output
                CommandExecutionResult(formatted)
            } else {
                CommandExecutionResult(if (output.isNotBlank()) output else "sh: $command: command not found\n")
            }
        } catch (e: Exception) {
            null
        }
    }
}
