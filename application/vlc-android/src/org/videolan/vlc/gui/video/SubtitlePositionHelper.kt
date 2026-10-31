package org.videolan.vlc.gui.video

import android.view.View

/**
 * Moves the subtitles vertically by translating libVLC's subtitle surface. This is live, unlike the
 * subtitle style which libVLC only reads when the instance is created.
 *
 * The offset is stored as a fraction of the video layout height so it survives rotation and
 * layout size changes. Positive lifts the subtitles, negative lowers them.
 */
object SubtitlePositionHelper {
    const val MIN_OFFSET = -0.10f
    const val MAX_OFFSET = 0.40f

    private const val SUBTITLES_SURFACE_ID = "surface_subtitles"

    fun clamp(offset: Float): Float = if (offset.isNaN()) 0f else offset.coerceIn(MIN_OFFSET, MAX_OFFSET)

    /** @return the translationY in pixels (negative moves up) for the given [offset] */
    fun translationFor(offset: Float, layoutHeight: Int): Float = -clamp(offset) * layoutHeight

    /** Apply [offset] to the subtitle surface found in [videoLayout]. Does nothing until the surface exists. */
    fun apply(videoLayout: View?, offset: Float) {
        val layout = videoLayout ?: return
        // The id comes from the libvlc library resources, look it up by name to not depend on R class transitivity
        val id = layout.resources.getIdentifier(SUBTITLES_SURFACE_ID, "id", layout.context.packageName)
        if (id == 0) return
        layout.findViewById<View>(id)?.translationY = translationFor(offset, layout.height)
    }
}
