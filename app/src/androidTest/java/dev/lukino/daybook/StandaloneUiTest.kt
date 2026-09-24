package dev.lukino.daybook

import android.graphics.Bitmap
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
import java.io.File
import java.time.*

class StandaloneUiTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DaybookViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db)
        vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        store.put("standalone", vm)
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    private fun screen() { compose.setContent { DaybookTheme { DaybookScreen(vm) } } }
    private fun closed() { compose.waitUntil(5000) { vm.standaloneEditor.draft.value == null && !vm.standaloneEditor.busy.value } }
    private fun list() { compose.onNodeWithTag("backup-menu").performClick(); compose.onNodeWithTag("reminder-list-menu").performClick() }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("v06Preview") != "true") return
        compose.waitForIdle()
        android.os.SystemClock.sleep(450) // Native dialog/window transitions are outside the Compose test clock.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = File(ApplicationProvider.getApplicationContext<android.content.Context>().getExternalFilesDir(null), "v06-previews").apply { mkdirs() }
        val suffix = InstrumentationRegistry.getArguments().getString("previewSuffix") ?: "normal"
        File(folder, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun newEntrySwitchPreservesTextAndManualCalendarChoiceSurvivesRuleChanges() {
        screen()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("title").performTextInput("朋友的生日")
        compose.onNodeWithTag("note").performScrollTo().performTextInput("记得送上祝福")
        compose.onNodeWithTag("new-reminder-kind").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-title").assertTextContains("朋友的生日")
        compose.onNodeWithTag("standalone-note").performScrollTo().assertTextContains("记得送上祝福")
        compose.onNodeWithTag("standalone-kind-TASK").performScrollTo().performClick()
        compose.onNodeWithTag("title").assertTextContains("朋友的生日")
        compose.onNodeWithTag("new-reminder-kind").performScrollTo().performClick()
        compose.onNodeWithTag("repeat-YEARLY").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-calendar").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("repeat-DAILY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat-YEARLY").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-calendar").performScrollTo().assertIsOff()
        compose.onNodeWithTag("standalone-calendar").performClick()
        capture("editor-options")
        compose.onNodeWithTag("standalone-title").performScrollTo(); capture("editor-top")
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        val saved = runBlocking { repo.allReminders().single() }
        assertEquals("朋友的生日", saved.title); assertTrue(saved.showInCalendar)
        assertEquals(RepeatKind.YEARLY, saved.repeat)
        assertTrue(runBlocking { repo.all().isEmpty() && repo.allMemos().isEmpty() })
        list(); capture("list")
        compose.onNodeWithTag("reminder-row-reminder:${saved.id}").assertIsDisplayed()
        compose.onNodeWithTag("reminder-list-add").performClick()
        compose.onNodeWithTag("standalone-title").performTextInput("取消的新提醒")
        compose.onNodeWithTag("standalone-cancel").performClick(); closed()
        assertEquals(1, runBlocking { repo.allReminders().size })
    }
    @Test fun calendarShowsPausedOccurrencesAndHidesWithoutChangingTaskCounts() {
        val today = LocalDate.now()
        val r = StandaloneReminder(title = "每月整理照片", startDate = today.toString(), time = "09:00", repeat = RepeatKind.MONTHLY, enabled = false, showInCalendar = true)
        runBlocking { repo.saveReminder(r) }; screen()
        compose.onNodeWithTag("marker-$today", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("day-reminder-${r.id}"))
        compose.onNodeWithTag("day-reminder-${r.id}").assertIsDisplayed(); capture("calendar")
        compose.onNodeWithTag("day-reminder-${r.id}").performClick()
        compose.onNodeWithTag("standalone-enabled").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        assertTrue(runBlocking { repo.allReminders().single().enabled })
        compose.onNodeWithTag("day-reminder-${r.id}").performClick()
        compose.onNodeWithTag("standalone-calendar").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        compose.onNodeWithTag("day-reminder-${r.id}").assertDoesNotExist()
        compose.onNodeWithTag("marker-$today", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("nav-tasks").performClick()
        compose.onNodeWithText("任务 0").assertExists()
        assertTrue(runBlocking { repo.all().isEmpty() })
        list()
        compose.onNodeWithTag("reminder-row-reminder:${r.id}").performClick()
        compose.onNodeWithTag("standalone-enabled").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        compose.onNodeWithTag("reminder-row-reminder:${r.id}").assertDoesNotExist()
        compose.onNodeWithTag("reminder-group-PAUSED").performClick()
        compose.onNodeWithTag("reminder-row-reminder:${r.id}").assertExists()
    }
    @Test fun saveAndDeleteFailuresPreserveContentAndDeletionRequiresConfirmation() {
        screen(); list(); compose.onNodeWithTag("reminder-list-add").performClick()
        compose.onNodeWithTag("standalone-title").performTextInput("每周检查清单")
        compose.onNodeWithTag("repeat-WEEKLY").performScrollTo().performClick()
        compose.runOnIdle { vm.standaloneEditor.current()!!.let { vm.standaloneEditor.change(it.copy(weekdays = 0)) } }
        compose.onNodeWithTag("standalone-save").performClick()
        compose.waitUntil(5000) { vm.standaloneEditor.error.value != null }
        compose.onNodeWithTag("standalone-error").assertTextContains("星期", substring = true)
        compose.onNodeWithTag("weekday-1").performScrollTo().performClick()
        compose.onNodeWithTag("weekday-3").performScrollTo().performClick()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reminder_save BEFORE INSERT ON reminders BEGIN SELECT RAISE(ABORT, 'test save failed'); END")
        compose.onNodeWithTag("standalone-save").performClick()
        compose.waitUntil(5000) { vm.standaloneEditor.error.value != null && !vm.standaloneEditor.busy.value }
        compose.onNodeWithTag("standalone-title").performScrollTo().assertTextContains("每周检查清单"); capture("save-error")
        assertTrue(runBlocking { repo.allReminders().isEmpty() })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reminder_save")
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        val r = runBlocking { repo.allReminders().single() }
        assertEquals(5, r.weekdays)
        compose.onNodeWithTag("reminder-row-reminder:${r.id}").performClick()
        compose.onNodeWithTag("standalone-delete").performScrollTo().performClick(); capture("delete-confirm")
        compose.onNodeWithTag("standalone-cancel-delete").performClick()
        assertEquals(1, runBlocking { repo.allReminders().size })
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reminder_delete BEFORE DELETE ON reminders BEGIN SELECT RAISE(ABORT, 'test delete failed'); END")
        compose.onNodeWithTag("standalone-delete").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-confirm-delete").performClick()
        compose.waitUntil(5000) { vm.standaloneEditor.error.value != null && !vm.standaloneEditor.busy.value }
        assertEquals(1, runBlocking { repo.allReminders().size })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_reminder_delete")
        compose.onNodeWithTag("standalone-delete").performScrollTo().performClick()
        compose.onNodeWithTag("standalone-confirm-delete").performClick(); closed()
        assertTrue(runBlocking { repo.allReminders().isEmpty() })
    }
    @Test fun unifiedListOpensOriginalSourcesAndNotificationWaitsForDraft() {
        val future = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0).toString()
        val entry = Entry(kind = EntryKind.TASK, title = "整理材料", reminderAt = future)
        val memo = Memo(body = "周末散步的想法", reminderAt = future)
        val r = StandaloneReminder(title = "喝水提醒", startDate = LocalDate.now().toString(), time = "09:00", repeat = RepeatKind.DAILY)
        runBlocking { repo.save(entry); repo.saveMemo(memo); repo.saveReminder(r) }
        screen(); list()
        compose.onNodeWithTag("reminder-row-${entry.id}").performClick()
        compose.onNodeWithTag("title").assertTextContains(entry.title)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("reminder-row-memo:${memo.id}").performClick()
        compose.onNodeWithTag("memo-body").assertTextContains(memo.body)
        compose.onNodeWithTag("memo-done").performClick()
        compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
        compose.onNodeWithTag("reminder-list-add").performClick()
        compose.onNodeWithTag("standalone-title").performTextInput("保留当前草稿")
        compose.runOnIdle { vm.notification("reminder", r.id) }
        compose.onNodeWithTag("standalone-title").assertTextContains("保留当前草稿")
        compose.onNodeWithTag("standalone-save").performClick()
        compose.waitUntil(5000) { vm.notificationDetail.value?.reminder?.id == r.id }
        compose.onNodeWithTag("detail-edit").performClick()
        compose.waitUntil(5000) { vm.standaloneEditor.draft.value?.id == r.id }
        compose.onNodeWithTag("standalone-title").assertTextContains(r.title)
        assertTrue(runBlocking { repo.allReminders().any { it.title == "保留当前草稿" } })
        compose.onNodeWithTag("standalone-cancel").performClick(); closed()
        runBlocking { repo.deleteReminder(r.id) }
        compose.runOnIdle { vm.notification("reminder", r.id) }
        compose.onNodeWithTag("detail-missing").assertTextContains("已删除", substring = true)
    }
    @Test fun intervalInputRoundTripsAndCalendarUsesEachViewedMonth() {
        screen(); list(); compose.onNodeWithTag("reminder-list-add").performClick()
        compose.onNodeWithTag("standalone-title").performTextInput("每三天整理一次")
        compose.onNodeWithTag("repeat-INTERVAL").performScrollTo().performClick()
        compose.onNodeWithTag("interval-days").performScrollTo().performTextReplacement("3")
        compose.onNodeWithTag("repeat-DAILY").performScrollTo().performClick()
        compose.onNodeWithTag("repeat-INTERVAL").performScrollTo().performClick()
        compose.onNodeWithTag("interval-days").performScrollTo().assertTextContains("1").performTextReplacement("3")
        compose.onNodeWithTag("standalone-save").performClick(); closed()
        assertEquals(3, runBlocking { repo.allReminders().single().intervalDays })
        compose.onNodeWithTag("reminder-list-back").performClick()
        val leap = StandaloneReminder(title = "闰日生日", startDate = "2024-02-29", time = "09:00", repeat = RepeatKind.YEARLY, enabled = false, showInCalendar = true)
        val monthEnd = StandaloneReminder(title = "月末整理", startDate = "2024-01-31", time = "09:00", repeat = RepeatKind.MONTHLY, enabled = false, showInCalendar = true)
        runBlocking { repo.saveReminder(leap); repo.saveReminder(monthEnd) }
        compose.runOnIdle { vm.setMonth("2028-02"); vm.select(LocalDate.parse("2028-02-29")) }
        compose.onNodeWithTag("marker-2028-02-29", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("day-reminder-${leap.id}"))
        compose.onNodeWithTag("day-reminder-${leap.id}").assertExists()
        compose.onNodeWithTag("day-reminder-${monthEnd.id}").assertDoesNotExist()
        compose.runOnIdle { vm.setMonth("2029-02"); vm.select(LocalDate.parse("2029-02-28")) }
        compose.onNodeWithTag("day-reminder-${leap.id}").assertDoesNotExist()
        compose.runOnIdle { vm.setMonth("2029-03"); vm.select(LocalDate.parse("2029-03-31")) }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("day-reminder-${monthEnd.id}"))
        compose.onNodeWithTag("day-reminder-${monthEnd.id}").assertExists()
        assertEquals(3, runBlocking { repo.allReminders().size })
    }

}
