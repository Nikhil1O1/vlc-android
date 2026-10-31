package org.videolan.resources

import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_SIZE

/**
 * The subtitle style options stored in the preferences. They are the same keys as the ones of the
 * subtitles settings screen so both always show the same values.
 *
 * libVLC only reads them when its instance is created, see [VLCOptions.libOptions].
 *
 * Colors are stored as ARGB ints (as the settings color picker does). Only the RGB part is used by
 * libVLC, the opacity comes from the dedicated opacity fields.
 */
data class SubtitleStyle(
    /** libVLC relative font size divisor: the **smaller** the value, the **bigger** the text. One of [SIZE_VALUES] */
    val size: String = "16",
    val bold: Boolean = false,
    val color: Int = WHITE,
    val colorOpacity: Int = 255,
    val backgroundEnabled: Boolean = false,
    val backgroundColor: Int = BLACK,
    val backgroundOpacity: Int = 255,
    val shadowEnabled: Boolean = true,
    val shadowColor: Int = BLACK,
    val shadowOpacity: Int = 128,
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = BLACK,
    val outlineOpacity: Int = 255
) {

    /** The values to write in the shared preferences, by preference key */
    fun toPrefValues(): Map<String, Any> = mapOf(
        KEY_SUBTITLES_SIZE to size,
        KEY_SUBTITLES_BOLD to bold,
        KEY_SUBTITLES_COLOR to color,
        KEY_SUBTITLES_COLOR_OPACITY to colorOpacity,
        KEY_SUBTITLES_BACKGROUND to backgroundEnabled,
        KEY_SUBTITLES_BACKGROUND_COLOR to backgroundColor,
        KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY to backgroundOpacity,
        KEY_SUBTITLES_SHADOW to shadowEnabled,
        KEY_SUBTITLES_SHADOW_COLOR to shadowColor,
        KEY_SUBTITLES_SHADOW_COLOR_OPACITY to shadowOpacity,
        KEY_SUBTITLES_OUTLINE to outlineEnabled,
        KEY_SUBTITLES_OUTLINE_COLOR to outlineColor,
        KEY_SUBTITLES_OUTLINE_COLOR_OPACITY to outlineOpacity
    )

    /** Index of [size] in [SIZE_VALUES], the nearest "normal" step if the stored value is unknown */
    val sizeIndex: Int get() = SIZE_VALUES.indexOf(size).takeIf { it >= 0 } ?: SIZE_VALUES.indexOf(DEFAULT.size)

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()

        /** Same order and values as the `subtitles_size_values` array: smallest text first */
        val SIZE_VALUES = listOf("40", "32", "25", "19", "16", "13", "10")

        val DEFAULT = SubtitleStyle()

        /**
         * Read a style from preferences, falling back to the defaults for any missing key.
         * [getString], [getInt] and [getBoolean] read a preference or return the provided default.
         */
        fun read(
            getString: (String, String) -> String,
            getInt: (String, Int) -> Int,
            getBoolean: (String, Boolean) -> Boolean
        ): SubtitleStyle {
            val d = DEFAULT
            return SubtitleStyle(
                size = getString(KEY_SUBTITLES_SIZE, d.size),
                bold = getBoolean(KEY_SUBTITLES_BOLD, d.bold),
                color = getInt(KEY_SUBTITLES_COLOR, d.color),
                colorOpacity = getInt(KEY_SUBTITLES_COLOR_OPACITY, d.colorOpacity),
                backgroundEnabled = getBoolean(KEY_SUBTITLES_BACKGROUND, d.backgroundEnabled),
                backgroundColor = getInt(KEY_SUBTITLES_BACKGROUND_COLOR, d.backgroundColor),
                backgroundOpacity = getInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, d.backgroundOpacity),
                shadowEnabled = getBoolean(KEY_SUBTITLES_SHADOW, d.shadowEnabled),
                shadowColor = getInt(KEY_SUBTITLES_SHADOW_COLOR, d.shadowColor),
                shadowOpacity = getInt(KEY_SUBTITLES_SHADOW_COLOR_OPACITY, d.shadowOpacity),
                outlineEnabled = getBoolean(KEY_SUBTITLES_OUTLINE, d.outlineEnabled),
                outlineColor = getInt(KEY_SUBTITLES_OUTLINE_COLOR, d.outlineColor),
                outlineOpacity = getInt(KEY_SUBTITLES_OUTLINE_COLOR_OPACITY, d.outlineOpacity)
            )
        }
    }
}

/**
 * The one-tap presets. [id] matches the values of the `subtitles_presets_values` array used by the
 * subtitles settings screen, so both screens apply exactly the same style.
 */
enum class SubtitlePreset(val id: String, val style: SubtitleStyle) {
    DEFAULT("0", SubtitleStyle.DEFAULT),
    LARGE("1", SubtitleStyle.DEFAULT.copy(size = "13")),
    LARGEST_BOXED(
        "2", SubtitleStyle.DEFAULT.copy(
            size = "10", backgroundEnabled = true, backgroundOpacity = 255,
            shadowEnabled = false, outlineEnabled = false
        )
    ),
    BOXED(
        "3", SubtitleStyle.DEFAULT.copy(
            backgroundEnabled = true, backgroundOpacity = 128, shadowEnabled = false
        )
    ),
    YELLOW("4", SubtitleStyle.DEFAULT.copy(color = SubtitleStyle.YELLOW)),
    YELLOW_BOXED(
        "5", SubtitleStyle.DEFAULT.copy(
            color = SubtitleStyle.YELLOW, backgroundEnabled = true, backgroundOpacity = 128, shadowEnabled = false
        )
    );

    companion object {
        fun fromId(id: String?): SubtitlePreset? = entries.firstOrNull { it.id == id }
    }
}
