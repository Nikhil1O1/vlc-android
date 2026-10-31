package org.videolan.vlc.gui.music.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.videolan.vlc.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: NowPlayingUiState, actions: NowPlayingActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val listState = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(listState) { from, to -> actions.onMoveQueueItem(from.index, to.index) }
        LaunchedEffect(Unit) { if (state.currentIndex > 0) listState.scrollToItem(state.currentIndex) }
        Column {
            Text(stringResource(R.string.music_queue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(state = listState) {
                itemsIndexed(state.queue, key = { index, item -> System.identityHashCode(item) * 31 + index }) { index, item ->
                    ReorderableItem(reorder, key = System.identityHashCode(item) * 31 + index) {
                        val current = index == state.currentIndex
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                .clickable { actions.onPlayIndex(index) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(item.artworkMrl, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), 96)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                                Text(item.artistName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { actions.onRemoveQueueItem(index) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) { Icon(Icons.Filled.DragHandle, stringResource(R.string.music_reorder)) }
                        }
                    }
                }
            }
        }
    }
}
