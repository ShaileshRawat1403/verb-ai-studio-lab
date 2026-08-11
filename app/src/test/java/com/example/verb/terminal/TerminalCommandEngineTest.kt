package com.example.verb.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TerminalCommandEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `cd command updates working directory`() {
        val root = tempFolder.root
        val subDir = File(root, "sub_folder").apply { mkdirs() }

        val result = TerminalCommandEngine.executeCommand("cd sub_folder", root)
        assertNotNull(result.newWorkingDir)
        assertEquals(subDir.absolutePath, result.newWorkingDir?.absolutePath)
    }

    @Test
    fun `cd invalid directory returns error message`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("cd nonexistent_dir_123", root)
        assertTrue(result.output.contains("no such file or directory"))
    }

    @Test
    fun `ls command lists files and directories`() {
        val root = tempFolder.root
        File(root, "fileA.txt").createNewFile()
        File(root, "fileB.txt").createNewFile()

        val result = TerminalCommandEngine.executeCommand("ls", root)
        assertTrue(result.output.contains("fileA.txt"))
        assertTrue(result.output.contains("fileB.txt"))
    }

    @Test
    fun `git status command when repo not initialized`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("git status", root)
        assertTrue(result.output.contains("fatal: not a git repository"))
    }

    @Test
    fun `git init and git status workflow`() {
        val root = tempFolder.root
        val initResult = TerminalCommandEngine.executeCommand("git init", root)
        assertTrue(initResult.output.contains("Initialized empty Git repository"))

        val statusResult = TerminalCommandEngine.executeCommand("git status", root)
        assertTrue(statusResult.output.contains("On branch main"))
        assertTrue(statusResult.output.contains("working tree clean"))
    }

    @Test
    fun `git version command returns formatted version`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("git --version", root)
        assertTrue(result.output.contains("git version 2.43.0"))
    }

    @Test
    fun `node version command returns formatted node version`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("node -v", root)
        assertTrue(result.output.contains("v20.11.1"))
    }

    @Test
    fun `node expression evaluation`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("node -e \"console.log('Hello Verb')\"", root)
        assertTrue(result.output.contains("Hello Verb"))
    }

    @Test
    fun `bun version command returns formatted bun version`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("bun -v", root)
        assertTrue(result.output.contains("1.0.25"))
    }

    @Test
    fun `python version command returns formatted python version`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("python --version", root)
        assertTrue(result.output.contains("Python 3.11.7"))
    }

    @Test
    fun `python code evaluation`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("python -c \"print('Hello Python')\"", root)
        assertTrue(result.output.contains("Hello Python"))
    }

    @Test
    fun `mkdir touch and cat commands`() {
        val root = tempFolder.root
        TerminalCommandEngine.executeCommand("mkdir test_dir", root)
        assertTrue(File(root, "test_dir").isDirectory)

        TerminalCommandEngine.executeCommand("touch test_dir/note.txt", root)
        val noteFile = File(root, "test_dir/note.txt")
        assertTrue(noteFile.isFile)

        noteFile.writeText("Verb Terminal Works!")
        val catResult = TerminalCommandEngine.executeCommand("cat test_dir/note.txt", root)
        assertTrue(catResult.output.contains("Verb Terminal Works!"))
    }

    @Test
    fun `help command lists supported toolchains`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("help", root)
        assertTrue(result.output.contains("git"))
        assertTrue(result.output.contains("node"))
        assertTrue(result.output.contains("bun"))
        assertTrue(result.output.contains("python"))
    }

    @Test
    fun `clear command flags buffer clear`() {
        val root = tempFolder.root
        val result = TerminalCommandEngine.executeCommand("clear", root)
        assertTrue(result.shouldClearBuffer)
    }
}
