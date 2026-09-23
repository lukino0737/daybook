package dev.lukino.daybook

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AiLifecycleTest {
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
