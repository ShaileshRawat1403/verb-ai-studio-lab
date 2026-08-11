package com.example.verb.terminal

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalDiagnosticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `executeShellVerification returns non-null report`() {
        val root = tempFolder.root
        val report = TerminalDiagnostics.executeShellVerification(root)

        assertNotNull(report)
        assertTrue(report.executionTimeMs >= 0)
        // Shell accessibility check in Android test runner
        assertNotNull(report.sampleBinaries)
    }

    @Test
    fun `agent query intent handling returns agent response`() {
        val intentEngine = com.example.verb.intent.IntentEngine()
        val intent = intentEngine.resolveIntent("hi who are you")

        org.junit.Assert.assertEquals("agent.query", intent.id)
        org.junit.Assert.assertEquals("Verb AI Assistant", intent.name)

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val actionRegistry = com.example.verb.actions.ActionRegistry(context)
        val result = actionRegistry.executeAction(intent)

        assertTrue(result.isSuccess)
        assertTrue(result.title.contains("Verb AI Assistant"))
    }
}
