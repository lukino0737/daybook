package dev.lukino.daybook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.appearance.AppearanceStore
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class AppearanceUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun paletteWithoutBackgroundPreviewsCancelsAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "palette-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val store = AppearanceStore(context, root)
        var open by mutableStateOf(true)
        try {
            runBlocking { store.initialized.join() }
            compose.setContent { DaybookTheme { if (open) AppearanceSettingsScreen(store) { open = false } } }
            compose.onNodeWithText("四个主页使用同一背景", substring = true).assertDoesNotExist()
            compose.onNodeWithTag("preset-海蓝").performScrollTo().performClick()
            compose.waitUntil(5000) { store.state.value.preview?.customColor == true }
            assertNull(store.state.value.current)
            compose.onNodeWithTag("color-brightness").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.25f) }
            compose.waitUntil(5000) { (store.state.value.preview!!.seed and 0xFF) < 100 }
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitUntil(5000) { !open && store.state.value.preview == null }
            assertNull(store.state.value.current)
            compose.runOnIdle { open = true }
            compose.onNodeWithTag("preset-玫红").performScrollTo().performClick()
            compose.onNodeWithTag("apply-appearance").performScrollTo().performClick()
            compose.waitUntil(5000) { !open && store.state.value.current != null }
            assertEquals(0xFFB83F78.toInt(), store.state.value.current!!.seed)
            assertNull(store.state.value.current!!.bitmap)
        } finally { root.deleteRecursively() }
    }

    @Test fun previewRequiresApplyAndDefaultCanBeRestored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "appearance-ui-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(root, "source.png")
        val store = AppearanceStore(context, File(root, "private"))
        var open by mutableStateOf(true)
        try {
            Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
                .let { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            runBlocking { store.initialized.join(); store.select(Uri.fromFile(source)).join() }
            compose.setContent { DaybookTheme { if (open) AppearanceSettingsScreen(store) { open = false } } }
            compose.onNodeWithText("预览 · 尚未应用").assertExists()
            assertNull(store.state.value.current)
            compose.onNodeWithTag("apply-appearance").performScrollTo().performClick()
            compose.waitUntil(5000) { !open }
            assertNotNull(store.state.value.current)
            compose.runOnIdle { open = true }
            compose.onNodeWithText("当前外观").assertExists()
            compose.onNodeWithTag("reset-appearance").performScrollTo().performClick()
            compose.waitUntil(5000) { !store.state.value.busy && store.state.value.current == null }
            compose.onNodeWithTag("apply-appearance").assertIsNotEnabled()
        } finally { root.deleteRecursively() }
    }
}
