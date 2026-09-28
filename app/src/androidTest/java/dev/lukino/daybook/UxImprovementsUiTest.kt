package dev.lukino.daybook

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.backup.BackupService
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.io.File

class UxImprovementsUiTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DaybookViewModel
    private val models = ViewModelStore()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db)
        vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo)); models.put("ux", vm)
    }
    @After fun cleanup() { compose.runOnIdle { models.clear() }; db.close() }
    private fun screen() { compose.setContent { DaybookTheme { DaybookScreen(vm) } } }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uxPreview") != "true") return
        compose.waitForIdle(); android.os.SystemClock.sleep(300)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.getExternalFilesDir(null), "ux-previews").apply { mkdirs() }
        val suffix = InstrumentationRegistry.getArguments().getString("previewSuffix") ?: "normal"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(folder, "$name-$suffix.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun emptyDayHasNoSectionButExistingEventsAndCalendarRemindersRemain() {
        val today = LocalDate.now()
        screen(); compose.runOnIdle { vm.select(today) }
        compose.onNodeWithText("这一天，留给你慢慢写。").assertDoesNotExist()
        compose.onNodeWithText(today.toString()).assertDoesNotExist()
        compose.onNodeWithTag("add").assertExists(); capture("empty-day")
        val event = Entry(title = "当天日程", date = today.toString())
        runBlocking { repo.save(event) }
        compose.waitUntil(5000) { vm.entries.value.size == 1 }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${event.id}"))
        compose.onNodeWithText(today.toString()).assertExists()
        runBlocking { repo.delete(event.id) }
        val reminder = StandaloneReminder(title = "当天独立提醒", startDate = today.toString(), time = "23:58", repeat = RepeatKind.DAILY, showInCalendar = true)
        runBlocking { repo.saveReminder(reminder) }
        compose.waitUntil(5000) { vm.reminders.value.size == 1 && vm.entries.value.isEmpty() }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("当天独立提醒"))
        compose.onNodeWithText(today.toString()).assertExists()
    }
    @Test fun swipeRequiresConfirmationAndPreservesAttachedContent() {
        val e = Entry(kind = EntryKind.TASK, title = "保留任务正文", note = "原有文字", reminderAt = LocalDate.now().plusDays(1).atTime(12, 0).toString())
        runBlocking { repo.save(e) }; screen()
        compose.waitUntil(5000) { vm.entries.value.size == 1 }
        compose.runOnIdle { vm.showReminderList() }
        compose.onNodeWithTag("swipe-${e.id}").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("delete-${e.id}").performClick()
        compose.onNodeWithTag("reminder-delete-dialog").assertExists(); capture("reminder-delete")
        assertEquals(listOf(e), runBlocking { repo.all() })
        compose.onNodeWithText("返回").performClick()
        assertEquals(listOf(e), runBlocking { repo.all() })
        compose.onNodeWithTag("delete-${e.id}").performClick()
        compose.onNodeWithTag("reminder-delete-confirm").performClick()
        compose.waitUntil(5000) { vm.entries.value.singleOrNull()?.reminderAt == null }
        val actual = runBlocking { repo.all().single() }
        assertEquals(e.note, actual.note); assertEquals(e.title, actual.title)
        compose.onNodeWithTag("reminder-row-${e.id}").assertDoesNotExist()
    }
    @Test fun centeredPreviewKeepsControlsVisibleWhileLongContentScrolls() {
        val e = Entry(kind = EntryKind.TASK, title = "卡片预览样例", note = (1..40).joinToString("\n") { "第${it}行：连续阅读，图文内容显示在卡片内。" })
        runBlocking { repo.save(e) }; screen()
        compose.runOnIdle { vm.notification("entry", e.id) }
        compose.waitUntil(5000) { vm.notificationDetail.value?.entry != null }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("detail-blurred-background").fetchSemanticsNodes().isNotEmpty() }
        val root = compose.onNodeWithTag("notification-detail").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("detail-card").fetchSemanticsNode().boundsInRoot
        assertTrue(card.width < root.width && card.height < root.height)
        assertEquals(root.center.x, card.center.x, 2f)
        compose.onNodeWithTag("detail-back").assertIsDisplayed()
        compose.onNodeWithTag("detail-edit").assertIsDisplayed(); capture("long-preview")
        compose.onNodeWithTag("detail-content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("detail-edit").assertIsDisplayed()
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("notification-detail").assertDoesNotExist()
    }
}
