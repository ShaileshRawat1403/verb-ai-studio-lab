package com.example.verb.terminal

import android.os.ParcelFileDescriptor
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * [NativeTerminalSession] uses JNI to interface with Android PTY (pseudo-terminal) APIs.
 * It manages standard file descriptors and stream handles for stdin, stdout, and stderr
 * to communicate with the spawned shell subprocess directly.
 */
class NativeTerminalSession(
    val shellPath: String = "/system/bin/sh",
    val cwd: String,
    val args: Array<String> = arrayOf("-l"),
    val env: Array<String> = emptyArray(),
    var rows: Int = 24,
    var columns: Int = 80,
    var cellWidthPixels: Int = 0,
    var cellHeightPixels: Int = 0
) {

    var masterFd: Int = -1
        private set

    var pid: Int = -1
        private set

    var parcelFileDescriptor: ParcelFileDescriptor? = null
        private set

    var masterFileDescriptor: FileDescriptor? = null
        private set

    var stdinFd: FileDescriptor? = null
        private set

    var stdoutFd: FileDescriptor? = null
        private set

    var stderrFd: FileDescriptor? = null
        private set

    var stdinStream: OutputStream? = null
        private set

    var stdoutStream: InputStream? = null
        private set

    var stderrStream: InputStream? = null
        private set

    var isRunning: Boolean = false
        private set

    var exitCode: Int? = null
        private set

    interface OutputListener {
        fun onOutputData(data: ByteArray, length: Int)
        fun onProcessExited(exitCode: Int)
    }

    var outputListener: OutputListener? = null

    /**
     * Initializes and spawns the native PTY process using JNI.
     * Sets up explicit file descriptors and I/O streams for stdin, stdout, and stderr.
     */
    fun start(): Boolean {
        if (isRunning) return true

        if (!NativeJNI.loadNativeLibrary()) {
            TerminalSessionLogger.error(LogCategory.JNI, "NativeTerminalSession failed: libtermux.so unavailable")
            return false
        }

        val processIdArray = IntArray(1)
        try {
            masterFd = try {
                NativeJNI.createPtySubprocess(
                    shellPath,
                    cwd,
                    args,
                    env,
                    processIdArray,
                    rows,
                    columns,
                    cellWidthPixels,
                    cellHeightPixels
                )
            } catch (_: Throwable) {
                NativeJNI.createSubprocess(
                    shellPath,
                    cwd,
                    args,
                    env,
                    processIdArray,
                    rows,
                    columns,
                    cellWidthPixels,
                    cellHeightPixels
                )
            }
            pid = processIdArray[0]

            if (masterFd < 0 || pid <= 0) {
                TerminalSessionLogger.error(LogCategory.JNI, "NativeTerminalSession creation failed. masterFd=$masterFd, pid=$pid")
                return false
            }

            // Adopt master PTY file descriptor
            val pfd = ParcelFileDescriptor.adoptFd(masterFd)
            parcelFileDescriptor = pfd
            val fd = pfd.fileDescriptor
            masterFileDescriptor = fd

            // Assign standard stdin, stdout, stderr descriptors & streams
            stdinFd = fd
            stdoutFd = fd
            stderrFd = fd

            stdinStream = FileOutputStream(fd)
            stdoutStream = FileInputStream(fd)
            stderrStream = FileInputStream(fd)

            isRunning = true
            TerminalSessionLogger.info(
                LogCategory.LIFECYCLE,
                "NativeTerminalSession PTY session started [PID=$pid, masterFd=$masterFd]"
            )
            return true
        } catch (t: Throwable) {
            TerminalSessionLogger.error(LogCategory.JNI, "Error starting NativeTerminalSession: ${t.message}")
            return false
        }
    }

    /**
     * Writes raw bytes to stdin.
     */
    fun write(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        if (!isRunning) return
        try {
            stdinStream?.write(data, offset, count)
            stdinStream?.flush()
        } catch (e: Exception) {
            TerminalSessionLogger.error(LogCategory.JNI, "Failed writing to PTY stdin: ${e.message}")
        }
    }

    /**
     * Writes text string to stdin.
     */
    fun write(text: String) {
        write(text.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Reads from stdout stream.
     */
    fun readStdout(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size): Int {
        if (!isRunning) return -1
        return try {
            stdoutStream?.read(buffer, offset, count) ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Reads from stderr stream.
     */
    fun readStderr(buffer: ByteArray, offset: Int = 0, count: Int = buffer.size): Int {
        if (!isRunning) return -1
        return try {
            stderrStream?.read(buffer, offset, count) ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Updates terminal window dimensions via JNI setPtyWindowSize.
     */
    fun updateWindowSize(newRows: Int, newCols: Int, newCellWidth: Int = 0, newCellHeight: Int = 0) {
        rows = newRows
        columns = newCols
        cellWidthPixels = newCellWidth
        cellHeightPixels = newCellHeight
        if (masterFd >= 0 && isRunning) {
            try {
                NativeJNI.setPtyWindowSize(masterFd, rows, columns, cellWidthPixels, cellHeightPixels)
            } catch (e: Exception) {
                TerminalSessionLogger.error(LogCategory.JNI, "Failed updating PTY window size: ${e.message}")
            }
        }
    }

    /**
     * Waits for the process to terminate.
     */
    fun waitFor(): Int {
        if (pid <= 0) return -1
        return try {
            val status = NativeJNI.waitFor(pid)
            exitCode = status
            isRunning = false
            outputListener?.onProcessExited(status)
            status
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Closes file descriptors and releases native resources.
     */
    fun close() {
        if (!isRunning && masterFd < 0) return
        isRunning = false
        try {
            stdinStream?.close()
        } catch (_: Exception) {}
        try {
            stdoutStream?.close()
        } catch (_: Exception) {}
        try {
            stderrStream?.close()
        } catch (_: Exception) {}
        try {
            parcelFileDescriptor?.close()
        } catch (_: Exception) {}
        if (masterFd >= 0) {
            try {
                NativeJNI.close(masterFd)
            } catch (_: Exception) {}
            masterFd = -1
        }
        TerminalSessionLogger.info(LogCategory.LIFECYCLE, "NativeTerminalSession closed [PID=$pid]")
    }
}
