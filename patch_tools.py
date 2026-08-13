import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

# We need to extract jq and busybox just like curl.
# And for busybox, we need to run it with --install -s

new_bootstrap = """
    private fun bootstrapBinaries() {
        val app = getApplication<Application>()
        val binDir = java.io.File(app.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        
        val certFile = java.io.File(binDir, "cacert.pem")
        if (!certFile.exists()) {
            try {
                val inputStream = app.assets.open("cacert.pem")
                val outputStream = java.io.FileOutputStream(certFile)
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val tools = listOf("curl", "jq", "busybox")
        val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" }
        
        tools.forEach { tool ->
            val toolFile = java.io.File(binDir, tool)
            if (!toolFile.exists()) {
                try {
                    if (arch != null) {
                        val inputStream = app.assets.open("$arch/$tool")
                        val outputStream = java.io.FileOutputStream(toolFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        toolFile.setExecutable(true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        // Let busybox install its symlinks if they don't exist yet
        val busyboxSymlinkInstalled = java.io.File(binDir, "vi").exists()
        if (!busyboxSymlinkInstalled && java.io.File(binDir, "busybox").exists()) {
            try {
                val pb = ProcessBuilder(java.io.File(binDir, "busybox").absolutePath, "--install", "-s", ".")
                pb.directory(binDir)
                pb.start().waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startShellProcess() {"""

# Replace old bootstrap logic with the new one
text = re.sub(r'\n    private fun bootstrapBinaries\(\) \{.*?\n    private fun startShellProcess\(\) \{', new_bootstrap, text, flags=re.DOTALL)

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)

