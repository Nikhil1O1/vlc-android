package org.videolan.vlc.gui.music.nowplaying

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import org.videolan.vlc.gui.music.visualizer.RING_COVER_FRACTION
import org.videolan.vlc.gui.music.visualizer.VisualizerStrip
import org.videolan.vlc.gui.music.visualizer.coverVisualizer
import org.videolan.vlc.gui.music.visualizer.rememberVisualizerFrame
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.flow.drop
import org.videolan.medialibrary.Tools
import org.videolan.vlc.R

private val MiniHeight = 72.dp

@Composable
fun NowPlayingScreen(state: NowPlayingUiState, actions: NowPlayingActions, slide: Float) {
    val miniAlpha = (1f - slide * 2.5f).coerceIn(0f, 1f)
    val fullAlpha = ((slide - 0.25f) / 0.75f).coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            if (fullAlpha > 0f) FullPlayer(state, actions, Modifier.alpha(fullAlpha))
            if (miniAlpha > 0f) MiniPlayer(state, actions, Modifier.alpha(miniAlpha))
        }
    }
}

@Composable
private fun MiniPlayer(state: NowPlayingUiState, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(MiniHeight).clickable(onClick = actions.onTogglePlayer).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(state.artworkMrl, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)), 128)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(state.title.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.artist.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            val fraction = if (state.lengthMs > 0) (state.timeMs.toFloat() / state.lengthMs).coerceIn(0f, 1f) else 0f
            CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
            IconButton(onClick = actions.onPlayPause) {
                Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (state.playing) R.string.pause else R.string.play))
            }
        }
        IconButton(onClick = actions.onNext) { Icon(Icons.Filled.SkipNext, stringResource(R.string.next)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayer(state: NowPlayingUiState, actions: NowPlayingActions, modifier: Modifier = Modifier) {
    var showQueue by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Landscape / wide screens: cover on the left, controls on the right. The full-bleed style needs portrait.
        val landscape = maxWidth > maxHeight
        val style = if (landscape && state.style == NowPlayingStyle.FullCover) NowPlayingStyle.Normal else state.style
        // ---- background ----
        when (style) {
            NowPlayingStyle.Expressive -> {
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(36.dp) else Modifier
                Cover(state.artworkMrl, Modifier.fillMaxSize().then(blurModifier).alpha(0.55f), 64)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, scheme.surface.copy(alpha = 0.85f)))))
            }
            NowPlayingStyle.Gradient -> Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainer, scheme.surface)))
            )
            NowPlayingStyle.Normal, NowPlayingStyle.FullCover -> Unit
        }
        // Ring and Glow need room around the cover, so the full-bleed style falls back to bars.
        val vis = if (style == NowPlayingStyle.FullCover && state.visualizer.let { it == VisualizerStyle.Ring || it == VisualizerStyle.Glow }) VisualizerStyle.Bars else state.visualizer
        val frame = rememberVisualizerFrame(enabled = vis != VisualizerStyle.Off && state.playing)
        val accent = scheme.primary
        val corner = if (style == NowPlayingStyle.Expressive) 36 else 24

        // The cover, optionally with the ring/glow visualizer around it. [square] sizes the outer square.
        val coverBlock: @Composable (Modifier) -> Unit = { square ->
            if (vis == VisualizerStyle.Ring || vis == VisualizerStyle.Glow) {
                val fraction = if (vis == VisualizerStyle.Ring) RING_COVER_FRACTION else 0.86f
                Box(square.coverVisualizer(vis, frame, accent), contentAlignment = Alignment.Center) {
                    val shape = if (vis == VisualizerStyle.Ring) CircleShape else RoundedCornerShape(corner.dp)
                    CoverPager(state, actions, Modifier.fillMaxWidth(fraction).aspectRatio(1f).clip(shape), corner = if (vis == VisualizerStyle.Ring) 0 else corner)
                }
            } else {
                CoverPager(state, actions, square.clip(RoundedCornerShape(corner.dp)), corner = corner)
            }
        }

        if (landscape) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    coverBlock(Modifier.aspectRatio(1f))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Header(state, actions)
                    Spacer(Modifier.weight(1f))
                    if (vis == VisualizerStyle.Bars || vis == VisualizerStyle.Wave) VisualizerStrip(vis, frame, accent, Modifier.height(28.dp))
                    InfoRow(state, actions, centered = style == NowPlayingStyle.Gradient)
                    Seek(state, actions)
                    Transport(state, actions)
                    Spacer(Modifier.height(8.dp))
                    Chips(state, actions, onQueue = { showQueue = true })
                    Spacer(Modifier.weight(0.4f))
                }
            }
        } else {
            // ---- full-bleed cover for the Full cover style ----
            if (style == NowPlayingStyle.FullCover) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.68f)) {
                    CoverPager(state, actions, Modifier.fillMaxSize(), corner = 0, spacing = 0)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0.55f to Color.Transparent, 1f to scheme.surfaceContainer)
                        )
                    )
                }
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Header(state, actions)
                if (style == NowPlayingStyle.FullCover) Spacer(Modifier.weight(1f))
                else {
                    Spacer(Modifier.weight(0.25f))
                    coverBlock(Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.weight(0.25f))
                }
                if (vis == VisualizerStyle.Bars || vis == VisualizerStyle.Wave) VisualizerStrip(vis, frame, accent)
                InfoRow(state, actions, centered = style == NowPlayingStyle.Gradient)
                Spacer(Modifier.height(12.dp))
                Seek(state, actions)
                Spacer(Modifier.height(8.dp))
                Transport(state, actions)
                Spacer(Modifier.height(20.dp))
                Chips(state, actions, onQueue = { showQueue = true })
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    if (showQueue) QueueSheet(state, actions, onDismiss = { showQueue = false })
}

@Composable
private fun Header(state: NowPlayingUiState, actions: NowPlayingActions) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onTogglePlayer) { Icon(Icons.Filled.KeyboardArrowDown, null) }
        Row {
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Palette, stringResource(R.string.music_player_style)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                NowPlayingStyle.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(s.labelRes), fontWeight = if (s == state.style) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; actions.onStyle(s) },
                    )
                }
            }
        }
        Box {
            var vmenu by remember { mutableStateOf(false) }
            IconButton(onClick = { vmenu = true }) { Icon(Icons.Filled.GraphicEq, stringResource(R.string.music_visualizer)) }
            DropdownMenu(expanded = vmenu, onDismissRequest = { vmenu = false }) {
                VisualizerStyle.entries.forEach { v ->
                    DropdownMenuItem(
                        text = { Text(stringResource(v.labelRes), fontWeight = if (v == state.visualizer) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { vmenu = false; actions.onVisualizer(v) },
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverPager(state: NowPlayingUiState, actions: NowPlayingActions, modifier: Modifier, corner: Int, spacing: Int = 16) {
    val queue = state.queue
    if (queue.isEmpty() || state.currentIndex !in queue.indices) {
        Cover(state.artworkMrl, modifier, 720)
        return
    }
    val size by rememberUpdatedState(queue.size)
    val current by rememberUpdatedState(state.currentIndex)
    val playIndex by rememberUpdatedState(actions.onPlayIndex)
    val pager = rememberPagerState(initialPage = state.currentIndex) { size }
    LaunchedEffect(state.currentIndex) {
        if (pager.currentPage != state.currentIndex) pager.scrollToPage(state.currentIndex)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { if (it != current) playIndex(it) }
    }
    HorizontalPager(pager, modifier, pageSpacing = spacing.dp, beyondViewportPageCount = 1, key = { System.identityHashCode(queue.getOrNull(it)) + it }) { page ->
        Cover(queue.getOrNull(page)?.artworkMrl, Modifier.fillMaxSize().clip(RoundedCornerShape(corner.dp)), 720, ContentScale.Crop)
    }
}

@Composable
private fun InfoRow(state: NowPlayingUiState, actions: NowPlayingActions, centered: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                state.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth()
            )
            val sub = listOfNotNull(state.artist?.takeIf { it.isNotBlank() }, state.album?.takeIf { it.isNotBlank() }).joinToString(" • ")
            Text(
                sub, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.size(8.dp))
        Row(Modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            IconButton(onClick = actions.onFavorite) {
                Icon(if (state.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = if (state.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = actions.onOptions) { Icon(Icons.Filled.MoreVert, null) }
        }
    }
}

@Composable
private fun Seek(state: NowPlayingUiState, actions: NowPlayingActions) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = if (state.lengthMs > 0) (state.timeMs.toFloat() / state.lengthMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        WavySeekBar(
            value = fraction, playing = state.playing, enabled = state.lengthMs > 0,
            onFinished = { actions.onSeek((it * state.lengthMs).toLong()) },
            onDragging = { dragFraction = it },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragFraction?.let { (it * state.lengthMs).toLong() } ?: state.timeMs
            Text(Tools.millisToString(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Tools.millisToString(state.lengthMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Transport(state: NowPlayingUiState, actions: NowPlayingActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = actions.onPrevious, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous), Modifier.size(30.dp))
        }
        CookiePlayButton(
            playing = state.playing, onClick = actions.onPlayPause,
            icon = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            description = stringResource(if (state.playing) R.string.pause else R.string.play),
        )
        FilledTonalIconButton(onClick = actions.onNext, modifier = Modifier.size(64.dp)) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.next), Modifier.size(30.dp))
        }
    }
}

@Composable
private fun Chips(state: NowPlayingUiState, actions: NowPlayingActions, onQueue: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val repeating = state.repeatMode != PlaybackStateCompat.REPEAT_MODE_NONE
        ChipToggle(if (state.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, stringResource(R.string.music_repeat), repeating, actions.onRepeat)
        ChipToggle(Icons.Filled.Shuffle, stringResource(R.string.shuffle), state.shuffling, actions.onShuffle)
        ChipToggle(Icons.Filled.QueueMusic, stringResource(R.string.music_queue), false, onQueue)
        ChipToggle(Icons.Filled.VolumeUp, stringResource(R.string.volume), false, actions.onVolume)
        ChipToggle(Icons.Filled.Tune, stringResource(R.string.music_options), false, actions.onOptions)
    }
}
