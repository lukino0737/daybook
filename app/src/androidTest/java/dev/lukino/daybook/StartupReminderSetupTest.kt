package dev.lukino.daybook

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.ui.ReminderSetupPreferences
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StartupReminderSetupTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Before fun firstLaunchWithNotificationPermissionAlreadyGranted() {
        ReminderSetupPreferences(instrumentation.targetContext).apply {
            notificationRequested = false
            guideCompleted = false
        }
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand(
            "pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS").use {
                java.io.FileInputStream(it.fileDescriptor).readBytes()
            }
    }
    @Test fun skipSurvivesRecreationAndNewLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("reminder-setup-guide").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("reminder-setup-guide").assertIsDisplayed()
            compose.onNodeWithTag("reminder-setup-skip").performClick()
            scenario.recreate()
            compose.onNodeWithTag("reminder-setup-guide").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("reminder-setup-guide").assertDoesNotExist()
            compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        }
    }
    @Test fun guideOpensSettingsAndDoesNotReturnAfterLeaving() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("reminder-setup-open").performClick()
            compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
            compose.onNodeWithTag("autostart-system-settings").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("heads-up-system-settings").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("reminder-settings-back").performClick()
            compose.onNodeWithTag("reminder-setup-guide").assertDoesNotExist()
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("reminder-setup-guide").assertDoesNotExist()
        }
    }
    @Test fun backSkipsGuideAndSettingsRemainAccessibleFromMenu() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithTag("reminder-setup-guide").assertIsDisplayed()
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use {
                java.io.FileInputStream(it.fileDescriptor).readBytes()
            }
            compose.onNodeWithTag("reminder-setup-guide").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithTag("backup-menu").performClick()
            compose.onNodeWithText("提醒设置", substring = false).performClick()
            compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
        }
    }
}
