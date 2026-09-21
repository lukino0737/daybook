package dev.lukino.daybook

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.ui.ReminderSetupPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackgroundGuideTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun returningUserSeesGuideOnReminderUseOnceWithoutNewPermissionRequest() {
        val prefs = ReminderSetupPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
        prefs.notificationRequested = true; prefs.guideCompleted = true; prefs.backgroundGuideCompleted = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("background-reminder-guide").assertDoesNotExist()
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("reminder-toggle").performScrollTo().performClick()
            compose.onNodeWithTag("background-reminder-guide").assertIsDisplayed()
            compose.onNodeWithTag("background-guide-skip").performClick()
            scenario.recreate()
            compose.onNodeWithTag("background-reminder-guide").assertDoesNotExist()
            assertTrue(prefs.backgroundGuideCompleted)
            assertTrue(prefs.notificationRequested)
        }
    }
}
