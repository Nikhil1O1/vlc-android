package org.videolan.vlc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.resources.SubtitlePreset
import org.videolan.resources.SubtitleStyle
import org.videolan.vlc.gui.dialogs.SubtitleStyleEditor
import org.videolan.vlc.gui.video.SubtitlePositionHelper

class SubtitleStyleEditorTest {
    private fun editor(carried: Boolean = false) = SubtitleStyleEditor(SubtitleStyle.DEFAULT, 0f, carried)

    @Test
    fun noChangeNeedsNoReload() {
        assertFalse(editor().reloadNeeded)
    }

    @Test
    fun styleChangeNeedsExactlyOneReload() {
        val e = editor()
        assertTrue(e.setStyle(SubtitlePreset.YELLOW.style))
        assertTrue(e.reloadNeeded)
        e.markReloaded()
        // closing again (or a second dismiss) must not reload twice
        assertFalse(e.reloadNeeded)
    }

    @Test
    fun positionChangeNeverNeedsReload() {
        val e = editor()
        e.setOffset(0.3f)
        e.setOffset(-0.05f)
        assertFalse(e.reloadNeeded)
    }

    @Test
    fun changingBackToTheOriginalStyleNeedsNoReload() {
        val e = editor()
        e.setStyle(SubtitleStyle.DEFAULT.copy(color = SubtitleStyle.YELLOW))
        e.setStyle(SubtitleStyle.DEFAULT)
        assertFalse(e.reloadNeeded)
    }

    @Test
    fun settingTheSameStyleReportsNoChange() {
        val e = editor()
        assertFalse(e.setStyle(SubtitleStyle.DEFAULT))
        assertFalse(e.reloadNeeded)
    }

    @Test
    fun changeAfterAReloadIsMeasuredFromTheReloadedStyle() {
        val e = editor()
        e.setStyle(SubtitlePreset.YELLOW.style)
        e.markReloaded()
        // back to default is now a real change: the player runs with yellow
        e.setStyle(SubtitleStyle.DEFAULT)
        assertTrue(e.reloadNeeded)
    }

    @Test
    fun aChangeCarriedOverARotationStillReloads() {
        // the panel was destroyed by a rotation after a change: the new panel sees the new style as its baseline
        val e = editor(carried = true)
        assertTrue(e.reloadNeeded)
        e.markReloaded()
        assertFalse(e.reloadNeeded)
    }

    @Test
    fun offsetIsClamped() {
        val e = editor()
        assertEquals(SubtitlePositionHelper.MAX_OFFSET, e.setOffset(9f), 0.0001f)
        assertEquals(SubtitlePositionHelper.MIN_OFFSET, e.setOffset(-9f), 0.0001f)
        assertEquals(SubtitlePositionHelper.MAX_OFFSET, SubtitleStyleEditor(SubtitleStyle.DEFAULT, 5f).offset, 0.0001f)
    }
}
