package org.videolan.vlc.gui.music.nowplaying

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.R

/** The look of the full now playing screen. Persisted by [key] under KEY_MUSIC_PLAYER_STYLE. */
enum class NowPlayingStyle(val key: String, @StringRes val labelRes: Int) {
    Normal("normal", R.string.music_style_normal),
    FullCover("full_cover", R.string.music_style_full_cover),
    Gradient("gradient", R.string.music_style_gradient),
    Expressive("expressive", R.string.music_style_expressive);

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
