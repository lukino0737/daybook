package dev.lukino.daybook

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AiLifecycleTest {
    @Test fun seedProcessMemoryForTerminationCheck() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("daybookAiProcess") == "seed")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try { scenario.onActivity { app.ai.input("虚构的进程结束验收输入"); assertTrue(app.ai.state.value.input.isNotBlank()) } }
        finally { scenario.close() }
    }
    @Test fun newProcessDoesNotRestoreChat() {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("daybookAiProcess") == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        assertEquals("", app.ai.state.value.input)
        assertTrue(app.ai.state.value.lines.isEmpty())
        assertTrue(app.ai.state.value.drafts.isEmpty())
    }
    @Test fun activityRecreationKeepsProcessMemoryWithoutSavedState() {
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { app.ai.input("仅用于重建验收的虚构输入") }
            scenario.recreate()
            scenario.onActivity { assertEquals("仅用于重建验收的虚构输入", app.ai.state.value.input); app.ai.clear() }
        } finally { scenario.close() }
    }
}
