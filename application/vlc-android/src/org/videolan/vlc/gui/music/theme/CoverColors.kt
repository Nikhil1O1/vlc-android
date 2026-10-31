package org.videolan.vlc.gui.music.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.vlc.gui.helpers.AudioUtil

/**
 * Builds a Material 3 [ColorScheme] from the dominant colour of a cover, cached per cover/theme.
 */
object CoverColorRepository {
    private const val SAMPLE_SIZE = 128
    private val cache = LruCache<String, ColorScheme>(48)

    suspend fun scheme(artworkMrl: String?, dark: Boolean): ColorScheme {
        if (artworkMrl.isNullOrEmpty()) return fallbackColorScheme(dark)
        val key = "$artworkMrl|$dark"
        cache.get(key)?.let { return it }
        val scheme = withContext(Dispatchers.IO) {
            val seed = AudioUtil.readCoverBitmap(artworkMrl, SAMPLE_SIZE)?.let(::seedColor)
            if (seed == null) fallbackColorScheme(dark) else dynamicColorScheme(seed, dark)
        }
        cache.put(key, scheme)
        return scheme
    }

    private fun seedColor(bitmap: Bitmap): Color? {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch ?: palette.mutedSwatch
        return swatch?.let { Color(it.rgb) }
    }
}
