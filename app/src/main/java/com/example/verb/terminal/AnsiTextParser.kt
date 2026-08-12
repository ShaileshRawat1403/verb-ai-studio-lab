package com.example.verb.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * ANSI escape code parser for styling terminal text in Jetpack Compose UI.
 * Handles standard 8-color, 16-color, 256-color (SGR 38;5;n / 48;5;n),
 * truecolor (SGR 38;2;r;g;b), bold, italic, underline, strikethrough, and reset sequences.
 */
object AnsiTextParser {

    private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*[a-zA-Z]")

    private val STANDARD_COLORS = mapOf(
        30 to Color(0xFF000000), // Black
        31 to Color(0xFFCD3131), // Red
        32 to Color(0xFF0DBC79), // Green
        33 to Color(0xFFE5E510), // Yellow
        34 to Color(0xFF2472C8), // Blue
        35 to Color(0xFFBC3FBC), // Magenta
        36 to Color(0xFF11A8CD), // Cyan
        37 to Color(0xFFE5E5E5), // White
        90 to Color(0xFF666666), // Bright Black (Gray)
        91 to Color(0xFFF14C4C), // Bright Red
        92 to Color(0xFF23D18B), // Bright Green
        93 to Color(0xFFF5F543), // Bright Yellow
        94 to Color(0xFF3B8EEA), // Bright Blue
        95 to Color(0xFFD670D6), // Bright Magenta
        96 to Color(0xFF29B8DB), // Bright Cyan
        97 to Color(0xFFFFFFFF)  // Bright White
    )

    private val BG_STANDARD_COLORS = mapOf(
        40 to Color(0xFF000000),
        41 to Color(0xFFCD3131),
        42 to Color(0xFF0DBC79),
        43 to Color(0xFFE5E510),
        44 to Color(0xFF2472C8),
        45 to Color(0xFFBC3FBC),
        46 to Color(0xFF11A8CD),
        47 to Color(0xFFE5E5E5),
        100 to Color(0xFF666666),
        101 to Color(0xFFF14C4C),
        102 to Color(0xFF23D18B),
        103 to Color(0xFFF5F543),
        104 to Color(0xFF3B8EEA),
        105 to Color(0xFFD670D6),
        106 to Color(0xFF29B8DB),
        107 to Color(0xFFFFFFFF)
    )

    data class TextStyleState(
        val fgColor: Color? = null,
        val bgColor: Color? = null,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false,
        val isStrikethrough: Boolean = false
    ) {
        fun toSpanStyle(): SpanStyle {
            return SpanStyle(
                color = fgColor ?: Color.Unspecified,
                background = bgColor ?: Color.Unspecified,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = when {
                    isUnderline && isStrikethrough -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    isUnderline -> TextDecoration.Underline
                    isStrikethrough -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                }
            )
        }
    }

    /**
     * Parses raw terminal output string containing ANSI escape sequences into a styled [AnnotatedString].
     */
    fun parse(rawText: String, defaultColor: Color = Color(0xFFE2E8F0)): AnnotatedString {
        return buildAnnotatedString {
            var currentStyle = TextStyleState(fgColor = defaultColor)
            var lastIndex = 0

            ANSI_REGEX.findAll(rawText).forEach { matchResult ->
                val textChunk = rawText.substring(lastIndex, matchResult.range.first)
                if (textChunk.isNotEmpty()) {
                    withStyle(currentStyle.toSpanStyle()) {
                        append(textChunk)
                    }
                }

                val seq = matchResult.value
                currentStyle = applyAnsiSequence(seq, currentStyle, defaultColor)
                lastIndex = matchResult.range.last + 1
            }

            if (lastIndex < rawText.length) {
                val remaining = rawText.substring(lastIndex)
                withStyle(currentStyle.toSpanStyle()) {
                    append(remaining)
                }
            }
        }
    }

    fun applyBasicSyntaxHighlighting(annotatedString: AnnotatedString, isDark: Boolean): AnnotatedString {
        val text = annotatedString.text
        val builder = AnnotatedString.Builder(annotatedString)

        val errorColor = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626) // Red
        val successColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A) // Green
        val pathColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB) // Blue

        // Error regex: matches "error:", "fatal:", "failed", etc.
        val errorRegex = Regex("(?i)\\b(error|fatal|exception|failed|failure|command not found)\\b.*")
        errorRegex.findAll(text).forEach { matchResult ->
            builder.addStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Bold), matchResult.range.first, matchResult.range.last + 1)
        }

        // Success regex: matches "success", "OK", "done"
        val successRegex = Regex("(?i)\\b(success|successfully|ok|done|completed)\\b")
        successRegex.findAll(text).forEach { matchResult ->
            builder.addStyle(SpanStyle(color = successColor, fontWeight = FontWeight.Bold), matchResult.range.first, matchResult.range.last + 1)
        }

        // Path regex: matches /path/to/file or ./path or ~/path
        val pathRegex = Regex("(?<=^|\\s)(/[a-zA-Z0-9_.-]+)+|(\\./[a-zA-Z0-9_.-]+)+|~(/[a-zA-Z0-9_.-]+)*")
        pathRegex.findAll(text).forEach { matchResult ->
            builder.addStyle(SpanStyle(color = pathColor, textDecoration = TextDecoration.Underline), matchResult.range.first, matchResult.range.last + 1)
        }
        
        return builder.toAnnotatedString()
    }

    private fun applyAnsiSequence(
        seq: String,
        current: TextStyleState,
        defaultColor: Color
    ): TextStyleState {
        if (!seq.endsWith("m")) return current // Only handle SGR (Select Graphic Rendition) for styling

        val paramsStr = seq.removePrefix("\u001B[").removeSuffix("m")
        if (paramsStr.isEmpty()) return TextStyleState(fgColor = defaultColor)

        val params = paramsStr.split(";").mapNotNull { it.toIntOrNull() }
        if (params.isEmpty()) return TextStyleState(fgColor = defaultColor)

        var updated = current
        var idx = 0

        while (idx < params.size) {
            when (val code = params[idx]) {
                0 -> updated = TextStyleState(fgColor = defaultColor)
                1 -> updated = updated.copy(isBold = true)
                3 -> updated = updated.copy(isItalic = true)
                4 -> updated = updated.copy(isUnderline = true)
                9 -> updated = updated.copy(isStrikethrough = true)
                22 -> updated = updated.copy(isBold = false)
                23 -> updated = updated.copy(isItalic = false)
                24 -> updated = updated.copy(isUnderline = false)
                29 -> updated = updated.copy(isStrikethrough = false)
                39 -> updated = updated.copy(fgColor = defaultColor)
                49 -> updated = updated.copy(bgColor = null)
                in 30..37, in 90..97 -> updated = updated.copy(fgColor = STANDARD_COLORS[code])
                in 40..47, in 100..107 -> updated = updated.copy(bgColor = BG_STANDARD_COLORS[code])
                38 -> { // Foreground extended colors
                    if (idx + 2 < params.size && params[idx + 1] == 5) { // 256 color
                        val colorIndex = params[idx + 2]
                        updated = updated.copy(fgColor = parse256Color(colorIndex))
                        idx += 2
                    } else if (idx + 4 < params.size && params[idx + 1] == 2) { // Truecolor RGB
                        val r = params[idx + 2].coerceIn(0, 255)
                        val g = params[idx + 3].coerceIn(0, 255)
                        val b = params[idx + 4].coerceIn(0, 255)
                        updated = updated.copy(fgColor = Color(r, g, b))
                        idx += 4
                    }
                }
                48 -> { // Background extended colors
                    if (idx + 2 < params.size && params[idx + 1] == 5) { // 256 color
                        val colorIndex = params[idx + 2]
                        updated = updated.copy(bgColor = parse256Color(colorIndex))
                        idx += 2
                    } else if (idx + 4 < params.size && params[idx + 1] == 2) { // Truecolor RGB
                        val r = params[idx + 2].coerceIn(0, 255)
                        val g = params[idx + 3].coerceIn(0, 255)
                        val b = params[idx + 4].coerceIn(0, 255)
                        updated = updated.copy(bgColor = Color(r, g, b))
                        idx += 4
                    }
                }
            }
            idx++
        }

        return updated
    }

    private fun parse256Color(index: Int): Color {
        return when (index) {
            in 0..15 -> STANDARD_COLORS[if (index < 8) index + 30 else index + 82] ?: Color.Unspecified
            in 16..231 -> {
                val i = index - 16
                val r = (i / 36) * 51
                val g = ((i % 36) / 6) * 51
                val b = (i % 6) * 51
                Color(r, g, b)
            }
            in 232..255 -> {
                val gray = (index - 232) * 10 + 8
                Color(gray, gray, gray)
            }
            else -> Color.Unspecified
        }
    }
}
