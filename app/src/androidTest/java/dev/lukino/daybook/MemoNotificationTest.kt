package dev.lukino.daybook

import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.Memo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

class MemoNotificationTest {
    @get:Rule(order = 0) val returningUser = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun memoNotificationOpensMemoAndRecreationKeepsDraft() {
        val app = compose.activity.application as DaybookApplication
        fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).readBytes()
        }
        shell("pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS")
        shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow")
        val memo = Memo(body = "便签通知专项", reminderAt = LocalDateTime.now().minusMinutes(2).withSecond(0).withNano(0).toString())
        try {
            runBlocking { app.repository.saveMemo(memo); app.reminders.reconcile() }
            val manager = app.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.single { it.tag == "memo:${memo.id}" }.notification
            assertTrue(notification.extras.getCharSequence("android.text")?.contains("补发提醒") == true)
            notification.contentIntent.send()
            compose.onNodeWithTag("memo-body").assertTextContains(memo.body)
            compose.onNodeWithTag("memo-body").performTextReplacement("便签通知专项 · 继续编辑")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("memo-body").assertTextContains("便签通知专项 · 继续编辑")
            compose.onNodeWithTag("memo-back").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("memo-editor").fetchSemanticsNodes().isEmpty() }
            assertEquals(memo.reminderAt, runBlocking { app.repository.allMemos().single { it.id == memo.id }.reminderDeliveredFor })
        } finally { runBlocking { app.repository.deleteMemo(memo.id); app.reminders.reconcile() } }
    }
}
