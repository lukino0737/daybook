package dev.lukino.daybook

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.backup.BackupService
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

class NotificationDetailUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DaybookViewModel
    private lateinit var dir: File
    private val models = ViewModelStore()
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.cacheDir, "detail-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db, BodyImageStore(isolated))
        vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(isolated, repo))
        models.put("detail", vm)
    }
    @After fun cleanup() { compose.runOnIdle { models.clear() }; db.close(); dir.deleteRecursively() }
    private fun detailText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("notification-detail")))
    private fun screen() { compose.setContent { DaybookTheme { DaybookScreen(vm) } } }
    private fun open(host: String, id: String) {
        compose.runOnIdle { vm.notification(host, id) }
        compose.waitUntil(5000) { vm.notificationDetail.value?.target == "$host/$id" }
    }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("detailPreview") != "true") return
        compose.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.getExternalFilesDir(null), "v071-previews").apply { mkdirs() }
        val suffix = InstrumentationRegistry.getArguments().getString("previewSuffix") ?: "normal"
        File(folder, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun entryPreviewIsReadOnlyAndEditReturnsToFreshContent() {
        val entry = Entry(kind = EntryKind.TASK, title = "准备周末出行", note = "先查看完整内容\n再决定是否修改", date = LocalDate.now().plusDays(1).toString())
        runBlocking { repo.save(entry) }; screen(); open("entry", entry.id)
        compose.onNodeWithTag("title").assertDoesNotExist()
        detailText(entry.note).assertExists(); capture("task-detail")
        assertEquals(entry, runBlocking { repo.all().single() })
        compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("title").performTextReplacement("改好的出行计划")
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5000) { vm.draft.value == null && vm.notificationDetail.value?.entry?.title == "改好的出行计划" }
        compose.onNodeWithTag("notification-detail").assertExists()
        detailText("改好的出行计划").assertExists()
        compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("title").performTextReplacement("不保存的改动")
        compose.onNodeWithText("取消").performClick()
        detailText("改好的出行计划").assertExists()
        runBlocking { repo.delete(entry.id) }
        compose.waitUntil(5000) { vm.notificationDetail.value?.exists == false }
        compose.onNodeWithTag("detail-missing").assertExists()
        compose.onNodeWithTag("detail-edit").assertIsNotEnabled()
        compose.onNodeWithTag("detail-back").performClick()
        compose.onNodeWithTag("notification-detail").assertDoesNotExist()
    }
    @Test fun memoImagesAreReadOnlyAndReuseZoomViewer() {
        val source = File(dir, "sample.png")
        Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(126, 165, 141))
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        val image = runBlocking { repo.importImage(Uri.fromFile(source), "fixture") }
        val blocks = listOf(BodyBlock("上半段正文"), BodyBlock(image = image), BodyBlock("下半段正文"))
        val memo = Memo(body = RichBody.text(blocks), blocks = blocks)
        runBlocking { repo.saveMemo(memo) }; repo.images!!.release("fixture")
        val before = runBlocking { repo.allMemos().single() }
        screen(); open("memo", memo.id)
        compose.onNodeWithTag("memo-editor").assertDoesNotExist()
        compose.onNodeWithTag("insert-image").assertDoesNotExist()
        detailText("上半段正文").assertExists()
        detailText("下半段正文").performScrollTo().assertIsDisplayed(); capture("memo-detail")
        compose.onNodeWithTag("detail-image-0").performScrollTo().performClick()
        compose.onNodeWithTag("image-viewer").assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("detail-back").performClick()
        assertEquals(before, runBlocking { repo.allMemos().single() })
        open("memo", memo.id); compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("memo-body").performTextReplacement("编辑后的便签")
        compose.onNodeWithTag("memo-done").performClick()
        compose.waitUntil(5000) { vm.memoEditor.draft.value == null && vm.notificationDetail.value?.memo?.body?.contains("编辑后的便签") == true }
        compose.onNodeWithTag("notification-detail").assertExists()
        assertEquals(listOf(image), RichBody.images(runBlocking { repo.allMemos().single().blocks }))
    }
    @Test fun reminderDetailsAndAllDraftTypesAreProtected() {
        val r = StandaloneReminder(title = "每周整理照片", note = "检查相册与备份", startDate = LocalDate.now().toString(), time = "09:00", repeat = RepeatKind.WEEKLY, weekdays = 5)
        runBlocking { repo.saveReminder(r) }; screen()
        compose.runOnIdle { vm.setDraft(Draft(title = "当前事项草稿", kind = EntryKind.TASK)); vm.notification("reminder", r.id) }
        compose.onNodeWithTag("title").assertTextContains("当前事项草稿")
        compose.onNodeWithTag("notification-detail").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5000) { vm.notificationDetail.value?.reminder?.id == r.id }
        detailText("每周一、三").assertExists(); capture("reminder-detail")
        compose.onNodeWithTag("detail-edit").performClick()
        compose.onNodeWithTag("standalone-title").performTextReplacement("修改后的提醒")
        compose.onNodeWithTag("standalone-save").performClick()
        compose.waitUntil(5000) { vm.standaloneEditor.draft.value == null && vm.notificationDetail.value?.reminder?.title == "修改后的提醒" }
        compose.onNodeWithTag("detail-back").performClick()
        val memo = Memo(body = "便签草稿需要保留")
        runBlocking { repo.saveMemo(memo) }
        compose.runOnIdle { vm.memoEditor.open(memo); vm.notification("reminder", r.id) }
        compose.onNodeWithTag("memo-body").assertTextContains(memo.body)
        compose.onNodeWithTag("notification-detail").assertDoesNotExist()
        compose.onNodeWithTag("memo-done").performClick()
        compose.waitUntil(5000) { vm.detailTarget.value == "reminder/${r.id}" }
        compose.onNodeWithTag("notification-detail").assertExists()
    }
    @Test fun calendarOnlyShowsTasksInUpcomingAndKeepsEventsAndNotes() {
        val today = LocalDate.now()
        val task = Entry(kind = EntryKind.TASK, title = "近期截止样例", date = today.toString())
        val done = Entry(kind = EntryKind.TASK, title = "已完成样例", date = today.toString(), completed = true)
        val event = Entry(title = "明天的日程", date = today.plusDays(1).toString())
        val note = Entry(kind = EntryKind.NOTE, title = "今天的记录", date = today.toString())
        runBlocking { listOf(task, done, event).forEach { repo.save(it) } }; screen()
        compose.waitUntil(5000) { vm.entries.value.size == 3 }
        compose.onNodeWithTag("marker-$today", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${task.id}"))
        compose.onAllNodesWithTag("entry-${task.id}").assertCountEquals(1)
        compose.onNodeWithTag("entry-${done.id}").assertDoesNotExist()
        runBlocking { repo.save(note) }
        compose.waitUntil(5000) { vm.entries.value.size == 4 }
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${note.id}"))
        compose.onNodeWithTag("entry-${note.id}").assertExists(); capture("calendar-tasks")
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.onNodeWithTag("marker-$today", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-${today.plusDays(1)}").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${event.id}"))
        compose.onNodeWithTag("entry-${event.id}").assertExists()
        compose.onNodeWithTag("nav-tasks").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("entry-${task.id}"))
        compose.onNodeWithTag("entry-${task.id}").assertExists()
    }
}
