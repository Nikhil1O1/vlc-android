package org.videolan.vlc.gui.music.nowplaying

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.vlc.gui.helpers.AudioUtil

/** Cover art loaded through VLC's own cover cache. Falls back to a music note placeholder. */
@Composable
fun Cover(artworkMrl: String?, modifier: Modifier, pixelWidth: Int, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, artworkMrl, pixelWidth) {
        value = if (artworkMrl.isNullOrEmpty()) null
        else withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(artworkMrl, pixelWidth) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = contentScale)
        else Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Seek bar whose played part is a wave that moves while music plays, with a vertical thumb.
 * [value] is 0..1. [onFinished] is called with the new fraction when the user lifts their finger.
 */
@Composable
fun WavySeekBar(
    value: Float,
    playing: Boolean,
    enabled: Boolean,
    onFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDragging: (Float?) -> Unit = {},
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: value
    val amplitude by animateDpAsState(if (playing && drag == null) 4.dp else 0.dp, tween(400), label = "amplitude")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "phase"
    )
    val finished by rememberUpdatedState(onFinished)
    val dragging by rememberUpdatedState(onDragging)
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { finished((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f); dragging(drag) },
                    onDragEnd = { drag?.let(finished); drag = null; dragging(null) },
                    onDragCancel = { drag = null; dragging(null) },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f); dragging(drag) },
                )
            }
    ) {
        val stroke = 4.dp.toPx()
        val cy = size.height / 2
        val x = (shown.coerceIn(0f, 1f) * size.width)
        val amp = amplitude.toPx()
        val wavelength = 28.dp.toPx()
        val path = Path().apply {
            moveTo(0f, cy)
            var px = 0f
            while (px <= x) {
                lineTo(px, cy + amp * sin(px / wavelength * 2 * PI.toFloat() - phase))
                px += 2f
            }
        }
        drawLine(inactive, Offset(x, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
        drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
        val thumbH = 22.dp.toPx()
        val thumbW = 4.dp.toPx()
        drawRoundRect(active, Offset(x - thumbW / 2, cy - thumbH / 2), Size(thumbW, thumbH), CornerRadius(thumbW / 2))
    }
}

private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).also {
            val m = Matrix()
            m.setScale(size.width / 2f, size.height / 2f)
            m.postTranslate(size.width / 2f, size.height / 2f)
            it.transform(m)
        }
        return Outline.Generic(path.asComposePath())
    }
}

/** Scalloped "cookie" play button that eases into a circle when paused. */
@Composable
fun CookiePlayButton(playing: Boolean, onClick: () -> Unit, icon: ImageVector, description: String, modifier: Modifier = Modifier) {
    val morph = remember {
        val cookie = RoundedPolygon.star(9, 1f, 0.82f, CornerRounding(0.35f), CornerRounding(0.35f))
        val circle = RoundedPolygon.circle(9)
        Morph(cookie, circle)
    }
    val progress by animateFloatAsState(if (playing) 0f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "morph")
    val shape = remember(progress) { MorphShape(morph, progress) }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(88.dp),
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(),
    ) {
        Icon(icon, description, Modifier.size(40.dp))
    }
}

/** Small rounded toggle used in the row under the transport controls. */
@Composable
fun ChipToggle(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = container, contentColor = content, modifier = modifier.size(width = 56.dp, height = 48.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(22.dp)) }
    }
}
