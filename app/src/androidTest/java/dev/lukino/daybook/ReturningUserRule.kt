package dev.lukino.daybook

import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.ui.ReminderSetupPreferences
import org.junit.rules.ExternalResource

/** Existing feature tests start as a returning user; startup has its own cold-launch tests. */
class ReturningUserRule : ExternalResource() {
    override fun before() {
        ReminderSetupPreferences(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            notificationRequested = true
            guideCompleted = true
        }
    }
}
