package dev.lukino.daybook

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime

/** External test driver kills the background process or reboots between seed and verify. */
class ReminderLifecycleTest {
    @Test fun reminderSurvivesSystemLifecycle() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("daybookLifecycle")
        assumeTrue(mode == "seed" || mode == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val id = "00000000-0000-0000-0000-000000000104"
        if (mode == "seed") {
            val entry = Entry(id = id, kind = EntryKind.TASK, title = "DAYBOOK_LIFECYCLE_FIXTURE",
                reminderAt = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0).toString())
            app.repository.save(entry)
            app.reminders.reconcile()
            assertNull(app.reminders.failure.value)
            assertTrue(app.reminders.notificationsEnabled() && app.reminders.exactEnabled())
        } else {
            val entry = app.repository.all().single { it.id == id }
            assertEquals(entry.reminderAt, entry.reminderDeliveredFor)
            app.repository.delete(id)
            app.reminders.reconcile()
        }
    }
}
