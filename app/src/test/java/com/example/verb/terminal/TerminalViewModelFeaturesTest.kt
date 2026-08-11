package com.example.verb.terminal

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.verb.viewmodel.TerminalTheme
import com.example.verb.viewmodel.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalViewModelFeaturesTest {

    @Test
    fun testCommandHistoryBufferNavigation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = TerminalViewModel(app)

        vm.executeCommand("ls -la")
        vm.executeCommand("git status")
        vm.executeCommand("node -v")

        assertEquals(3, vm.commandHistory.value.size)

        // Navigate history up (older commands)
        val cmd1 = vm.navigateHistoryUp()
        assertEquals("node -v", cmd1)

        val cmd2 = vm.navigateHistoryUp()
        assertEquals("git status", cmd2)

        val cmd3 = vm.navigateHistoryUp()
        assertEquals("ls -la", cmd3)

        // Navigating history down (newer commands)
        val cmd4 = vm.navigateHistoryDown()
        assertEquals("git status", cmd4)

        val cmd5 = vm.navigateHistoryDown()
        assertEquals("node -v", cmd5)

        val cmd6 = vm.navigateHistoryDown()
        assertEquals("", cmd6)
    }

    @Test
    fun testAutocompleteSuggestions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = TerminalViewModel(app)

        val gitSuggestions = vm.getAutocompleteSuggestions("git")
        assertTrue(gitSuggestions.isNotEmpty())
        assertTrue(gitSuggestions.any { it.startsWith("git ") })

        val nodeSuggestions = vm.getAutocompleteSuggestions("nod")
        assertTrue(nodeSuggestions.contains("node index.js"))

        val emptySuggestions = vm.getAutocompleteSuggestions("")
        assertTrue(emptySuggestions.isEmpty())
    }

    @Test
    fun testThemeSwitching() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = TerminalViewModel(app)

        assertEquals(TerminalTheme.MIDNIGHT, vm.terminalTheme.value)

        vm.toggleTheme()
        assertEquals(TerminalTheme.LIGHT, vm.terminalTheme.value)

        vm.toggleTheme()
        assertEquals(TerminalTheme.MIDNIGHT, vm.terminalTheme.value)

        vm.setTheme(TerminalTheme.LIGHT)
        assertEquals(TerminalTheme.LIGHT, vm.terminalTheme.value)
    }
}
