package dev.lukino.daybook

import android.content.Context
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

class TaskCompletionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun completingOneRowKeepsAnotherEnabledAndIgnoresDuplicateTap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("local-row", vm) }
        val first = Entry(kind = EntryKind.TASK, title = "第一条")
        val second = Entry(kind = EntryKind.TASK, title = "第二条")
        try {
            runBlocking { repo.save(first); repo.save(second) }
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithTag("nav-tasks").performClick()
            compose.waitUntil(5000) { vm.entries.value.size == 2 }
            compose.mainClock.autoAdvance = false
            compose.runOnIdle {
                vm.toggle(first); vm.toggle(first)
                assertEquals(setOf(first.id), vm.toggling.value)
                assertFalse(vm.busy.value)
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("entry-${second.id}").assertIsEnabled()
            compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("entry-${second.id}"))).assertIsEnabled()
            compose.runOnIdle { vm.toggle(second) }
            compose.waitUntil(5000) { vm.toggling.value.isEmpty() }
            runBlocking { assertTrue(repo.all().all { it.completed }) }
        } finally { compose.mainClock.autoAdvance = true; compose.runOnIdle { store.clear() }; db.close() }
    }
    @Test fun staleCompletionNeverOverwritesAnEditOrRecreatesDeletedTask() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        try {
            val repo = EntryRepository(db)
            val original = Entry(kind = EntryKind.TASK, title = "原任务")
            val changed = original.copy(title = "用户已改", updatedAt = original.updatedAt + 1)
            repo.save(changed)
            try { repo.toggleTask(original); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(listOf(changed), repo.all())
            repo.delete(changed.id)
            try { repo.toggleTask(changed); fail() } catch (_: IllegalArgumentException) {}
            assertTrue(repo.all().isEmpty())
        } finally { db.close() }
    }
}
