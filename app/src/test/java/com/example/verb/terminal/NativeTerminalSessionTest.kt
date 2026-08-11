package com.example.verb.terminal

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NativeTerminalSessionTest {

    private lateinit var context: Context
    private lateinit var workingDir: File
    private lateinit var session: NativeTerminalSession

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        workingDir = context.filesDir
        session = NativeTerminalSession(
            shellPath = "/system/bin/sh",
            cwd = workingDir.absolutePath,
            args = arrayOf("-c", "echo hello"),
            env = arrayOf("TERM=xterm-256color")
        )
    }

    @Test
    fun `session initializes default configuration correctly`() {
        assertEquals("/system/bin/sh", session.shellPath)
        assertEquals(workingDir.absolutePath, session.cwd)
        assertEquals(24, session.rows)
        assertEquals(80, session.columns)
        assertFalse(session.isRunning)
        assertNull(session.masterFileDescriptor)
        assertNull(session.stdinFd)
        assertNull(session.stdoutFd)
        assertNull(session.stderrFd)
        assertNull(session.stdinStream)
        assertNull(session.stdoutStream)
        assertNull(session.stderrStream)
    }

    @Test
    fun `window size update modifies rows and columns`() {
        session.updateWindowSize(30, 100, 10, 20)
        assertEquals(30, session.rows)
        assertEquals(100, session.columns)
        assertEquals(10, session.cellWidthPixels)
        assertEquals(20, session.cellHeightPixels)
    }

    @Test
    fun `close on inactive session handles gracefully without errors`() {
        session.close()
        assertFalse(session.isRunning)
        assertEquals(-1, session.masterFd)
    }
}
