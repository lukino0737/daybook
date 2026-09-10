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

class TaskListsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun countsMatchTaskScopesAndCreatingFromUndatedDefaultsToUndatedTask() {
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
            compose.onNodeWithText("无截止 2").performClick()
            compose.onNodeWithText("待完成 2").assertExists()
            compose.onNodeWithText("未设截止日期的任务").assertExists()
            compose.onNodeWithText("日历里的安排").assertDoesNotExist()
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${undated.id}"))
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${undated.id}"))).performClick()
            compose.waitUntil(5000) { !vm.busy.value && vm.entries.value.first { it.id == undated.id }.completed }
            compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
            compose.onNodeWithText("待完成 1").assertExists()
            compose.onNodeWithText("无截止 2").assertExists()
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("title").performTextInput("新的无截止任务")
            compose.onNodeWithText("未设截止日期").assertExists()
            compose.onNodeWithTag("save").performClick()
            compose.waitUntil(5000) { vm.draft.value == null && !vm.busy.value }
            compose.onNodeWithText("无截止 3").assertExists()
            compose.onNodeWithText("待完成 2").assertExists()
        } finally { store.clear(); db.close() }
    }
}
