package dev.lukino.daybook

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ReminderTest {
    private val app get() = ApplicationProvider.getApplicationContext<DaybookApplication>()
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
    private fun allow() {
        shell("pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS")
        shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow")
    }
    @Test fun notificationDeliveryDeduplicationAndCancellation() = runBlocking {
        allow()
        val manager = app.getSystemService(NotificationManager::class.java)
        val entry = Entry(kind = EntryKind.TASK, title = "通知测试", reminderAt = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0).toString())
        try {
            app.repository.save(entry)
            app.reminders.reconcile()
            assertEquals(entry.reminderAt, app.repository.all().first { it.id == entry.id }.reminderDeliveredFor)
            val notification = manager.activeNotifications.single { it.tag == entry.id }
            assertNotNull(notification.notification.contentIntent)
            assertEquals(entry.title, notification.notification.extras.getString("android.title"))
            manager.cancel(entry.id, 0)
            app.reminders.reconcile()
            assertFalse(manager.activeNotifications.any { it.tag == entry.id })
            app.repository.save(entry)
            app.reminders.reconcile()
            assertTrue(manager.activeNotifications.any { it.tag == entry.id })
            app.repository.save(entry.copy(completed = true))
            app.reminders.reconcile()
            assertFalse(manager.activeNotifications.any { it.tag == entry.id })
            app.repository.delete(entry.id)
            app.reminders.reconcile()
            assertFalse(manager.activeNotifications.any { it.tag == entry.id })
        } finally { app.repository.delete(entry.id); manager.cancel(entry.id, 0); allow() }
    }
    @Test fun deniedPermissionKeepsReminderPending() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("daybookDenied") == "true")
        assertFalse(app.reminders.notificationsEnabled())
        val entry = Entry(kind = EntryKind.TASK, title = "权限拒绝测试", reminderAt = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0).toString())
        try {
            app.repository.save(entry)
            app.reminders.reconcile()
            assertNull(app.repository.all().first { it.id == entry.id }.reminderDeliveredFor)
        } finally { app.repository.delete(entry.id) }
    }
    @Test fun systemAlarmActuallyDeliversWithoutManualRefresh() = runBlocking {
        allow()
        val manager = app.getSystemService(NotificationManager::class.java)
        val entry = Entry(kind = EntryKind.TASK, title = "系统定时测试", reminderAt = LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0).toString())
        try {
            app.repository.save(entry)
            app.reminders.reconcile()
            assertFalse(manager.activeNotifications.any { it.tag == entry.id })
            withTimeout(85_000) {
                while (!manager.activeNotifications.any { it.tag == entry.id }) delay(500)
            }
            assertEquals(entry.reminderAt, app.repository.all().first { it.id == entry.id }.reminderDeliveredFor)
        } finally { app.repository.delete(entry.id); app.reminders.reconcile(); manager.cancel(entry.id, 0) }
    }
}
