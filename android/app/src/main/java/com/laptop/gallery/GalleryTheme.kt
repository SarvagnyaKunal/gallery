package com.laptop.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark is the theme (per spec) — always the dark scheme, regardless of system setting.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF1A1A1E),
    onBackground = Color(0xFFF2F2F5),
    onSurface = Color(0xFFF2F2F5)
)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
