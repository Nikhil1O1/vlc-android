package org.videolan.vlc.gui.music.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VlcOrange = Color(0xFFFF8800)

/** Fallback scheme used when no cover colour is available. */
fun fallbackColorScheme(dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(primary = VlcOrange) else lightColorScheme(primary = VlcOrange)

@Composable
fun MusicTheme(
    colorScheme: ColorScheme = fallbackColorScheme(isSystemInDarkTheme()),
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
