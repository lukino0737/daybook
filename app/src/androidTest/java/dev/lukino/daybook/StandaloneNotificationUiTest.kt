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
import java.time.*

class StandaloneNotificationUiTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun actualNotificationOpensEditorAndRecreationPreservesUnsavedDraft() {
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        assertTrue(app.reminders.notificationsEnabled() && app.reminders.exactEnabled())
        val due = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0)
        val r = StandaloneReminder(title = "DAYBOOK_V06_ROUTE", startDate = due.toLocalDate().toString(), time = due.toLocalTime().toString(), repeat = RepeatKind.DAILY)
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            runBlocking { app.repository.saveReminder(r, due.minusHours(1).atZone(ZoneId.systemDefault()).toInstant()); app.reminders.reconcile() }
            val notification = manager.activeNotifications.single { it.notification.extras.getString("daybook.source") == "reminder:${r.id}" }
            val target = notification.notification.contentIntent
            target.send()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("standalone-editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("standalone-title").assertTextContains(r.title).performTextReplacement("未保存的新标题")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("standalone-title").assertTextContains("未保存的新标题")
            compose.onNodeWithTag("standalone-cancel").performClick()
            assertEquals(r.title, runBlocking { app.repository.allReminders().single { it.id == r.id }.title })
            runBlocking { app.repository.deleteReminder(r.id); app.reminders.reconcile() }
            target.send()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("reminder-list-message").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reminder-list-message").assertTextContains("已删除", substring = true)
        } finally { runBlocking { app.repository.deleteReminder(r.id); app.reminders.reconcile() } }
    }
}
