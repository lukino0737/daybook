package dev.lukino.daybook.appearance

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/** A small local histogram avoids uploading photos or retaining their metadata. */
object ImageColors {
    fun seed(bitmap: Bitmap): Int {
        val counts = IntArray(4096)
        val step = max(1, max(bitmap.width, bitmap.height) / 100)
        for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
            val color = bitmap.getPixel(x, y)
            val key = ((color shr 20 and 15) shl 8) or ((color shr 12 and 15) shl 4) or (color shr 4 and 15)
            counts[key]++
        }
        val hsl = FloatArray(3)
        fun color(key: Int) = 0xFF000000.toInt() or ((key shr 8 and 15) * 17 shl 16) or ((key shr 4 and 15) * 17 shl 8) or ((key and 15) * 17)
        val best = counts.indices.maxBy { key ->
            ColorUtils.colorToHSL(color(key), hsl)
            val usable = if (hsl[2] in .12f.. .88f) 1f else .08f
            counts[key] * usable * (.15f + hsl[1])
        }
        return color(best)
    }
}
