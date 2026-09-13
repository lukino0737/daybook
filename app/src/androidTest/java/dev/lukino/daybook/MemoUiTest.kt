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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MemoUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun quickMemoSavesOnBackWithNoDateAndNeverAppearsInCalendar() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("memo", vm) }
        try {
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithTag("nav-memos").performClick()
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("memo-body").performTextInput("一个随手想到的点子\n以后再细想")
            compose.onNodeWithTag("reminder-toggle").performScrollTo().performClick()
            compose.onNodeWithTag("memo-back").performClick()
            compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
            val memo = runBlocking { repo.allMemos().single() }
            assertEquals("一个随手想到的点子", memo.summary)
            assertNull(memo.date); assertNotNull(memo.reminderAt)
            assertTrue(runBlocking { repo.all().isEmpty() })
            compose.onNodeWithTag("nav-day").performClick()
            compose.onNodeWithText(memo.summary).assertDoesNotExist()
            compose.onNodeWithTag("nav-memos").performClick()
            compose.onNodeWithTag("memo-${memo.id}").performClick()
            compose.onNodeWithTag("memo-body").assertTextContains(memo.body)
            compose.onNodeWithTag("memo-back").performClick()
            compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("memo-back").performClick()
            compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
            assertEquals(1, runBlocking { repo.allMemos().size })
        } finally { store.clear(); db.close() }
    }
}
