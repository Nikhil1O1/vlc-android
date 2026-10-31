package org.videolan.vlc.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.videolan.vlc.gui.video.SubtitlePositionHelper

class SubtitlePositionHelperTest {
    private val delta = 0.001f

    @Test
    fun clampKeepsValuesInRange() {
        assertEquals(0.2f, SubtitlePositionHelper.clamp(0.2f), delta)
        assertEquals(SubtitlePositionHelper.MAX_OFFSET, SubtitlePositionHelper.clamp(5f), delta)
        assertEquals(SubtitlePositionHelper.MIN_OFFSET, SubtitlePositionHelper.clamp(-5f), delta)
    }

    @Test
    fun clampTreatsNaNAsDefault() {
        assertEquals(0f, SubtitlePositionHelper.clamp(Float.NaN), delta)
    }

    @Test
    fun positiveOffsetMovesUp() {
        assertEquals(-200f, SubtitlePositionHelper.translationFor(0.25f, 800), delta)
    }

    @Test
    fun negativeOffsetMovesDown() {
        assertEquals(80f, SubtitlePositionHelper.translationFor(-0.1f, 800), delta)
    }

    @Test
    fun translationScalesWithLayoutHeight() {
        // same fraction, rotated layout: the pixel shift follows the new height
        assertEquals(-100f, SubtitlePositionHelper.translationFor(0.2f, 500), delta)
        assertEquals(-216f, SubtitlePositionHelper.translationFor(0.2f, 1080), delta)
    }

    @Test
    fun translationIsClamped() {
        assertEquals(-320f, SubtitlePositionHelper.translationFor(3f, 800), delta)
    }
}
