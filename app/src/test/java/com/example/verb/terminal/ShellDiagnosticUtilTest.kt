package com.example.verb.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShellDiagnosticUtilTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `diagnose non existent path returns exists false and diagnostic error`() {
        val path = "/invalid/non_existent_shell_binary_${System.currentTimeMillis()}"
        val diag = ShellDiagnosticUtil.diagnoseShellExecutable(path)

        assertFalse(diag.exists)
        assertFalse(diag.isAccessible)
        assertFalse(diag.canExecute)
        assertEquals(path, diag.executablePath)
        assertTrue(diag.errorMessage?.contains("does not exist") == true)
    }

    @Test
    fun `diagnose valid executable file returns accessible true`() {
        val testFile = tempFolder.newFile("test_shell.sh")
        testFile.setExecutable(true)
        testFile.setReadable(true)

        val diag = ShellDiagnosticUtil.diagnoseShellExecutable(testFile.absolutePath)

        assertTrue(diag.exists)
        assertTrue(diag.isFile)
        assertTrue(diag.canExecute)
        assertTrue(diag.canRead)
        assertTrue(diag.isAccessible)
        assertNull(diag.errorMessage)
    }

    @Test
    fun `diagnose file without execute permission returns missing execute permission error`() {
        val testFile = tempFolder.newFile("test_non_exec_shell.sh")
        testFile.setExecutable(false)

        val diag = ShellDiagnosticUtil.diagnoseShellExecutable(testFile.absolutePath)

        assertTrue(diag.exists)
        assertTrue(diag.isFile)
        assertFalse(diag.canExecute)
        assertFalse(diag.isAccessible)
        assertTrue(diag.errorMessage?.contains("lacks execution permission") == true)
    }
}
