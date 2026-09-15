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

/** Explicit visual QA, isolated fictional content, never user's app database. */
class V05PreviewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tasksSwipeAndMemoWithKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("daybookPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("preview", vm) }
        val dated = Entry(kind = EntryKind.TASK, title = "整理周末出游清单", date = "2099-09-20")
        val undated = Entry(kind = EntryKind.TASK, title = "找一部想看的电影")
        val done = Entry(kind = EntryKind.TASK, title = "读完书中的一章", completed = true)
        val memo = Memo(body = "下次散步，换一条没走过的路\n带上相机，看看傍晚的天空。")
        val folder = File(context.getExternalFilesDir(null), "v05-previews").apply { mkdirs() }
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(folder, "$name-${args.getString("previewSuffix") ?: "normal"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            runBlocking { listOf(dated, undated, done).forEach { repo.save(it) }; repo.saveMemo(memo) }
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithTag("nav-tasks").performClick()
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("completed-heading"))
            compose.onNodeWithTag("completed-heading").performClick()
            capture("tasks")
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("swipe-${undated.id}"))
            compose.onNodeWithTag("swipe-${undated.id}").performScrollTo().performTouchInput { swipeLeft() }
            compose.onNodeWithTag("delete-${undated.id}").assertIsDisplayed()
            capture("swipe")
            compose.onNodeWithTag("delete-${undated.id}").performClick(); capture("delete-confirm")
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("nav-memos").performClick()
            compose.onNodeWithTag("memo-${memo.id}").performClick(); capture("memo-editor")
            compose.onNodeWithTag("memo-delete").performScrollTo().assertIsDisplayed(); capture("memo-delete")
            compose.onNodeWithTag("memo-body").performScrollTo().performTextClearance()
            compose.onNodeWithTag("memo-done").performClick(); capture("memo-empty")
            compose.onNodeWithText("保留内容并退出").performClick()
            compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
            assertEquals(memo, runBlocking { repo.allMemos().single() })
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
}
