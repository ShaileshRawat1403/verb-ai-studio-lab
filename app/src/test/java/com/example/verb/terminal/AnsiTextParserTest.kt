package com.example.verb.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnsiTextParserTest {

    @Test
    fun `plain text parsing returns matching string`() {
        val result = AnsiTextParser.parse("Hello World")
        assertEquals("Hello World", result.text)
    }

    @Test
    fun `parses standard ANSI red color escape sequence`() {
        val raw = "\u001B[31mRed Text\u001B[0m"
        val result = AnsiTextParser.parse(raw)
        assertEquals("Red Text", result.text)
        val styles = result.spanStyles
        assertTrue("Expected at least one SpanStyle", styles.isNotEmpty())
        val span = styles[0]
        assertEquals(0, span.start)
        assertEquals(8, span.end)
        assertEquals(Color(0xFFCD3131), span.item.color)
    }

    @Test
    fun `parses bold ansi sequence`() {
        val raw = "\u001B[1mBold Text\u001B[0m"
        val result = AnsiTextParser.parse(raw)
        assertEquals("Bold Text", result.text)
        val span = result.spanStyles[0]
        assertEquals(FontWeight.Bold, span.item.fontWeight)
    }

    @Test
    fun `parses combined color and style reset`() {
        val raw = "\u001B[32;1mGreen Bold\u001B[0m Plain"
        val result = AnsiTextParser.parse(raw)
        assertEquals("Green Bold Plain", result.text)
        assertTrue(result.spanStyles.isNotEmpty())
    }

    @Test
    fun `parses 256 color sequence`() {
        val raw = "\u001B[38;5;196mBright Red 256\u001B[0m"
        val result = AnsiTextParser.parse(raw)
        assertEquals("Bright Red 256", result.text)
        val span = result.spanStyles[0]
        assertNotNull(span.item.color)
    }
}
