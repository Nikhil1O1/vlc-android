package org.videolan.vlc.gui.video

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import java.util.WeakHashMap

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

    /** One watcher per subtitle surface, so the offset is re-applied each time its surface is (re)created */
    private val watchers = WeakHashMap<SurfaceView, SurfaceWatcher>()

    fun clamp(offset: Float): Float = if (offset.isNaN()) 0f else offset.coerceIn(MIN_OFFSET, MAX_OFFSET)

    /** @return the translationY in pixels (negative moves up) for the given [offset] */
    fun translationFor(offset: Float, layoutHeight: Int): Float = -clamp(offset) * layoutHeight

    /**
     * Apply [offset] to the subtitle surface found in [videoLayout]. Does nothing until the surface exists.
     *
     * A new playback creates the subtitle surface after the first calls, and a [SurfaceView] ignores a
     * translation set before its surface exists. So the offset is also re-applied whenever that surface is
     * created or resized. When the layout has not been measured yet the offset would compute to 0, so it is
     * retried once after the layout.
     */
    fun apply(videoLayout: View?, offset: Float, retryAfterLayout: Boolean = true) {
        val layout = videoLayout ?: return
        // The id comes from the libvlc library resources, look it up by name to not depend on R class transitivity
        val id = layout.resources.getIdentifier(SUBTITLES_SURFACE_ID, "id", layout.context.packageName)
        if (id == 0) return
        val surface = layout.findViewById<View>(id)
        if (surface is SurfaceView) {
            val watcher = watchers.getOrPut(surface) { SurfaceWatcher(surface, layout).also { surface.holder.addCallback(it) } }
            watcher.offset = offset
        }
        if (layout.height == 0) {
            if (retryAfterLayout) layout.post { apply(layout, offset, retryAfterLayout = false) }
            return
        }
        surface?.translationY = translationFor(offset, layout.height)
    }

    private class SurfaceWatcher(private val surface: SurfaceView, private val layout: View) : SurfaceHolder.Callback {
        @Volatile
        var offset = 0f

        private fun reapply() {
            // After the surface transaction, so the new position is the one that sticks
            surface.post { surface.translationY = translationFor(offset, layout.height) }
        }

        override fun surfaceCreated(holder: SurfaceHolder) = reapply()
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = reapply()
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }
}
