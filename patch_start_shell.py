import re

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'r') as f:
    text = f.read()

new_start = """    private fun startShellProcess() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bootstrapBinaries()
                val pb = ProcessBuilder("/system/bin/sh")
                    .directory(activeWorkingDir)
                    
                val env = pb.environment()
                val binPath = java.io.File(getApplication<Application>().filesDir, "bin").absolutePath
                env["PATH"] = "$binPath:" + (System.getenv("PATH") ?: "/system/bin:/system/xbin")"""

text = re.sub(r'    private fun startShellProcess\(\) \{\s+viewModelScope\.launch\(Dispatchers\.IO\) \{\s+try \{\s+val pb = ProcessBuilder\("/system/bin/sh"\)\s+\.directory\(activeWorkingDir\)\s+val env = pb\.environment\(\)\s+env\["PATH"\] = System\.getenv\("PATH"\) \?: "/system/bin:/system/xbin"', new_start, text)

with open('app/src/main/java/com/example/verb/viewmodel/TerminalViewModel.kt', 'w') as f:
    f.write(text)

