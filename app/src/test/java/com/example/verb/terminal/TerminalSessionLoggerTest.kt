package com.example.verb.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TerminalSessionLoggerTest {

    @Before
    fun setup() {
        TerminalSessionLogger.clear()
    }

    @Test
    fun `logs capture entries and respect capacity`() {
        TerminalSessionLogger.info(LogCategory.LIFECYCLE, "Test lifecycle event")
        TerminalSessionLogger.error(LogCategory.JNI, "Test JNI error")

        val entries = TerminalSessionLogger.logs.value
        assertEquals(2, entries.size)
        assertEquals("Test lifecycle event", entries[0].message)
        assertEquals(LogCategory.LIFECYCLE, entries[0].category)
        assertEquals(LogLevel.INFO, entries[0].level)

        assertEquals("Test JNI error", entries[1].message)
        assertEquals(LogCategory.JNI, entries[1].category)
        assertEquals(LogLevel.ERROR, entries[1].level)
    }

    @Test
    fun `diagnostic report export contains session details and log history`() {
        TerminalSessionLogger.info(LogCategory.SHELL, "Shell initialized")
        val report = TerminalSessionLogger.exportDiagnosticReport(
            sessionState = TerminalSessionState.RUNNING,
            workingDir = "/data/user/0/com.example/files",
            shellExecutable = "/system/bin/sh"
        )

        assertTrue(report.contains("VERB TERMINAL DIAGNOSTIC REPORT"))
        assertTrue(report.contains("Session State: RUNNING"))
        assertTrue(report.contains("Working Directory: /data/user/0/com.example/files"))
        assertTrue(report.contains("Shell Executable: /system/bin/sh"))
        assertTrue(report.contains("Shell initialized"))
    }

    @Test
    fun `clear empties log history`() {
        TerminalSessionLogger.info(LogCategory.IO, "Input command")
        assertEquals(1, TerminalSessionLogger.logs.value.size)

        TerminalSessionLogger.clear()
        assertTrue(TerminalSessionLogger.logs.value.isEmpty())
    }
}
