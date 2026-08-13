import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

bootstrap = """
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
        
        val curlFile = java.io.File(binDir, "curl")
        if (!curlFile.exists()) {"""

text = text.replace("""
    private fun bootstrapBinaries() {
        val app = getApplication<Application>()
        val binDir = java.io.File(app.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        
        val curlFile = java.io.File(binDir, "curl")
        if (!curlFile.exists()) {""", bootstrap)

env_patch = """                val env = pb.environment()
                val binPath = java.io.File(getApplication<Application>().filesDir, "bin").absolutePath
                val certPath = java.io.File(getApplication<Application>().filesDir, "bin/cacert.pem").absolutePath
                env["PATH"] = "$binPath:" + (System.getenv("PATH") ?: "/system/bin:/system/xbin")
                env["CURL_CA_BUNDLE"] = certPath"""

old_env_patch = """                val env = pb.environment()
                val binPath = java.io.File(getApplication<Application>().filesDir, "bin").absolutePath
                env["PATH"] = "$binPath:" + (System.getenv("PATH") ?: "/system/bin:/system/xbin")"""

text = text.replace(old_env_patch, env_patch)

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)

print("Patched cacert logic")
