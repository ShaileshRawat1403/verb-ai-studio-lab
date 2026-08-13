import re

with open('app/src/main/java/com/example/verb/terminal/TerminalCommandEngine.kt', 'r') as f:
    text = f.read()

# Remove toolchain fallback logic entirely
match = re.search(r'(// Try System ProcessBuilder execution first if executable is in system PATH.*?)(// Fallback Toolchain Engine for git, node, bun, python, and unix utilities.*)', text, re.DOTALL)
if match:
    system_part = match.group(1)
    
    new_tail = """
        val systemResult = tryExecuteSystemProcess(trimmed, currentDir, environment)
        if (systemResult != null) {
            return systemResult
        }
        
        return CommandExecutionResult("sh: $trimmed: command not found\\n")
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
                val formatted = if (!output.endsWith("\\n")) "$output\\n" else output
                CommandExecutionResult(formatted)
            } else {
                CommandExecutionResult(if (output.isNotBlank()) output else "sh: $command: command not found\\n")
            }
        } catch (e: Exception) {
            null
        }
    }
}
"""
    
    text = text[:match.start()] + new_tail
    with open('app/src/main/java/com/example/verb/terminal/TerminalCommandEngine.kt', 'w') as f:
        f.write(text)
    print("Stripped fake engine.")
else:
    print("Failed to strip engine.")
