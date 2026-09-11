package dev.lukino.daybook

import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.io.File

class ReviewUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun filtersDiaryByTagAndBodyThenEditsTags() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val store = ViewModelStore().apply { put("review", vm) }
        val love = Entry(kind = EntryKind.NOTE, title = "河边散步", note = "一起看落日，路边买了两杯奶茶。", date = "2026-09-10", tags = listOf("恋爱", "生活"))
        val old = Entry(kind = EntryKind.NOTE, title = "周末电影", note = "记住平凡的小事。", date = "2026-09-05", tags = listOf("恋爱"))
        val study = Entry(kind = EntryKind.NOTE, title = "学习笔记", note = "数据库迁移", date = "2026-09-09", tags = listOf("学习"))
        try {
            runBlocking { listOf(love, old, study).forEach { repo.save(it) } }
            compose.setContent { DaybookTheme { DaybookScreen(vm) } }
            compose.onNodeWithText("回顾", useUnmergedTree = true).performClick()
            compose.onNodeWithText("回顾 · 3 条").assertDoesNotExist()
            compose.onNodeWithTag("apply-review").performClick()
            compose.onNodeWithTag("review-confirm-all").assertExists()
            compose.onNodeWithText("取消").performClick()
            compose.onNodeWithTag("filter-kind-EVENT").assertExists()
            compose.onNodeWithTag("filter-kind-TASK").assertExists()
            compose.onNodeWithTag("filter-kind-NOTE").assertExists()
            compose.onNodeWithTag("filter-tag-恋爱").performClick()
            compose.onNodeWithTag("apply-review").performClick()
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("河边散步"))
            compose.onNodeWithText("河边散步").assertIsDisplayed()
            compose.onNodeWithText("学习笔记").assertDoesNotExist()
            val dir = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
            File(dir, "review.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
            compose.onNodeWithTag("review-query").performTextInput("落日")
            compose.onNodeWithText("回顾 · 2 条").assertExists()
            compose.onNodeWithTag("apply-review").performScrollTo().performClick()
            compose.onNodeWithText("回顾 · 1 条").assertExists()
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("河边散步"))
            // Tap the title itself: the merged card center can fall on an interactive tag at large font sizes.
            compose.onNodeWithText("河边散步", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("tags").performScrollTo().performTextReplacement("恋爱，散步，恋爱")
            compose.onNodeWithTag("save").performClick()
            compose.waitUntil(5000) { vm.draft.value == null && !vm.busy.value }
            assertEquals(listOf("恋爱", "散步"), runBlocking { repo.all().first { it.id == love.id }.tags })
            compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
            compose.onNodeWithText("回顾", useUnmergedTree = true).performClick()
            compose.onNodeWithTag("review-query").assertTextContains("落日")
            compose.onNodeWithTag("review-query").performTextReplacement("找不到的正文")
            compose.onNodeWithTag("apply-review").performScrollTo().performClick()
            compose.onNodeWithText("回顾 · 0 条").assertExists()
        } finally { store.clear(); db.close() }
    }
}
