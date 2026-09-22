package dev.lukino.daybook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.backup.BackupService
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TaskListsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun taskDefaultsAcrossCalendarAndReminderWhileExistingDeadlineIsPreserved() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("defaults", vm) }
        try {
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithText("□ 任务").performClick()
            compose.onNodeWithText("不设截止日期").assertExists()
            compose.onNodeWithText("具体时间未定").assertIsNotEnabled()
            compose.onNodeWithTag("title").performTextInput("默认不设截止")
            compose.onNodeWithTag("save").performClick()
            compose.waitUntil(5000) { vm.draft.value == null && !vm.busy.value }
            val task = runBlocking { repo.all().single() }
            assertNull(task.date); assertNull(task.time)
            // Simulate an explicitly chosen deadline; editing or tapping the selected kind must retain it.
            val dated = task.copy(date = "2099-12-31", time = "18:30")
            runBlocking { repo.save(dated) }
            compose.runOnIdle { vm.edit(dated) }
            compose.onNodeWithText("□ 任务").performClick()
            compose.onNodeWithText("2099-12-31").assertExists()
            compose.onNodeWithText("18:30").assertExists()
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { vm.newReminder() }
            compose.waitUntil(5000) { vm.standaloneEditor.draft.value != null }
            compose.onNodeWithTag("standalone-kind-TASK").performClick()
            compose.onNodeWithText("不设截止日期").assertExists()
            compose.onNodeWithText("具体时间未定").assertIsNotEnabled()
            compose.onNodeWithText("取消").performClick()
            // An explicitly set deadline survives a task -> reminder -> task round trip.
            compose.runOnIdle { vm.setDraft(Draft(kind = EntryKind.TASK, date = "2099-12-30", time = "12:00")) }
            compose.onNodeWithTag("new-reminder-kind").performClick()
            compose.onNodeWithTag("standalone-kind-TASK").performClick()
            compose.onNodeWithText("2099-12-30").assertExists()
            compose.onNodeWithText("12:00").assertExists()
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
    @Test fun tasksArePartitionedAndCountsUpdateWhenCompleted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("tasks", vm) }
        val undated = Entry(kind = EntryKind.TASK, title = "找时间读书")
        try {
            runBlocking {
                listOf(undated, Entry(kind = EntryKind.TASK, title = "已完成无截止", completed = true),
                    Entry(kind = EntryKind.TASK, title = "提交材料", date = "2026-09-10"),
                    Entry(title = "日历里的安排", date = "2026-09-10")).forEach { repo.save(it) }
            }
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithTag("nav-tasks").performClick()
            compose.onNodeWithText("任务 2").assertExists()
            compose.onNodeWithText("有截止日期").assertExists()
            compose.onNodeWithText("无截止日期").assertExists()
            compose.onNodeWithText("日历里的安排").assertDoesNotExist()
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${undated.id}"))
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${undated.id}"))).performClick()
            compose.waitUntil(5000) { !vm.busy.value && vm.entries.value.first { it.id == undated.id }.completed }
            compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
            compose.onNodeWithText("任务 1").assertExists()
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("title").performTextInput("新的无截止任务")
            compose.onNodeWithTag("clear-date").assertDoesNotExist()
            compose.onNodeWithText("不设截止日期").assertExists()
            compose.onNodeWithTag("save").performClick()
            compose.waitUntil(5000) { vm.draft.value == null && !vm.busy.value }
            compose.onNodeWithText("任务 2").assertExists()
        } finally { store.clear(); db.close() }
    }
}
