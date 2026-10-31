package org.videolan.vlc.gui.music.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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

@Composable
private fun animated(target: Color): Color =
    animateColorAsState(target, tween(durationMillis = 600), label = "coverColor").value

/** Cross-fades the roles used by the music screens when the target scheme changes (new track). */
@Composable
fun ColorScheme.animated(): ColorScheme = copy(
    primary = animated(primary),
    onPrimary = animated(onPrimary),
    primaryContainer = animated(primaryContainer),
    onPrimaryContainer = animated(onPrimaryContainer),
    secondaryContainer = animated(secondaryContainer),
    onSecondaryContainer = animated(onSecondaryContainer),
    surface = animated(surface),
    onSurface = animated(onSurface),
    surfaceVariant = animated(surfaceVariant),
    onSurfaceVariant = animated(onSurfaceVariant),
    background = animated(background),
    onBackground = animated(onBackground),
    surfaceContainer = animated(surfaceContainer),
    surfaceContainerHigh = animated(surfaceContainerHigh),
    outline = animated(outline),
)
