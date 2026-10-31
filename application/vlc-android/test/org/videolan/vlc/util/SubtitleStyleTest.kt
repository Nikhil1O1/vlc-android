package org.videolan.vlc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.resources.SubtitlePreset
import org.videolan.resources.SubtitleStyle
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SIZE

class SubtitleStyleTest {

    /** Read a style back from the values a style was written with, as the preferences would */
    private fun roundTrip(style: SubtitleStyle): SubtitleStyle {
        val values = style.toPrefValues()
        return SubtitleStyle.read(
            { key, def -> values[key] as? String ?: def },
            { key, def -> values[key] as? Int ?: def },
            { key, def -> values[key] as? Boolean ?: def }
        )
    }

    @Test
    fun defaultsMatchSettingsXml() {
        val d = SubtitleStyle.DEFAULT
        assertEquals("16", d.size)
        assertFalse(d.backgroundEnabled)
        // the background must be black, not the white fallback used when the key is missing
        assertEquals(SubtitleStyle.BLACK, d.backgroundColor)
        assertTrue(d.shadowEnabled)
        assertEquals(128, d.shadowOpacity)
        assertTrue(d.outlineEnabled)
    }

    @Test
    fun missingPreferencesFallBackToDefaults() {
        val style = SubtitleStyle.read({ _, def -> def }, { _, def -> def }, { _, def -> def })
        assertEquals(SubtitleStyle.DEFAULT, style)
    }

    @Test
    fun writtenValuesReadBackIdentically() {
        SubtitlePreset.entries.forEach { assertEquals(it.name, it.style, roundTrip(it.style)) }
    }

    @Test
    fun presetIdsMatchTheSettingsScreenValues() {
        assertEquals(listOf("0", "1", "2", "3", "4", "5"), SubtitlePreset.entries.map { it.id })
        assertEquals(SubtitlePreset.YELLOW, SubtitlePreset.fromId("4"))
        assertNull(SubtitlePreset.fromId("-1"))
        assertNull(SubtitlePreset.fromId(null))
    }

    @Test
    fun yellowOnBoxPreset() {
        val values = SubtitlePreset.YELLOW_BOXED.style.toPrefValues()
        assertEquals(SubtitleStyle.YELLOW, values[KEY_SUBTITLES_COLOR])
        assertEquals(true, values[KEY_SUBTITLES_BACKGROUND])
        assertEquals(128, values[KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY])
        assertEquals(false, values[KEY_SUBTITLES_SHADOW])
    }

    @Test
    fun largestBoxedPresetDropsOutlineAndShadow() {
        val values = SubtitlePreset.LARGEST_BOXED.style.toPrefValues()
        assertEquals("10", values[KEY_SUBTITLES_SIZE])
        assertEquals(false, values[KEY_SUBTITLES_OUTLINE])
        assertEquals(false, values[KEY_SUBTITLES_SHADOW])
    }

    @Test
    fun sizeStepsAreOrderedSmallestToLargest() {
        // the stored value is a divisor: a smaller number means bigger text
        assertEquals(SubtitleStyle.SIZE_VALUES.map { it.toInt() }.sortedDescending(), SubtitleStyle.SIZE_VALUES.map { it.toInt() })
    }

    @Test
    fun unknownSizeFallsBackToNormalStep() {
        val style = SubtitleStyle(size = "99")
        assertEquals(SubtitleStyle.SIZE_VALUES.indexOf("16"), style.sizeIndex)
        assertEquals(5, SubtitleStyle(size = "13").sizeIndex)
    }
}
