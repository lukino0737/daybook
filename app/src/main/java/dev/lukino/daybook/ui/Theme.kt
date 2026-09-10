package dev.lukino.daybook.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFF8F6EF)
val Ink = Color(0xFF27392F)
val Green = Color(0xFF466553)
val Clay = Color(0xFFA34D34)

@Composable fun DaybookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = Color(0xFFE3EADD), onPrimaryContainer = Ink,
        background = Paper, surface = Paper, onSurface = Ink, onBackground = Ink,
        surfaceVariant = Color(0xFFECE9DE), onSurfaceVariant = Color(0xFF62695F),
        error = Clay, outline = Color(0xFFB9BCAF),
    ), content = content)
}
