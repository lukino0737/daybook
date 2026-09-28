package dev.lukino.daybook.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Small, in-memory snapshot: no image or screen content is written to storage. API 26 compatible. */
internal suspend fun blurredPreviewBackdrop(view: View): Bitmap? {
    val bitmap = withContext(Dispatchers.Main.immediate) {
        val root = view.rootView
        // A notification may open the preview before the Activity's first layout.
        if (root.width <= 0 || root.height <= 0) suspendCancellableCoroutine<Unit> { continuation ->
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (v.width > 0 && v.height > 0) {
                        v.removeOnLayoutChangeListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            root.addOnLayoutChangeListener(listener)
            continuation.invokeOnCancellation { root.post { root.removeOnLayoutChangeListener(listener) } }
        }
        val width = (root.width / 8).coerceAtLeast(1)
        val height = (root.height / 8).coerceAtLeast(1)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.scale(width.toFloat() / root.width, height.toFloat() / root.height)
            root.draw(canvas)
        }
    }
    val width = bitmap.width
    val height = bitmap.height
    return withContext(Dispatchers.Default) {
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        // Three separable box passes approximate a Gaussian blur at bounded memory/cost.
        repeat(3) {
            pixels = blurPass(pixels, width, height, horizontal = true)
            pixels = blurPass(pixels, width, height, horizontal = false)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    }
}
private fun blurPass(source: IntArray, width: Int, height: Int, horizontal: Boolean): IntArray {
    val result = IntArray(source.size)
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val radius = 4
    val count = radius * 2 + 1
    for (line in 0 until lines) {
        fun index(position: Int) = if (horizontal) line * width + position.coerceIn(0, length - 1)
            else position.coerceIn(0, length - 1) * width + line
        var red = 0; var green = 0; var blue = 0
        for (position in -radius..radius) {
            val color = source[index(position)]
            red += color shr 16 and 255; green += color shr 8 and 255; blue += color and 255
        }
        for (position in 0 until length) {
            result[index(position)] = (255 shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
            val leaving = source[index(position - radius)]
            val entering = source[index(position + radius + 1)]
            red += (entering shr 16 and 255) - (leaving shr 16 and 255)
            green += (entering shr 8 and 255) - (leaving shr 8 and 255)
            blue += (entering and 255) - (leaving and 255)
        }
    }
    return result
}
