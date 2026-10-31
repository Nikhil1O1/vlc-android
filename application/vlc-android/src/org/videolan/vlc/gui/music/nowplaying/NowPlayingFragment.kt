package org.videolan.vlc.gui.music.nowplaying

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.AudioPlayerContainerActivity
import org.videolan.vlc.gui.audio.AudioPlayerHost
import org.videolan.vlc.gui.helpers.BookmarkListDelegate
import org.videolan.vlc.gui.helpers.PlayerOptionsDelegate
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.music.theme.CoverColorRepository
import org.videolan.vlc.gui.music.theme.MusicTheme
import org.videolan.vlc.gui.music.theme.animated
import org.videolan.vlc.gui.music.theme.fallbackColorScheme
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlaylistModel

/**
 * The Compose audio player living in the bottom sheet of [AudioPlayerContainerActivity]:
 * a mini player when collapsed that cross-fades into the full now playing screen when expanded.
 */
class NowPlayingFragment : Fragment(), AudioPlayerHost {

    override val playlistModel: PlaylistModel by lazy(LazyThreadSafetyMode.NONE) { PlaylistModel.get(this) }
    override val bookmarkModel: BookmarkModel by lazy(LazyThreadSafetyMode.NONE) { BookmarkModel.get(requireActivity()) }

    private var slideOffset by mutableFloatStateOf(0f)
    private var sheetState by mutableIntStateOf(0)
    private var optionsDelegate: PlayerOptionsDelegate? = null
    private var bookmarkListDelegate: BookmarkListDelegate? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.now_playing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ComposeView>(R.id.now_playing_compose).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { NowPlayingContent() }
        }
    }

    @androidx.compose.runtime.Composable
    private fun NowPlayingContent() {
        val dark = isSystemInDarkTheme()
        val state by playlistModel.playerState.observeAsState()
        val progress by playlistModel.progress.observeAsState()
        val media: MediaWrapper? = remember(state) { playlistModel.currentMediaWrapper }
        val artwork = media?.artworkMrl
        val scheme by produceState(fallbackColorScheme(dark), artwork, dark) {
            value = CoverColorRepository.scheme(artwork, dark)
        }
        MusicTheme(scheme.animated()) {
            NowPlayingScreen(
                title = state?.title ?: media?.title,
                artist = state?.artist ?: media?.artistName,
                artworkMrl = artwork,
                playing = state?.playing == true,
                timeMs = progress?.time ?: 0L,
                lengthMs = progress?.length ?: 0L,
                slide = slideOffset,
                onTogglePlayer = { (activity as? AudioPlayerContainerActivity)?.slideUpOrDownAudioPlayer() },
                onPlayPause = { onPlayPauseClick(null) },
                onNext = { onNextClick(null) },
                onPrevious = { onPreviousClick(null) },
                onSeek = { playlistModel.setTime(it) },
            )
        }
    }

    // AudioPlayerHost

    override fun setBottomMargin() {}

    override fun showCover(value: Boolean) {}

    override fun onSlide(slideOffset: Float) {
        this.slideOffset = slideOffset.coerceIn(0f, 1f)
    }

    override fun onStateChanged(newState: Int) {
        sheetState = newState
    }

    override fun onPlayPauseClick(v: View?) {
        val activity = activity ?: return
        if (playlistModel.service?.isPausable == false) {
            UiTools.snackerConfirm(activity, getString(R.string.stop_unpaubale), true) { playlistModel.stop() }
            return
        }
        playlistModel.togglePlayPause()
    }

    override fun onStopClick(v: View?): Boolean {
        playlistModel.stop()
        (activity as? AudioPlayerContainerActivity)?.closeMiniPlayer()
        return true
    }

    override fun onNextClick(v: View?) {
        val activity = activity ?: return
        if (!playlistModel.next()) UiTools.snacker(activity, R.string.lastsong, true)
    }

    override fun onPreviousClick(v: View?) {
        val activity = activity ?: return
        if (!playlistModel.previous()) UiTools.snacker(activity, R.string.firstsong)
    }

    override fun onResumeToVideoClick() {
        val activity = activity ?: return
        playlistModel.currentMediaWrapper?.let {
            if (PlaybackService.hasRenderer()) VideoPlayerActivity.startOpened(activity, it.uri, playlistModel.currentMediaPosition)
            else if (playlistModel.hasMedia) {
                it.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
                lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { playlistModel.switchToVideo() }
            }
        }
    }

    override fun showAdvancedOptions(v: View?) {
        if (!isVisible) return
        val delegate = optionsDelegate ?: run {
            val service = playlistModel.service ?: return
            val activity = activity as? AppCompatActivity ?: return
            PlayerOptionsDelegate(activity, service).also {
                it.setBookmarkClickedListener { showBookmarks() }
                optionsDelegate = it
            }
        }
        delegate.show()
    }

    override fun showBookmarks() {
        val service = playlistModel.service ?: return
        val delegate = bookmarkListDelegate ?: BookmarkListDelegate(requireActivity(), service, bookmarkModel, false).also {
            it.seekListener = { forward, long -> playlistModel.jump(forward, long, requireActivity()) }
            it.markerContainer = requireView().findViewById<ConstraintLayout>(R.id.bookmark_marker_container)
            bookmarkListDelegate = it
        }
        delegate.show()
    }

    override fun areBookmarksVisible() = bookmarkListDelegate?.visible == true
}
