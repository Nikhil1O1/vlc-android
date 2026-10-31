package org.videolan.vlc.gui.music.visualizer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import org.videolan.vlc.gui.music.nowplaying.VisualizerStyle
import org.videolan.resources.VLCOptions
import kotlin.math.max

/** In Ring style the cover is drawn at this fraction of the square so the bars fit around it. */
const val RING_COVER_FRACTION = 0.6f

/**
 * Live frames while [enabled] and the screen is resumed; null when off or unavailable.
 * Drawing code reads the returned State inside draw lambdas so only the canvas redraws, not the whole screen.
 */
@Composable
fun rememberVisualizerFrame(enabled: Boolean): State<VisualizerFrame?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<VisualizerFrame?>(null, enabled) {
        if (!enabled) { value = null; return@produceState }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            VisualizerSource.frames(VLCOptions.audiotrackSessionId).collect { value = it }
        }
        value = null
    }
}

/** Bars or waveform strip shown between the cover and the title. */
@Composable
fun VisualizerStrip(style: VisualizerStyle, frame: State<VisualizerFrame?>, color: Color, modifier: Modifier = Modifier.height(64.dp)) {
    Box(modifier.fillMaxWidth().drawBehind {
        val f = frame.value ?: return@drawBehind
        if (style == VisualizerStyle.Wave) drawWave(f.waveform, color) else drawBars(f.bands, color)
    })
}

/** Draws behind the cover: radial bars (Ring) or a pulsing glow (Glow). */
fun Modifier.coverVisualizer(style: VisualizerStyle, frame: State<VisualizerFrame?>, color: Color): Modifier = drawBehind {
    val f = frame.value ?: return@drawBehind
    when (style) {
        VisualizerStyle.Ring -> drawRing(f.bands, color, innerRadius = size.minDimension * RING_COVER_FRACTION / 2f)
        VisualizerStyle.Glow -> drawGlow(f.bass, color)
        else -> Unit
    }
}

private fun DrawScope.drawBars(bands: FloatArray, color: Color) {
    if (bands.isEmpty()) return
    val gap = 3.dp.toPx()
    val barWidth = (size.width - gap * (bands.size - 1)) / bands.size
    for (i in bands.indices) {
        val h = max(bands[i] * size.height, barWidth)
        drawRoundRect(
            color.copy(alpha = 0.45f + 0.55f * bands[i]),
            Offset(i * (barWidth + gap), size.height - h),
            Size(barWidth, h),
            CornerRadius(barWidth / 2f),
        )
    }
}

private fun DrawScope.drawWave(wave: FloatArray, color: Color) {
    if (wave.size < 2) return
    val path = androidx.compose.ui.graphics.Path()
    val mid = size.height / 2f
    for (i in wave.indices) {
        val x = size.width * i / (wave.size - 1)
        val y = mid - wave[i] * mid
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

/** Bars radiate outward from a circle of [innerRadius]; the space around the cover is made by the caller's padding. */
private fun DrawScope.drawRing(bands: FloatArray, color: Color, innerRadius: Float) {
    if (bands.isEmpty()) return
    val maxLen = size.minDimension * 0.18f
    val count = bands.size * 2 // mirror left/right so the ring is symmetric
    val barWidth = 4.dp.toPx()
    for (i in 0 until count) {
        val level = bands[if (i < bands.size) i else count - 1 - i]
        val length = max(level * maxLen, barWidth)
        rotate(degrees = 360f * i / count - 90f, pivot = center) {
            drawRoundRect(
                color.copy(alpha = 0.4f + 0.6f * level),
                Offset(center.x + innerRadius + 6.dp.toPx(), center.y - barWidth / 2f),
                Size(length, barWidth),
                CornerRadius(barWidth / 2f),
            )
        }
    }
}

private fun DrawScope.drawGlow(bass: Float, color: Color) {
    val radius = size.minDimension * (0.55f + 0.5f * bass)
    drawCircle(
        Brush.radialGradient(
            0f to color.copy(alpha = 0.25f + 0.55f * bass),
            1f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
    )
}
