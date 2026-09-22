package dev.lukino.daybook

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.RichBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Actual system document picker and Activity recreation; deletes only the unique synthetic row/media it creates. */
class ImagePickerSystemTest {
    @get:Rule(order = 0) val returning = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realPickerCopiesImageAndActivityRecreationKeepsDraft() {
        val app = compose.activity.application as DaybookApplication
        val resolver = app.contentResolver
        val marker = "Daybook图片QA-${UUID.randomUUID()}"
        val name = "$marker.png"
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Daybook-QA")
        })!!
        var sourceRemoved = false
        try {
            Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(130, 180, 145)); resolver.openOutputStream(uri)!!.use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
            }
            compose.onNodeWithTag("nav-memos").performClick(); compose.onNodeWithTag("add").performClick()
            compose.onNodeWithTag("memo-body").performTextInput(marker)
            compose.onNodeWithTag("insert-image").performScrollTo().performClick()
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString()?.contains(marker) == true || node.contentDescription?.toString()?.contains(marker) == true) return node
                for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            var found: AccessibilityNodeInfo? = null
            val until = System.currentTimeMillis() + 10_000
            while (found == null && System.currentTimeMillis() < until) { found = find(automation.rootInActiveWindow?.takeIf { it.packageName?.toString() == "com.android.documentsui" }); if (found == null) android.os.SystemClock.sleep(200) }
            assertNotNull("System document picker must show the synthetic image", found)
            automation.waitForIdle(500, 5000)
            android.os.SystemClock.sleep(500)
            found = find(automation.rootInActiveWindow)
            // DocumentsUI delegates tile taps to RecyclerView: the tile itself reports clickable=false.
            // Tap its observed bounds rather than climbing to the full-screen, non-clickable root.
            val target = found!!
            val rect = android.graphics.Rect(); target.getBoundsInScreen(rect)
            automation.executeShellCommand("input tap ${rect.centerX()} ${rect.centerY()}").let { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
            compose.waitUntil(10_000) { runBlocking { app.repository.allMemos().any { it.body == marker && RichBody.images(it.blocks).size == 1 } } }
            val memo = runBlocking { app.repository.allMemos().single { it.body == marker } }
            val image = RichBody.images(memo.blocks).single()
            assertEquals(1, resolver.delete(uri, null, null)); sourceRemoved = true; app.images.verify(image)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("memo-body").performScrollTo().assertTextContains(marker)
            compose.onNodeWithTag("body-image-1").performScrollTo().assertExists()
            if (InstrumentationRegistry.getArguments().getString("imagePreview") == "true") {
                compose.waitForIdle(); android.os.SystemClock.sleep(450)
                val out = java.io.File(app.getExternalFilesDir(null), "image-previews").apply { mkdirs() }
                automation.takeScreenshot()?.let { shot ->
                    java.io.File(out, "system-picker-restored.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }; shot.recycle()
                }
            }
            compose.onNodeWithTag("memo-done").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("memo-editor").fetchSemanticsNodes().isEmpty() }
            assertEquals(memo, runBlocking { app.repository.allMemos().single { it.id == memo.id } })
        } finally {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            if (automation.rootInActiveWindow?.packageName?.toString() == "com.android.documentsui") {
                automation.executeShellCommand("input keyevent 4").close()
            }
            compose.activityRule.scenario.close()
            if (!sourceRemoved) resolver.delete(uri, null, null)
            runBlocking { app.repository.allMemos().filter { it.body == marker }.forEach { app.repository.deleteMemo(it.id) }; app.repository.collectImages() }
        }
    }
}
