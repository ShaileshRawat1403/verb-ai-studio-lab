package com.example.verb.terminal

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * Controller that streams terminal data from JNI PTY session or input streams,
 * parses ANSI sequences, and maintains a StateFlow buffer for UI rendering.
 */
class JniPtyStreamController(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val _bufferFlow = MutableStateFlow<AnnotatedString>(AnnotatedString(""))
    val bufferFlow: StateFlow<AnnotatedString> = _bufferFlow.asStateFlow()

    private val _rawTextFlow = MutableStateFlow("")
    val rawTextFlow: StateFlow<String> = _rawTextFlow.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var activeSession: NativeTerminalSession? = null
    private var streamJob: Job? = null

    /**
     * Attaches and begins streaming stdout/stderr from a [NativeTerminalSession].
     */
    fun attachSession(session: NativeTerminalSession) {
        stopStreaming()
        activeSession = session
        if (!session.isRunning) {
            session.start()
        }

        val stdout = session.stdoutStream
        if (stdout != null) {
            startStreamReader(stdout)
        }
    }

    /**
     * Starts reading from an arbitrary [InputStream].
     */
    fun startStreamReader(inputStream: InputStream) {
        stopStreaming()
        _isStreaming.value = true

        streamJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (_isStreaming.value) {
                    val count = inputStream.read(buffer)
                    if (count <= 0) break
                    val chunk = String(buffer, 0, count, Charsets.UTF_8)
                    appendRawText(chunk)
                }
            } catch (_: Exception) {
            } finally {
                _isStreaming.value = false
            }
        }
    }

    /**
     * Appends new raw text chunk to buffer, re-parsing ANSI styles.
     */
    fun appendRawText(chunk: String) {
        val updatedRaw = (_rawTextFlow.value + chunk).takeLast(100_000) // Keep reasonable buffer size
        _rawTextFlow.value = updatedRaw
        _bufferFlow.value = AnsiTextParser.parse(updatedRaw)
    }

    /**
     * Sends input string to active native PTY stdin.
     */
    fun sendInput(input: String) {
        activeSession?.write(input)
    }

    /**
     * Clears terminal buffer.
     */
    fun clearBuffer() {
        _rawTextFlow.value = ""
        _bufferFlow.value = AnnotatedString("")
    }

    /**
     * Stops reading stream and releases job.
     */
    fun stopStreaming() {
        _isStreaming.value = false
        streamJob?.cancel()
        streamJob = null
    }

    fun close() {
        stopStreaming()
        activeSession?.close()
        activeSession = null
    }
}
