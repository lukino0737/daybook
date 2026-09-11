package dev.lukino.daybook

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class ReminderUiTest {
    @get:Rule(order = 0) val returningUser = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun notificationOpensMatchingEntryAndReminderDraftSurvivesRecreation() {
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        org.junit.Assume.assumeTrue(app.reminders.notificationsEnabled() && app.reminders.exactEnabled())
        val entry = Entry(kind = EntryKind.TASK, title = "通知打开测试", reminderAt = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0).toString())
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            runBlocking { app.repository.save(entry); app.reminders.reconcile() }
            manager.activeNotifications.single { it.tag == entry.id }.notification.contentIntent.send()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("editor-root").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("title").assertTextContains(entry.title)
            compose.onNodeWithTag("reminder-toggle").performScrollTo().assertIsOn()
            compose.onNodeWithTag("reminder-toggle").performClick()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("reminder-toggle").performScrollTo().assertIsOff()
            compose.onNodeWithTag("save").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("editor-root").fetchSemanticsNodes().isEmpty() }
            assertNull(runBlocking { app.repository.all().first { it.id == entry.id }.reminderAt })
            runBlocking { app.reminders.reconcile() }
            assertFalse(manager.activeNotifications.any { it.tag == entry.id })
        } finally { runBlocking { app.repository.delete(entry.id); app.reminders.reconcile() } }
    }
}
