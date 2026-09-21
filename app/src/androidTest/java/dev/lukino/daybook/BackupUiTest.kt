package dev.lukino.daybook

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.backup.*
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class BackupUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DaybookViewModel
    private lateinit var dir: File
    private val store = ViewModelStore()

    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dir = File(context.cacheDir, "ui-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        repo = EntryRepository(db)
        vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(isolated, repo))
        store.put("test", vm)
    }
    @After fun cleanup() { store.clear(); db.close(); dir.deleteRecursively() }

    @Test fun emptyRestoreNeedsConfirmationAndSnapshotRestoresPreviousData() {
        val original = Entry(title = "测试原始记录", date = "2026-09-10")
        runBlocking { repo.save(original) }
        val file = File(dir, "empty.json").apply { writeText(BackupCodec.encode(emptyList())) }
        compose.setContent { DaybookTheme { DaybookScreen(vm) } }
        compose.runOnIdle { vm.previewImport(Uri.fromFile(file)) }
        compose.waitUntil(5000) { vm.pendingRestore.value != null && !vm.busy.value }
        compose.onNodeWithText("这是空备份", substring = true).assertIsDisplayed()
        assertEquals(listOf(original), runBlocking { repo.all() })
        compose.onNodeWithText("取消").performClick()
        assertEquals(listOf(original), runBlocking { repo.all() })
        compose.runOnIdle { vm.previewImport(Uri.fromFile(file)) }
        compose.waitUntil(5000) { vm.pendingRestore.value != null && !vm.busy.value }
        compose.onNodeWithText("确认替换").performClick()
        compose.waitUntil(5000) { vm.pendingRestore.value == null && !vm.busy.value }
        assertTrue(runBlocking { repo.all().isEmpty() })
        compose.runOnIdle { vm.previewSnapshot() }
        compose.waitUntil(5000) { vm.pendingRestore.value != null && !vm.busy.value }
        compose.onNodeWithText("确认替换").performClick()
        compose.waitUntil(5000) { vm.pendingRestore.value == null && !vm.busy.value }
        assertEquals(listOf(original), runBlocking { repo.all() })
    }

    @Test fun largeFontEditorKeepsSaveAccessibleAndRejectsStaleUndo() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                DaybookTheme { DaybookScreen(vm) }
            }
        }
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("title").performTextInput("大字体下也能随手记录生活")
        compose.onNodeWithTag("note").performScrollTo().performClick().performTextInput("这是一段备注。\n跨行输入后保存按钮仍应可见。")
        compose.onNodeWithTag("save").assertIsDisplayed().assertIsEnabled()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.getExternalFilesDir(null), "qa").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "editor-large-font.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5000) { runBlocking { repo.all().size == 1 } }
        val before = runBlocking { repo.all().single() }
        compose.runOnIdle { vm.historyVersion.value += 1; vm.undoDelete(before.copy(id = UUID.randomUUID().toString()), 0) }
        compose.waitUntil(5000) { !vm.busy.value }
        assertEquals(1, runBlocking { repo.all().size })
    }
    @Test fun oldBackupPreviewWarnsBeforeClearingIndependentReminders() {
        val reminder = StandaloneReminder(title = "需要保留的提醒", startDate = "2026-09-21", time = "09:00", repeat = RepeatKind.DAILY)
        runBlocking { repo.saveReminder(reminder) }
        val file = File(dir, "legacy.json").apply { writeText("""{"formatVersion":4,"exportedAt":0,"entries":[],"memos":[]}""") }
        compose.setContent { DaybookTheme { DaybookScreen(vm) } }
        compose.runOnIdle { vm.previewImport(Uri.fromFile(file)) }
        compose.waitUntil(5000) { vm.pendingRestore.value != null && !vm.busy.value }
        compose.onNodeWithText("恢复会清空当前独立提醒", substring = true).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(reminder.id, runBlocking { repo.allReminders().single().id })
    }

}
