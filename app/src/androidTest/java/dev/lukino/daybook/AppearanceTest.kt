package dev.lukino.daybook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.appearance.*
import dev.lukino.daybook.ui.imageColorScheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class AppearanceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun previewCancelApplyRestartCorruptInputAndResetPreserveCommittedAppearance() = runBlocking {
        val root = File(context.cacheDir, "appearance-test-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(root, "source.png")
        val directory = File(root, "private")
        try {
            Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                .let { bitmap -> source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            val store = AppearanceStore(context, directory)
            store.initialized.join()
            store.select(Uri.fromFile(source)).join()
            assertNull(store.state.value.error)
            assertNull(store.state.value.current)
            val staged = store.state.value.preview!!.file!!
            store.cancel().join(); assertFalse(staged.exists())
            store.select(Uri.fromFile(source)).join(); store.apply().join()
            val current = store.state.value.current!!
            source.delete()
            assertTrue(current.file!!.exists())
            val restarted = AppearanceStore(context, directory)
            restarted.initialized.join()
            assertEquals(current.file, restarted.state.value.current!!.file)
            assertEquals(current.seed, restarted.state.value.current!!.seed)
            source.writeText("not an image")
            restarted.select(Uri.fromFile(source)).join()
            assertNotNull(restarted.state.value.error)
            assertEquals(current.file, restarted.state.value.current!!.file)
            // A failed configuration write must leave the current choice and image intact.
            File(directory, "current.json.new").mkdirs()
            File(directory, "current.json.new/block").writeText("fixture")
            restarted.reset().join()
            assertNotNull(restarted.state.value.error)
            assertEquals(current.file, restarted.state.value.current!!.file)
            assertTrue(current.file!!.exists())
            File(directory, "current.json.new").deleteRecursively()
            restarted.reset().join()
            assertNull(restarted.state.value.current)
            val reset = AppearanceStore(context, directory)
            reset.initialized.join()
            assertNull(reset.state.value.current)
            assertFalse(current.file!!.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun customColorWithoutImagePersistsAndLegacyImageCanSwitchModes() = runBlocking {
        val root = File(context.cacheDir, "appearance-color-${UUID.randomUUID()}").apply { mkdirs() }
        val directory = File(root, "private")
        val source = File(root, "source.png")
        try {
            val store = AppearanceStore(context, directory)
            store.initialized.join()
            store.setColor(Color.RED).join()
            assertNull(store.state.value.current)
            store.cancel().join()
            assertNull(store.state.value.preview)
            store.setColor(Color.BLUE).join(); store.apply().join()
            val restarted = AppearanceStore(context, directory)
            restarted.initialized.join()
            assertEquals(Color.BLUE, restarted.state.value.current!!.seed)
            assertNull(restarted.state.value.current!!.bitmap)
            assertTrue(restarted.state.value.current!!.customColor)
            Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }.let { bitmap ->
                source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
            restarted.select(Uri.fromFile(source)).join()
            assertEquals(Color.BLUE, restarted.state.value.preview!!.seed)
            restarted.followImage().join()
            assertFalse(restarted.state.value.preview!!.customColor)
            restarted.apply().join()
            val image = restarted.state.value.current!!
            // v0.4 JSON has no color mode field.
            File(directory, "current.json").writeText(org.json.JSONObject().put("image", image.file!!.name).put("seed", image.seed).toString())
            val legacy = AppearanceStore(context, directory)
            legacy.initialized.join()
            assertFalse(legacy.state.value.current!!.customColor)
            legacy.setColor(Color.MAGENTA).join()
            File(directory, "current.json.new/block").apply { parentFile!!.mkdirs(); writeText("fixture") }
            legacy.apply().join()
            assertNotNull(legacy.state.value.error)
            assertEquals(image.seed, legacy.state.value.current!!.seed)
            assertTrue(image.file.exists())
            File(directory, "current.json.new").deleteRecursively()
            legacy.apply().join()
            val custom = AppearanceStore(context, directory)
            custom.initialized.join()
            assertTrue(custom.state.value.current!!.customColor)
            assertEquals(Color.MAGENTA, custom.state.value.current!!.seed)
            assertEquals(image.file, custom.state.value.current!!.file)
            custom.reset().join()
            assertNull(custom.state.value.current)
        } finally { root.deleteRecursively() }
    }

    @Test fun photographedOrientationAndLargeImagesDecodeWithinBoundsOnBothPaths() {
        val file = File(context.cacheDir, "orientation-${UUID.randomUUID()}.jpg")
        try {
            val bitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.MAGENTA)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
            listOf(decodeImage(file), decodeLegacyImage(file)).forEach {
                assertTrue(it.height > it.width)
                assertTrue(maxOf(it.width, it.height) <= 1920)
                it.recycle()
            }
        } finally { file.delete() }
    }

    @Test fun customColorsRemainReadableAndBrightnessAffectsTheme() {
        for (seed in listOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.YELLOW, Color.BLUE, Color.CYAN)) {
            val scheme = imageColorScheme(seed, true)
            assertTrue(ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb()) >= 9.0)
            assertTrue(ColorUtils.calculateContrast(scheme.onSurface.toArgb(), scheme.surface.toArgb()) >= 7.0)
        }
        assertNotEquals(imageColorScheme(0xFF101080.toInt(), true).primary, imageColorScheme(0xFF080820.toInt(), true).primary)
    }

    @Test fun darkLightGrayAndSaturatedPhotosProduceLegibleRoles() {
        for (seed in listOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)) {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(seed) }
            val scheme = imageColorScheme(ImageColors.seed(bitmap)); bitmap.recycle()
            assertTrue(ColorUtils.calculateContrast(scheme.onPrimary.toArgb(), scheme.primary.toArgb()) >= 9.0)
            assertTrue(ColorUtils.calculateContrast(scheme.onSurface.toArgb(), scheme.surface.toArgb()) >= 7.0)
            val darkestBackground = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(scheme.background.toArgb(), 199), Color.BLACK)
            assertTrue(ColorUtils.calculateContrast(scheme.primary.toArgb(), darkestBackground) >= 4.5)
            assertTrue(ColorUtils.calculateContrast(scheme.onBackground.toArgb(), darkestBackground) >= 7.0)
            assertTrue(ColorUtils.calculateContrast(scheme.onSurfaceVariant.toArgb(), scheme.surface.toArgb()) >= 4.5)
        }
    }
}
