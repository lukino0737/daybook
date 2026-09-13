package dev.lukino.daybook

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.appearance.AppearanceStore
import dev.lukino.daybook.backup.BackupService
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Explicit visual acceptance only; all records and photos are fictional, in isolated stores. */
class V04PreviewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fourTabsMemoAndAppearanceRemainUsable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("daybookPreview") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "preview-${UUID.randomUUID()}").apply { mkdirs() }
        val folder = File(context.getExternalFilesDir(null), "v04-previews").apply { mkdirs() }
        val suffix = InstrumentationRegistry.getArguments().getString("previewSuffix") ?: "normal"
        val source = File(root, "sample.png")
        val photo = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 1000f, Color.rgb(115, 173, 200), Color.rgb(226, 195, 166), Shader.TileMode.CLAMP) })
            drawCircle(450f, 220f, 100f, Paint().apply { color = Color.rgb(249, 222, 168) })
            drawPath(Path().apply { moveTo(0f, 660f); quadTo(250f, 380f, 600f, 780f); lineTo(600f, 1000f); lineTo(0f, 1000f); close() }, Paint().apply { color = Color.rgb(75, 111, 144) })
            drawPath(Path().apply { moveTo(0f, 930f); quadTo(350f, 600f, 600f, 840f); lineTo(600f, 1000f); lineTo(0f, 1000f); close() }, Paint().apply { color = Color.rgb(54, 83, 105) })
        }
        source.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }; photo.recycle()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val vm = DaybookViewModel(repo, SavedStateHandle(), BackupService(context, repo))
        val viewModels = ViewModelStore().apply { put("preview", vm) }
        val store = AppearanceStore(context, File(root, "appearance"))
        var showAppearance by mutableStateOf(false)
        fun screenshot(name: String, tag: String? = null) {
            compose.waitForIdle()
            val node = tag?.let { compose.onNodeWithTag(it) } ?: compose.onRoot()
            File(folder, "$name-$suffix.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            runBlocking {
                repo.save(Entry(kind = EntryKind.EVENT, title = "周末散步", date = "2026-09-13", time = "17:30", tags = listOf("生活")))
                repo.save(Entry(kind = EntryKind.TASK, title = "整理旅行清单", date = "2026-09-20", tags = listOf("旅行")))
                repo.save(Entry(kind = EntryKind.TASK, title = "找一部想看的电影", date = null))
                repo.saveMemo(Memo(body = "下次散步，换一条没走过的路\n带上相机，看看傍晚的天空。"))
                repo.saveMemo(Memo(body = "一个关于周末的小想法\n去书店随便翻翻，不急着做计划。"))
                store.initialized.join()
            }
            vm.setMonth("2026-09"); vm.select(LocalDate.parse("2026-09-13"))
            compose.setContent {
                val appearance by store.state.collectAsState()
                DaybookTheme(appearance.current?.seed) {
                    CompositionLocalProvider(LocalAppearance provides appearance.current) {
                        DaybookScreen(vm)
                        if (showAppearance) AppearanceSettingsScreen(store) { showAppearance = false }
                    }
                }
            }
            compose.waitUntil(5000) { vm.entries.value.size == 3 }
            screenshot("calendar-default")
            runBlocking { store.select(Uri.fromFile(source)).join() }
            compose.runOnIdle { showAppearance = true }
            screenshot("appearance-preview", "appearance-settings")
            compose.onNodeWithTag("apply-appearance").performScrollTo().performClick()
            compose.waitUntil(5000) { !showAppearance }
            screenshot("calendar-background")
            compose.onNodeWithTag("nav-tasks").performClick(); screenshot("tasks-background")
            compose.onNodeWithTag("nav-memos").performClick(); screenshot("memos-background")
            compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("memo-body").performTextInput("随手记下今天的灵感\n便签默认不需要日期。")
            screenshot("memo-editor", "memo-editor")
            compose.onNodeWithTag("reminder-toggle").performScrollTo().performClick()
            compose.onNodeWithTag("memo-back").performClick()
            compose.waitUntil(5000) { vm.memoEditor.draft.value == null }
            compose.onNodeWithText("随手记下今天的灵感").assertExists()
            compose.onNodeWithTag("nav-review").performClick(); screenshot("review-background")
            compose.onNodeWithTag("reset-review").performScrollTo().assertIsDisplayed()
        } finally { viewModels.clear(); db.close(); root.deleteRecursively() }
    }
}
