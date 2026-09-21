package dev.lukino.daybook

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StandaloneDeliveryTest {
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes(); Unit
    }
    @Test fun recurringNotificationSurvivesAdvancingScheduleAndEditsButNotPauseOrDelete() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val manager = app.getSystemService(NotificationManager::class.java)
        shell("pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS")
        shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow")
        val due = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
        val initial = StandaloneReminder(title = "DAYBOOK_V06_DELIVERY", startDate = due.toLocalDate().minusDays(2).toString(), time = due.toLocalTime().toString(), repeat = RepeatKind.DAILY)
        fun active() = manager.activeNotifications.filter { it.notification.extras.getString("daybook.source") == "reminder:${initial.id}" }
        try {
            app.repository.saveReminder(initial, due.minusHours(1).atZone(ZoneId.systemDefault()).toInstant())
            app.reminders.reconcile()
            val saved = app.repository.allReminders().single { it.id == initial.id }
            assertEquals(due.toString(), saved.deliveredFor)
            assertEquals(1, active().size)
            val tag = active().single().tag
            app.reminders.reconcile()
            assertEquals(tag, active().single().tag)
            app.repository.saveReminder(saved.copy(title = "改标题", showInCalendar = true))
            app.reminders.reconcile()
            assertEquals(tag, active().single().tag)
            manager.cancel(tag, 0)
            app.reminders.reconcile()
            assertTrue(active().isEmpty())
            val current = app.repository.allReminders().single { it.id == saved.id }
            app.repository.saveReminder(current.copy(enabled = false))
            app.reminders.reconcile()
            assertTrue(active().isEmpty())
            app.repository.saveReminder(app.repository.allReminders().single { it.id == saved.id }.copy(enabled = true))
            app.reminders.reconcile()
            assertTrue(active().isEmpty())
            assertNull(app.reminders.failure.value)
        } finally { app.repository.deleteReminder(initial.id); app.reminders.reconcile() }
    }
    @Test fun scheduleChangesCancelOldNotification() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val manager = app.getSystemService(NotificationManager::class.java)
        val due = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
        val initial = StandaloneReminder(title = "DAYBOOK_V06_PERMISSION", startDate = due.toLocalDate().toString(), time = due.toLocalTime().toString(), repeat = RepeatKind.DAILY)
        fun active() = manager.activeNotifications.filter { it.notification.extras.getString("daybook.source") == "reminder:${initial.id}" }
        try {
            shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow")
            shell("pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS")
            app.repository.saveReminder(initial, due.minusHours(1).atZone(ZoneId.systemDefault()).toInstant())
            app.reminders.reconcile()
            assertEquals(1, active().size)
            val saved = app.repository.allReminders().single { it.id == initial.id }
            app.repository.saveReminder(saved.copy(time = due.plusHours(2).toLocalTime().toString()))
            app.reminders.reconcile()
            assertTrue(active().isEmpty())
            assertNull(app.repository.allReminders().single { it.id == initial.id }.deliveredFor)
        } finally { shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow"); app.repository.deleteReminder(initial.id); app.reminders.reconcile() }
    }
    @Test fun deniedPermissionRetainsOccurrenceThenExternalGrantCatchesUp() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("v06Permission")
        org.junit.Assume.assumeTrue(mode == "denied" || mode == "granted")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val id = "00000000-0000-0000-0000-000000000608"
        if (mode == "denied") {
            assertFalse(app.reminders.exactEnabled())
            val due = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
            app.repository.saveReminder(StandaloneReminder(id = id, title = "DAYBOOK_V06_DENIED", startDate = due.toLocalDate().toString(), time = due.toLocalTime().toString(), repeat = RepeatKind.DAILY),
                due.minusHours(1).atZone(ZoneId.systemDefault()).toInstant())
            app.reminders.reconcile()
            assertNull(app.repository.allReminders().single { it.id == id }.deliveredFor)
        } else {
            assertTrue(app.reminders.exactEnabled())
            app.reminders.reconcile()
            assertNotNull(app.repository.allReminders().single { it.id == id }.deliveredFor)
            app.repository.deleteReminder(id)
            app.reminders.reconcile()
        }
    }

}
