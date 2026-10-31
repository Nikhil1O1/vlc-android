package org.videolan.vlc.gui.audio

import android.view.View
import org.videolan.vlc.viewmodels.BookmarkModel
import org.videolan.vlc.viewmodels.PlaylistModel

/**
 * What [org.videolan.vlc.gui.AudioPlayerContainerActivity] needs from the audio player fragment
 * living in its bottom sheet. Implemented by the classic [AudioPlayer] and by the Compose
 * [org.videolan.vlc.gui.music.nowplaying.NowPlayingFragment].
 */
interface AudioPlayerHost {
    val playlistModel: PlaylistModel
    val bookmarkModel: BookmarkModel

    fun setBottomMargin()
    fun showCover(value: Boolean)
    fun onSlide(slideOffset: Float)
    fun onStateChanged(newState: Int)
    fun showBookmarks()
    fun areBookmarksVisible(): Boolean
    fun showAdvancedOptions(v: View?)
    fun onPlayPauseClick(v: View?)
    fun onNextClick(v: View?)
    fun onPreviousClick(v: View?)
    fun onStopClick(v: View?): Boolean
    fun onResumeToVideoClick()
}
