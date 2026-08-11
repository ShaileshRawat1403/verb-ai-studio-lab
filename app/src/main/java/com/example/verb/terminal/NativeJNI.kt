package com.example.verb.terminal

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-level JNI interface wrapper for native terminal PTY operations.
 */
object NativeJNI {
    private val isLoaded = AtomicBoolean(false)

    fun loadNativeLibrary(): Boolean {
        if (!isLoaded.get()) {
            try {
                System.loadLibrary("termux")
                isLoaded.set(true)
            } catch (t: Throwable) {
                TerminalSessionLogger.error(LogCategory.JNI, "Failed to load libtermux.so: ${t.message}")
                return false
            }
        }
        return isLoaded.get()
    }

    @JvmStatic
    external fun createSubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>?,
        envVars: Array<String>?,
        processId: IntArray,
        rows: Int,
        columns: Int,
        cellWidth: Int,
        cellHeight: Int
    ): Int

    @JvmStatic
    external fun createPtySubprocess(
        cmd: String,
        cwd: String,
        args: Array<String>?,
        envVars: Array<String>?,
        processId: IntArray,
        rows: Int,
        columns: Int,
        cellWidth: Int,
        cellHeight: Int
    ): Int

    @JvmStatic
    external fun setPtyWindowSize(fd: Int, rows: Int, cols: Int, cellWidth: Int, cellHeight: Int)

    @JvmStatic
    external fun setPtyUTF8Mode(fd: Int)

    @JvmStatic
    external fun waitFor(processId: Int): Int

    @JvmStatic
    external fun close(fileDescriptor: Int)
}
