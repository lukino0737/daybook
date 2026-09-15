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
import java.time.LocalDate

class InteractionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DaybookViewModel
    private val store = ViewModelStore()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db)
        vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        store.put("interaction", vm)
    }
    @After fun cleanup() { compose.runOnIdle { store.clear() }; db.close() }
    private fun screen() { compose.setContent { DaybookTheme { DaybookScreen(vm) } } }
    private fun waitClosed() { compose.waitUntil(5000) { vm.memoEditor.draft.value == null && !vm.memoEditor.busy.value } }
    private fun scroll(tag: String) { compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag(tag)) }
    private fun swipe(id: String) {
        scroll("swipe-$id")
        compose.onNodeWithTag("swipe-$id").performScrollTo()
        compose.waitForIdle()
        // The calendar FAB can cover the far right edge: swipe the exposed card body.
        compose.onNodeWithTag("swipe-$id").performTouchInput { swipeLeft(startX = centerX) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("delete-$id").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun completedTasksCanReturnAndAllTasksSupportConfirmedSwipeDelete() {
        val task = Entry(kind = EntryKind.TASK, title = "完成后再恢复")
        val done = Entry(kind = EntryKind.TASK, title = "已有完成任务", completed = true)
        runBlocking { repo.save(task); repo.save(done) }
        screen(); compose.onNodeWithTag("nav-tasks").performClick()
        compose.onNodeWithText(done.title).assertDoesNotExist()
        scroll("entry-${task.id}")
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${task.id}"))).performClick()
        compose.waitUntil(5000) { !vm.busy.value && vm.completing.value.isEmpty() && vm.entries.value.first { it.id == task.id }.completed }
        scroll("completed-heading"); compose.onNodeWithTag("completed-heading").performClick()
        scroll("entry-${task.id}")
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${task.id}"))).assertIsOn().performClick()
        compose.waitUntil(5000) { !vm.busy.value && !vm.entries.value.first { it.id == task.id }.completed }
        swipe(task.id)
        compose.onNodeWithTag("delete-${task.id}").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(2, runBlocking { repo.all().size })
        compose.onNodeWithTag("delete-${task.id}").performClick()
        compose.onNodeWithTag("confirm-row-delete").performClick()
        compose.waitUntil(5000) { !vm.busy.value && vm.entries.value.none { it.id == task.id } }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5000) { vm.entries.value.any { it.id == task.id } }
        swipe(done.id); compose.onNodeWithTag("delete-${done.id}").performClick()
        compose.onNodeWithTag("confirm-row-delete").performClick()
        compose.waitUntil(5000) { vm.entries.value.none { it.id == done.id } }
    }

    @Test fun onlyOneSwipeOpensAndCompletionFailurePreservesTask() {
        val first = Entry(kind = EntryKind.TASK, title = "第一项")
        val second = Entry(kind = EntryKind.TASK, title = "第二项")
        runBlocking { repo.save(first); repo.save(second) }; screen()
        compose.onNodeWithTag("nav-tasks").performClick()
        swipe(first.id); compose.onNodeWithTag("delete-${first.id}").assertIsDisplayed()
        swipe(second.id); compose.onNodeWithTag("delete-${first.id}").assertDoesNotExist()
        compose.onNodeWithTag("delete-${second.id}").assertIsDisplayed()
        compose.onNodeWithTag("swipe-${second.id}").performTouchInput { swipeRight() }
        compose.onNodeWithTag("delete-${second.id}").assertDoesNotExist()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_complete BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'save failed'); END")
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${second.id}"))).performClick()
        compose.waitUntil(5000) { vm.failure.value != null && !vm.busy.value }
        assertFalse(runBlocking { repo.all().first { it.id == second.id }.completed })
        assertTrue(vm.completing.value.isEmpty())
        compose.onNodeWithText("知道了").performClick()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_complete")
    }

    @Test fun calendarAllKindsDeleteAndPastDefaultNeverChangesExistingKinds() {
        val past = LocalDate.now().minusDays(1)
        val samples = EntryKind.entries.map { Entry(kind = it, title = "日历${it.label}", date = past.toString()) }
        runBlocking { samples.forEach { repo.save(it) } }
        compose.runOnIdle { vm.select(past); vm.edit() }
        compose.waitUntil { vm.draft.value != null }
        assertEquals(EntryKind.NOTE, vm.draft.value!!.kind)
        compose.runOnIdle { vm.dismissDraft(); vm.edit(samples.first { it.kind == EntryKind.EVENT }) }
        compose.waitUntil { vm.draft.value?.kind == EntryKind.EVENT }
        compose.runOnIdle { vm.dismissDraft() }
        compose.waitUntil { vm.draft.value == null }
        // Check defaults before mounting the screen so a native editor window exit
        // animation cannot intercept the following independent swipe assertions.
        screen()
        samples.forEach {
            swipe(it.id)
            compose.onNodeWithTag("delete-${it.id}").performClick()
            compose.onNodeWithTag("confirm-row-delete").performClick()
            compose.waitUntil(5000) { !vm.busy.value && vm.entries.value.none { entry -> entry.id == it.id } }
        }
        assertTrue(runBlocking { repo.all().isEmpty() })
    }

    @Test fun memoNewBlankDoneAndClearAfterAutosaveCancelCreation() {
        screen(); compose.onNodeWithTag("nav-memos").performClick()
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("memo-done").performClick(); waitClosed()
        assertTrue(runBlocking { repo.allMemos().isEmpty() })
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("memo-body").performTextInput("临时输入")
        compose.waitUntil(5000) { vm.memoEditor.items.value.size == 1 }
        compose.onNodeWithTag("memo-body").performTextReplacement("   ")
        compose.onNodeWithTag("memo-done").performClick(); waitClosed()
        assertTrue(runBlocking { repo.allMemos().isEmpty() })
    }

    @Test fun existingMemoBlankCanKeepSavedOrExplicitlyDelete() {
        val memo = Memo(body = "应保留的原文", reminderAt = "2099-12-31T09:00")
        runBlocking { repo.saveMemo(memo) }; screen()
        compose.onNodeWithTag("nav-memos").performClick()
        compose.onNodeWithTag("memo-${memo.id}").performClick()
        compose.onNodeWithTag("memo-body").performTextClearance()
        compose.onNodeWithTag("memo-done").performClick()
        compose.onNodeWithText("继续编辑").assertDoesNotExist()
        compose.onNodeWithText("保留内容并退出").performClick(); waitClosed()
        assertEquals(memo, runBlocking { repo.allMemos().single() })
        compose.onNodeWithTag("memo-${memo.id}").performClick()
        compose.onNodeWithTag("memo-body").performTextClearance()
        compose.onNodeWithTag("memo-done").performClick()
        // The blank-exit dialog offers deletion, which still requires confirmation.
        compose.onNodeWithTag("blank-delete").performClick()
        compose.onNodeWithText("确认删除").performClick(); waitClosed()
        assertTrue(runBlocking { repo.allMemos().isEmpty() })
    }

    @Test fun memoSwipeAndDatabaseFailuresKeepDataAndEditorOpen() {
        val memo = Memo(body = "不会因失败丢失")
        runBlocking { repo.saveMemo(memo) }; screen()
        compose.onNodeWithTag("nav-memos").performClick()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_delete BEFORE DELETE ON memos BEGIN SELECT RAISE(ABORT, 'delete failed'); END")
        swipe("memo-${memo.id}")
        compose.onNodeWithTag("delete-memo-${memo.id}").performClick()
        compose.onNodeWithTag("confirm-row-delete").performClick()
        compose.waitUntil(5000) { vm.failure.value != null }
        assertEquals(memo, runBlocking { repo.allMemos().single() })
        compose.onNodeWithText("知道了").performClick()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
        compose.onNodeWithTag("memo-${memo.id}").performClick()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_save BEFORE INSERT ON memos BEGIN SELECT RAISE(ABORT, 'save failed'); END")
        compose.onNodeWithTag("memo-body").performTextReplacement("失败的修改")
        compose.onNodeWithTag("memo-done").performClick()
        compose.waitUntil(5000) { vm.memoEditor.error.value != null && !vm.memoEditor.busy.value }
        compose.onNodeWithTag("memo-editor").assertExists()
        assertEquals(memo, runBlocking { repo.allMemos().single() })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_save")
        compose.onNodeWithTag("memo-done").performClick(); waitClosed()
        assertEquals("失败的修改", runBlocking { repo.allMemos().single().body })
        swipe("memo-${memo.id}"); compose.onNodeWithTag("delete-memo-${memo.id}").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("delete-memo-${memo.id}").performClick()
        compose.onNodeWithTag("confirm-row-delete").performClick()
        compose.waitUntil(5000) { vm.memoEditor.items.value.isEmpty() && !vm.busy.value }
    }
}
