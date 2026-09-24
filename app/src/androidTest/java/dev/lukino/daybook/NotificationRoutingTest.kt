package dev.lukino.daybook

import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDateTime

class NotificationRoutingTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<DaybookApplication>()
    private fun intent(host: String, id: String) = Intent(app, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(Uri.parse("daybook://$host/$id"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }
    private fun preview() { compose.waitUntil(5000) { compose.onAllNodesWithTag("detail-edit").fetchSemanticsNodes().isNotEmpty() }; compose.onNodeWithTag("detail-edit").assertIsEnabled() }
    @Test fun launchIntentRecreationAndWarmIntentPreserveReadOnlyNavigation() {
        val entry = Entry(kind = EntryKind.TASK, title = "通知入口样例", note = "查看后再编辑")
        val memo = Memo(body = "另一个通知的便签")
        runBlocking { app.repository.save(entry); app.repository.saveMemo(memo) }
        try {
            ActivityScenario.launch<MainActivity>(intent("entry", entry.id)).use { scenario ->
                preview(); compose.onNodeWithTag("title").assertDoesNotExist()
                scenario.recreate(); preview()
                compose.onNodeWithTag("detail-edit").performClick()
                compose.onNodeWithTag("title").performTextReplacement("重建后保留的草稿")
                scenario.recreate()
                compose.onNodeWithTag("title").assertTextContains("重建后保留的草稿")
                scenario.onActivity { it.startActivity(intent("memo", memo.id)) }
                compose.onNodeWithTag("title").assertTextContains("重建后保留的草稿")
                compose.onNodeWithText("取消").performClick()
                preview(); compose.onNodeWithText(memo.body).assertExists()
                compose.onNodeWithTag("memo-editor").assertDoesNotExist()
                scenario.recreate(); preview(); compose.onNodeWithText(memo.body).assertExists()
                compose.onNodeWithTag("detail-back").performClick()
                scenario.recreate()
                compose.onNodeWithTag("notification-detail").assertDoesNotExist()
            }
            assertEquals(entry, runBlocking { app.repository.all().single { it.id == entry.id } })
        } finally { runBlocking { app.repository.delete(entry.id); app.repository.deleteMemo(memo.id) } }
    }
    @Test fun deliveredNotificationPendingIntentOpensPreviewFromBackground() {
        shell("pm grant dev.lukino.daybook android.permission.POST_NOTIFICATIONS")
        shell("appops set dev.lukino.daybook SCHEDULE_EXACT_ALARM allow")
        val entry = Entry(kind = EntryKind.TASK, title = "真实通知跳转样例", reminderAt = LocalDateTime.now().minusMinutes(1).withSecond(0).withNano(0).toString())
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                runBlocking { app.repository.save(entry); app.reminders.reconcile() }
                compose.waitUntil(5000) { manager.activeNotifications.any { it.tag == entry.id } }
                val notification = manager.activeNotifications.single { it.tag == entry.id }.notification
                scenario.moveToState(Lifecycle.State.CREATED)
                val options = ActivityOptions.makeBasic()
                if (android.os.Build.VERSION.SDK_INT >= 34) options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                notification.contentIntent.send(app, 0, null, null, null, null, options.toBundle())
                preview(); compose.onNodeWithTag("title").assertDoesNotExist()
                compose.onNode(hasText(entry.title) and hasAnyAncestor(hasTestTag("notification-detail"))).assertExists()
                compose.onNodeWithTag("detail-edit").performClick()
                compose.onNodeWithTag("title").assertTextContains(entry.title)
                compose.onNodeWithText("取消").performClick(); preview()
            }
        } finally { runBlocking { app.repository.delete(entry.id); app.reminders.reconcile() }; manager.cancel(entry.id, 0) }
    }
}
