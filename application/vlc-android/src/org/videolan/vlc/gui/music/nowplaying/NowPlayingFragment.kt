package org.videolan.vlc.gui.music.nowplaying

import android.media.AudioManager
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import org.videolan.tools.KEY_MUSIC_PLAYER_STYLE
import org.videolan.tools.Settings
import org.videolan.tools.putSingle
import kotlinx.coroutines.launch
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import org.videolan.tools.KEY_VISUALIZER_STYLE
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
    private var playerStyle by mutableStateOf(NowPlayingStyle.Expressive)
    private var sheetState by mutableIntStateOf(0)
    private var visualizerStyle by mutableStateOf(VisualizerStyle.Off)
    private var pendingVisualizer: VisualizerStyle? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val wanted = pendingVisualizer
        pendingVisualizer = null
        if (granted && wanted != null) setVisualizer(wanted)
        else Toast.makeText(requireContext(), R.string.music_visualizer_denied, Toast.LENGTH_SHORT).show()
    }
    private var optionsDelegate: PlayerOptionsDelegate? = null
    private var bookmarkListDelegate: BookmarkListDelegate? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.now_playing, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerStyle = NowPlayingStyle.fromKey(Settings.getInstance(requireContext()).getString(KEY_MUSIC_PLAYER_STYLE, null))
        val savedVisualizer = VisualizerStyle.fromKey(Settings.getInstance(requireContext()).getString(KEY_VISUALIZER_STYLE, null))
        visualizerStyle = if (savedVisualizer != VisualizerStyle.Off && !hasMicPermission()) VisualizerStyle.Off else savedVisualizer
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
        val queue by playlistModel.dataset.observeAsState()
        var tick by remember { mutableIntStateOf(0) }
        val media: MediaWrapper? = remember(state) { playlistModel.currentMediaWrapper }
        val artwork = media?.artworkMrl
        // The database is the source of truth: re-read after every toggle and whenever the track changes,
        // instead of trusting a cached MediaWrapper that other screens may have changed.
        val mediaId = media?.id ?: 0L
        var favoriteTick by remember { mutableIntStateOf(0) }
        val favorite by produceState(false, mediaId, favoriteTick) {
            value = mediaId > 0L && withContext(Dispatchers.IO) { Medialibrary.getInstance().getMedia(mediaId)?.isFavorite == true }
        }
        val scheme by produceState(fallbackColorScheme(dark), artwork, dark) {
            value = CoverColorRepository.scheme(artwork, dark)
        }
        val ui = NowPlayingUiState(
            title = state?.title ?: media?.title,
            artist = state?.artist ?: media?.artistName,
            album = media?.albumName,
            artworkMrl = artwork,
            playing = state?.playing == true,
            timeMs = progress?.time ?: 0L,
            lengthMs = progress?.length ?: 0L,
            queue = queue ?: emptyList(),
            currentIndex = remember(state, queue) { playlistModel.currentMediaPosition },
            repeatMode = remember(state, tick) { playlistModel.repeatType },
            shuffling = remember(state, tick) { playlistModel.shuffling },
            favorite = favorite,
            style = playerStyle,
            visualizer = visualizerStyle,
        )
        val actions = remember {
            NowPlayingActions(
                onTogglePlayer = { (activity as? AudioPlayerContainerActivity)?.slideUpOrDownAudioPlayer() },
                onPlayPause = { onPlayPauseClick(null) },
                onNext = { onNextClick(null) },
                onPrevious = { onPreviousClick(null) },
                onSeek = { playlistModel.setTime(it) },
                onPlayIndex = { playlistModel.play(it) },
                onRepeat = {
                    playlistModel.repeatType = when (playlistModel.repeatType) {
                        PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                        PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                        else -> PlaybackStateCompat.REPEAT_MODE_NONE
                    }
                    tick++
                },
                onShuffle = { playlistModel.shuffle(); tick++ },
                onFavorite = {
                    val id = playlistModel.currentMediaWrapper?.id ?: return@NowPlayingActions
                    if (id <= 0L) return@NowPlayingActions
                    lifecycleScope.launch(Dispatchers.IO) {
                        val fresh = Medialibrary.getInstance().getMedia(id) ?: return@launch
                        fresh.isFavorite = !fresh.isFavorite
                        favoriteTick++
                    }
                },
                onOptions = { showAdvancedOptions(null) },
                onVolume = {
                    requireContext().getSystemService<AudioManager>()
                        ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                },
                onStyle = { style ->
                    playerStyle = style
                    Settings.getInstance(requireContext()).putSingle(KEY_MUSIC_PLAYER_STYLE, style.key)
                },
                onVisualizer = { requestVisualizer(it) },
                onMoveQueueItem = { from, to -> playlistModel.move(from, to) },
                onRemoveQueueItem = { playlistModel.remove(it) },
            )
        }
        MusicTheme(scheme.animated()) {
            NowPlayingScreen(ui, actions, slideOffset)
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setVisualizer(style: VisualizerStyle) {
        visualizerStyle = style
        Settings.getInstance(requireContext()).putSingle(KEY_VISUALIZER_STYLE, style.key)
    }

    private fun requestVisualizer(style: VisualizerStyle) {
        if (style == VisualizerStyle.Off || hasMicPermission()) { setVisualizer(style); return }
        pendingVisualizer = style
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.music_visualizer_permission_title)
            .setMessage(R.string.music_visualizer_permission_message)
            .setPositiveButton(android.R.string.ok) { _, _ -> micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pendingVisualizer = null }
            .show()
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
