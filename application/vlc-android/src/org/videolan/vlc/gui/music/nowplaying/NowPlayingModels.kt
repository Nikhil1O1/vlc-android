package org.videolan.vlc.gui.music.nowplaying

import androidx.compose.runtime.Immutable
import org.videolan.medialibrary.interfaces.media.MediaWrapper

/** The look of the full now playing screen. Persisted by [key] under KEY_MUSIC_PLAYER_STYLE. */
enum class NowPlayingStyle(val key: String, val label: String) {
    Normal("normal", "Normal"),
    FullCover("full_cover", "Full cover"),
    Gradient("gradient", "Gradient"),
    Expressive("expressive", "Expressive");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Expressive
    }
}

@Immutable
data class NowPlayingUiState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkMrl: String? = null,
    val playing: Boolean = false,
    val timeMs: Long = 0L,
    val lengthMs: Long = 0L,
    val queue: List<MediaWrapper> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: Int = 0,
    val shuffling: Boolean = false,
    val favorite: Boolean = false,
    val style: NowPlayingStyle = NowPlayingStyle.Expressive,
)

class NowPlayingActions(
    val onTogglePlayer: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onPlayIndex: (Int) -> Unit,
    val onRepeat: () -> Unit,
    val onShuffle: () -> Unit,
    val onFavorite: () -> Unit,
    val onOptions: () -> Unit,
    val onVolume: () -> Unit,
    val onStyle: (NowPlayingStyle) -> Unit,
    val onMoveQueueItem: (from: Int, to: Int) -> Unit,
    val onRemoveQueueItem: (Int) -> Unit,
)
