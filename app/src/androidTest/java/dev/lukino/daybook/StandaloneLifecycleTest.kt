package dev.lukino.daybook

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDateTime

/** Driver reboots the dedicated emulator between seed and verify, without clearing storage. */
class StandaloneLifecycleTest {
    @Test fun survivesRebootAndDoesNotResendOnNextLaunch() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("v06Lifecycle")
        assumeTrue(mode == "seed" || mode == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val id = "00000000-0000-0000-0000-000000000607"
        if (mode == "seed") {
            val due = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0)
            app.repository.saveReminder(StandaloneReminder(id = id, title = "DAYBOOK_V06_LIFECYCLE", startDate = due.toLocalDate().toString(), time = due.toLocalTime().toString(), repeat = RepeatKind.DAILY))
            app.reminders.reconcile()
            assertTrue(app.reminders.notificationsEnabled() && app.reminders.exactEnabled())
            assertNull(app.reminders.failure.value)
        } else {
            val r = app.repository.allReminders().single { it.id == id }
            assertEquals("${r.startDate}T${r.time}", r.deliveredFor)
            val manager = app.getSystemService(NotificationManager::class.java)
            val tags = manager.activeNotifications.filter { it.notification.extras.getString("daybook.source") == "reminder:$id" }.map { it.tag }
            assertEquals(1, tags.size)
            app.reminders.reconcile()
            assertEquals(tags, manager.activeNotifications.filter { it.notification.extras.getString("daybook.source") == "reminder:$id" }.map { it.tag })
            app.repository.deleteReminder(id)
            app.reminders.reconcile()
        }
    }
}
