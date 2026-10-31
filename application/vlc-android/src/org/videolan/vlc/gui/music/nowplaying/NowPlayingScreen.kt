package org.videolan.vlc.gui.music.nowplaying

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.Tools
import org.videolan.vlc.R
import org.videolan.vlc.gui.helpers.AudioUtil

private val MiniHeight = 72.dp

@Composable
fun NowPlayingScreen(
    title: String?,
    artist: String?,
    artworkMrl: String?,
    playing: Boolean,
    timeMs: Long,
    lengthMs: Long,
    slide: Float,
    onTogglePlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val miniAlpha = (1f - slide * 2.5f).coerceIn(0f, 1f)
    val fullAlpha = ((slide - 0.25f) / 0.75f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (fullAlpha > 0f) {
            FullPlayer(
                title, artist, artworkMrl, playing, timeMs, lengthMs, onTogglePlayer, onPlayPause, onNext, onPrevious, onSeek,
                Modifier.alpha(fullAlpha)
            )
        }
        if (miniAlpha > 0f) {
            MiniPlayer(
                title, artist, artworkMrl, playing, timeMs, lengthMs, onTogglePlayer, onPlayPause, onNext,
                Modifier.alpha(miniAlpha)
            )
        }
    }
}

@Composable
private fun MiniPlayer(
    title: String?, artist: String?, artworkMrl: String?, playing: Boolean, timeMs: Long, lengthMs: Long,
    onClick: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().height(MiniHeight).clickable(onClick = onClick)) {
        val fraction = if (lengthMs > 0) (timeMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(2.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(artworkMrl, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), 128)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title.orEmpty(), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    artist.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play)
                )
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, stringResource(R.string.next)) }
        }
    }
}

@Composable
private fun FullPlayer(
    title: String?, artist: String?, artworkMrl: String?, playing: Boolean, timeMs: Long, lengthMs: Long,
    onClose: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, null) }
        }
        Spacer(Modifier.weight(0.4f))
        Cover(artworkMrl, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)), 720)
        Spacer(Modifier.weight(0.4f))
        Text(
            title.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
        )
        Text(
            artist.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        SeekBar(timeMs, lengthMs, onSeek)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous), Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play), Modifier.size(44.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.next), Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.weight(0.6f))
    }
}

@Composable
private fun SeekBar(timeMs: Long, lengthMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: if (lengthMs > 0) (timeMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * lengthMs).toLong()) }
                dragging = null
            },
            enabled = lengthMs > 0,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.let { (it * lengthMs).toLong() } ?: timeMs
            Text(Tools.millisToString(shown), style = MaterialTheme.typography.labelMedium)
            Text(Tools.millisToString(lengthMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Cover(artworkMrl: String?, modifier: Modifier, pixelWidth: Int) {
    val bitmap by produceState<Bitmap?>(null, artworkMrl) {
        value = if (artworkMrl.isNullOrEmpty()) null
        else withContext(Dispatchers.IO) { AudioUtil.readCoverBitmap(artworkMrl, pixelWidth) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
