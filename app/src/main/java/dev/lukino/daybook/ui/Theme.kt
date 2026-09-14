package dev.lukino.daybook.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF8F6EF)
val Ink = Color(0xFF27392F)
val Green = Color(0xFF466553)
val Clay = Color(0xFFA34D34)

@Composable fun DaybookTheme(seed: Int? = null, custom: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = seed?.let { imageColorScheme(it, custom) } ?: lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = Color(0xFFE3EADD), onPrimaryContainer = Ink,
        secondary = Green, secondaryContainer = Color(0xFFE3EADD), onSecondaryContainer = Ink,
        background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink,
        surfaceContainerLow = Color(0xFFF0EEE5), surfaceContainer = Color(0xFFECE9DE),
        surfaceContainerHigh = Color(0xFFE6E8DD), errorContainer = Color(0xFFF6E1D6),
        surfaceVariant = Color(0xFFECE9DE), onSurfaceVariant = Color(0xFF62695F),
        error = Clay, outline = Color(0xFFB9BCAF),
    ), content = content)
}

fun imageColorScheme(seed: Int, custom: Boolean = false): ColorScheme {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(seed, hsl)
    val saturation = hsl[1].coerceAtMost(.55f)
    fun tone(light: Float, chroma: Float = saturation) = Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], chroma, light)))
    var light = if (custom) hsl[2].coerceIn(.08f, .45f) else .32f
    while (androidx.core.graphics.ColorUtils.calculateContrast(
            androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], saturation, light)), android.graphics.Color.WHITE) < 9.0) light -= .01f
    val accent = tone(light)
    val ink = tone(.13f, saturation * .3f)
    val paper = tone(.975f, saturation * .25f)
    val container = tone(.9f, saturation * .45f)
    return lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = ink,
        secondary = accent, onSecondary = Color.White, secondaryContainer = container, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = Color.White, tertiaryContainer = container, onTertiaryContainer = ink,
        background = paper, onBackground = ink, surface = paper, onSurface = ink,
        surfaceTint = accent, surfaceBright = paper, surfaceDim = tone(.88f, saturation * .2f),
        surfaceContainerLowest = tone(.99f, saturation * .2f), surfaceContainerLow = tone(.95f, saturation * .2f),
        surfaceContainer = tone(.93f, saturation * .2f), surfaceContainerHigh = tone(.91f, saturation * .2f),
        surfaceContainerHighest = tone(.89f, saturation * .2f), surfaceVariant = tone(.91f, saturation * .2f),
        onSurfaceVariant = tone(.3f, saturation * .2f), outline = tone(.46f, saturation * .2f),
        outlineVariant = tone(.8f, saturation * .2f), inverseSurface = ink, inverseOnSurface = paper, inversePrimary = container,
        error = Clay, errorContainer = Color(0xFFF6E1D6), onError = Color.White, onErrorContainer = Color(0xFF381508),
    )
}
