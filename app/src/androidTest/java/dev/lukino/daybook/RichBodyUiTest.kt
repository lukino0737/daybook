package dev.lukino.daybook

import android.app.Activity
import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.core.app.ActivityOptionsCompat
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
import java.util.UUID

class RichBodyUiTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private lateinit var dir: File
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var images: BodyImageStore
    private lateinit var backup: BackupService
    private lateinit var saved: SavedStateHandle
    private lateinit var models: ViewModelStore
    private lateinit var vm: DaybookViewModel
    private var shown by mutableStateOf<DaybookViewModel?>(null)
    private var picked: Uri? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            dispatchResult(requestCode, if (picked == null) Activity.RESULT_CANCELED else Activity.RESULT_OK, Intent().setData(picked))
        }
    }
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.cacheDir, "rich-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        images = BodyImageStore(isolated)
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db, images); backup = BackupService(isolated, repo)
        saved = SavedStateHandle(); models = ViewModelStore(); vm = DaybookViewModel(repo, saved, backup); models.put("vm", vm)
        val source = File(dir, "sample.png")
        Bitmap.createBitmap(800, 500, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this); canvas.drawColor(Color.rgb(224, 237, 224))
            canvas.drawCircle(570f, 150f, 90f, Paint().apply { color = Color.rgb(244, 198, 94) })
            canvas.drawRect(50f, 300f, 750f, 500f, Paint().apply { color = Color.rgb(78, 129, 101) })
            canvas.drawText("Daybook / image test", 50f, 100f, Paint().apply { color = Color.rgb(35, 57, 43); textSize = 40f; isAntiAlias = true })
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
        }
        picked = Uri.fromFile(source)
        shown = vm
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }) {
                DaybookTheme { DaybookScreen(shown!!) }
            }
        }
    }
    @After fun cleanup() { compose.runOnIdle { models.clear() }; db.close(); dir.deleteRecursively() }
    private fun addImage() {
        compose.onNodeWithTag("insert-image").performScrollTo().performClick()
        compose.waitUntil(5000) { (vm.draft.value?.blocks ?: vm.memoEditor.draft.value?.blocks).orEmpty().any { it.image != null } }
        compose.waitForIdle()
    }
    private fun closeEntry() { compose.onNodeWithTag("save").performClick(); compose.waitUntil(5000) { vm.draft.value == null && !vm.busy.value } }
    private fun closeMemo() { compose.onNodeWithTag("memo-done").performClick(); compose.waitUntil(5000) { vm.memoEditor.draft.value == null && !vm.memoEditor.busy.value } }
    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("imagePreview") != "true") return
        compose.waitForIdle(); android.os.SystemClock.sleep(450)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.getExternalFilesDir(null), "image-previews").apply { mkdirs() }
        val suffix = InstrumentationRegistry.getArguments().getString("previewSuffix") ?: "normal"
        File(folder, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun everyEntryKindInsertsAtCursorViewsAndRemovesWithoutLosingWords() {
        EntryKind.entries.forEach { kind ->
            compose.runOnIdle { vm.edit(); vm.setDraft(vm.draft.value?.copy(kind = kind) ?: Draft(kind = kind, date = "2026-09-22")) }
            compose.onNodeWithTag("title").performTextInput("图文${kind.label}")
            compose.onNodeWithTag("note").performScrollTo().performTextInput("前文后文")
            compose.onNodeWithTag("note").performTextInputSelection(TextRange(2))
            addImage()
            compose.onNodeWithTag("note").performScrollTo().assertTextContains("前文")
            compose.onNodeWithTag("note-2").performScrollTo().assertTextContains("后文")
            compose.onNodeWithTag("body-image-1").performScrollTo().performClick()
            compose.onNodeWithTag("image-viewer").assertExists(); capture("viewer")
            compose.onNodeWithTag("close-image-viewer").performClick()
            compose.onNodeWithTag("body-image-1").performScrollTo(); capture("entry-inline")
            closeEntry()
            val entry = runBlocking { repo.all().first { it.kind == kind } }
            assertEquals("前文后文", entry.note); assertEquals(3, entry.blocks.size)
            compose.runOnIdle { vm.edit(entry) }
            compose.onNodeWithTag("remove-image-1").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-remove-image").performClick()
            compose.onNodeWithTag("note").performScrollTo().assertTextContains("前文后文")
            closeEntry()
            assertTrue(runBlocking { repo.all().first { it.id == entry.id }.blocks.isEmpty() })
        }
    }
    @Test fun imageOnlyMemoAutoSavesAndBlankExitKeepsSavedImage() {
        compose.onNodeWithTag("nav-memos").performClick(); compose.onNodeWithTag("add").performClick()
        addImage()
        compose.waitUntil(5000) { runBlocking { repo.allMemos().size == 1 } }
        compose.onNodeWithTag("body-image-1").performScrollTo(); capture("memo-inline")
        closeMemo()
        val memo = runBlocking { repo.allMemos().single() }
        assertEquals("图片便签 · 1 张", memo.summary)
        compose.onNodeWithText("图片便签 · 1 张").assertExists(); capture("image-only-list")
        compose.onNodeWithTag("memo-${memo.id}").performClick()
        compose.onNodeWithTag("remove-image-1").performScrollTo().performClick(); compose.onNodeWithTag("confirm-remove-image").performClick()
        compose.onNodeWithTag("memo-done").performClick()
        compose.onNodeWithText("保留内容并退出").performClick()
        compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
        assertEquals(memo, runBlocking { repo.allMemos().single() }); images.verify(RichBody.images(memo.blocks).single())
    }
    @Test fun canceledDraftAndCanceledPickerCleanOnlyUnreferencedImages() {
        compose.onNodeWithTag("add").performClick(); compose.onNodeWithTag("title").performTextInput("取消草稿")
        addImage(); val image = RichBody.images(vm.draft.value!!.blocks).single()
        compose.onNodeWithText("取消", useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { !images.file(image).exists() }
        assertTrue(runBlocking { repo.all().isEmpty() })
        picked = null
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("insert-image").performScrollTo().performClick()
        compose.waitForIdle(); assertTrue(vm.draft.value!!.blocks.isEmpty())
    }
    @Test fun failedSaveAndRestoredDraftKeepImageUntilSuccessfulSave() {
        compose.onNodeWithTag("add").performClick(); compose.onNodeWithTag("title").performTextInput("需要保留的草稿")
        addImage(); val original = vm.draft.value!!; val image = RichBody.images(original.blocks).single()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_image_entry BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'test'); END")
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5000) { vm.failure.value != null && !vm.busy.value }
        assertEquals(original, vm.draft.value); images.verify(image)
        compose.onNodeWithTag("body-image-1").performScrollTo(); capture("save-failure")
        compose.runOnIdle {
            val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
            models.clear(); vm = DaybookViewModel(repo, restored, backup); models.put("restored", vm); shown = vm
        }
        compose.waitUntil(5000) { vm.draft.value != null }
        assertEquals(original, vm.draft.value); images.verify(image)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_image_entry")
        closeEntry(); assertEquals(original.blocks, runBlocking { repo.all().single().blocks })
    }
    @Test fun entryDeleteUndoAndMemoDeleteFailureKeepImages() {
        compose.onNodeWithTag("add").performClick(); compose.onNodeWithTag("title").performTextInput("可撤销的图文")
        addImage(); closeEntry()
        val entry = runBlocking { repo.all().single() }; val image = RichBody.images(entry.blocks).single()
        compose.runOnIdle { vm.delete(entry) }
        compose.waitUntil(5000) { runBlocking { repo.all().isEmpty() } }
        images.verify(image)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("撤销").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("撤销").performClick()
        compose.waitUntil(5000) { runBlocking { repo.all().isNotEmpty() } }
        images.verify(image)
        compose.onNodeWithTag("nav-memos").performClick(); compose.onNodeWithTag("add").performClick(); addImage(); closeMemo()
        val memo = runBlocking { repo.allMemos().single() }
        compose.onNodeWithTag("memo-${memo.id}").performClick()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_delete BEFORE DELETE ON memos BEGIN SELECT RAISE(ABORT, 'test'); END")
        compose.onNodeWithTag("memo-delete").performScrollTo().performClick()
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5000) { vm.memoEditor.error.value != null && !vm.memoEditor.busy.value }
        assertEquals(memo, runBlocking { repo.allMemos().single() }); images.verify(image)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_delete")
        compose.onNodeWithText("确认删除").performClick()
        compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
        assertTrue(runBlocking { repo.allMemos().isEmpty() }); images.verify(image) // entry still refers to the shared file
    }
    @Test fun badImageDoesNotChangeTextAndNineImagesDisableAdd() {
        picked = Uri.fromFile(File(dir, "bad.png").apply { writeText("invalid") })
        compose.onNodeWithTag("nav-memos").performClick(); compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("memo-body").performTextInput("不会丢失的文字")
        compose.onNodeWithTag("insert-image").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("image-error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("memo-body").performScrollTo().assertTextContains("不会丢失的文字")
        picked = Uri.fromFile(File(dir, "sample.png"))
        addImage()
        val first = vm.memoEditor.draft.value!!
        compose.runOnIdle {
            var blocks = first.blocks
            repeat(8) { blocks = RichBody.insert(first.body, blocks, blocks.lastIndex, blocks.last().text.length, RichBody.images(first.blocks).single()) }
            vm.memoEditor.change(first.copy(blocks = blocks))
        }
        compose.onNodeWithTag("insert-image").performScrollTo().assertIsNotEnabled()
        closeMemo(); assertEquals(9, RichBody.images(runBlocking { repo.allMemos().single().blocks }).size)
    }
}
