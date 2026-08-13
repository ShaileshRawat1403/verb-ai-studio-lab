import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

bootstrap = """
    private fun bootstrapBinaries() {
        val app = getApplication<Application>()
        val binDir = java.io.File(app.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        
        val curlFile = java.io.File(binDir, "curl")
        if (!curlFile.exists()) {
            try {
                val arch = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" || it == "x86_64" }
                if (arch != null) {
                    val inputStream = app.assets.open("$arch/curl")
                    val outputStream = java.io.FileOutputStream(curlFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    curlFile.setExecutable(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startShellProcess() {"""

text = text.replace("    private fun startShellProcess() {", bootstrap)

new_start = """    private fun startShellProcess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bootstrapBinaries()
                val pb = ProcessBuilder("/system/bin/sh")
                    .directory(activeWorkingDir)
                    
                val env = pb.environment()
                val binPath = java.io.File(getApplication<Application>().filesDir, "bin").absolutePath
                env["PATH"] = "$binPath:" + (System.getenv("PATH") ?: "/system/bin:/system/xbin")"""

old_start = """    private fun startShellProcess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("/system/bin/sh")
                    .directory(activeWorkingDir)
                    
                val env = pb.environment()
                env["PATH"] = System.getenv("PATH") ?: "/system/bin:/system/xbin" """

text = text.replace(old_start, new_start)
# wait, there's a trailing space in the old string maybe. Let's use regex instead.

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)

print("Saved patch script")
