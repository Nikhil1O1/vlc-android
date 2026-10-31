package org.videolan.vlc.gui.dialogs

import org.videolan.resources.SubtitleStyle
import org.videolan.vlc.gui.video.SubtitlePositionHelper

/**
 * The state behind the subtitle style panel, kept free of Android so the rules are testable.
 *
 * libVLC only reads the style when its instance is created, so a style change needs one reload of the
 * player when the panel closes ([reloadNeeded]). The position is applied live and never needs it.
 *
 * @param initialStyle the style the player currently runs with
 * @param carriedChange true when a change made before a rotation (that destroyed the previous panel)
 * has not been reloaded yet
 */
class SubtitleStyleEditor(
    initialStyle: SubtitleStyle,
    initialOffset: Float,
    private var carriedChange: Boolean = false
) {
    /** The style the running player was created with. Changes are measured against it */
    private var baseline = initialStyle

    var style: SubtitleStyle = initialStyle
        private set

    var offset: Float = SubtitlePositionHelper.clamp(initialOffset)
        private set

    /** @return true if [newStyle] differs from the current style and must be saved */
    fun setStyle(newStyle: SubtitleStyle): Boolean {
        if (newStyle == style) return false
        style = newStyle
        return true
    }

    /** @return the clamped offset that was set */
    fun setOffset(newOffset: Float): Float {
        offset = SubtitlePositionHelper.clamp(newOffset)
        return offset
    }

    /** True when the player must be reloaded to show the style. Changing a value and changing it back does not count */
    val reloadNeeded: Boolean get() = carriedChange || style != baseline

    /** To call once the reload was requested */
    fun markReloaded() {
        carriedChange = false
        baseline = style
    }
}
