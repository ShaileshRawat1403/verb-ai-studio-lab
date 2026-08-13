import re

with open('app/src/test/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapterTest.kt', 'r') as f:
    content = f.read()

# find the last closing brace and move it to the end
content = content.replace('}\n}', '}\n\n    @Test\n    fun `sendCommand processes clear command correctly`() {\n        adapter.sendCommand("clear")\n        assertEquals("$ ", adapter.terminalOutput.value)\n    }\n\n    @Test\n    fun `sendCommand processes cd command and updates working directory`() {\n        val childDir = File(workingDir, "test_dir")\n        childDir.mkdirs()\n        adapter.sendCommand("cd test_dir")\n        assertEquals(childDir.absolutePath, adapter.currentWorkingDirectory())\n    }\n}')

with open('app/src/test/java/com/example/verb/terminal/TermuxTerminalRuntimeAdapterTest.kt', 'w') as f:
    f.write(content)
