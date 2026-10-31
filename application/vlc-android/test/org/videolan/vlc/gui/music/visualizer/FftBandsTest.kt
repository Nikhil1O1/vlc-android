package org.videolan.vlc.gui.music.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FftBandsTest {
    private fun fftWithTone(bins: Int, bin: Int, amplitude: Int): ByteArray =
        ByteArray(bins * 2).also { it[2 * bin] = amplitude.toByte() }

    @Test fun silenceGivesZeroBands() {
        val b = FftBands.bands(ByteArray(1024), 48000)
        assertEquals(FftBands.BAND_COUNT, b.size)
        assertTrue(b.all { it == 0f })
    }

    @Test fun toneLightsUpTheMatchingBandOnly() {
        // 512 bins over 24 kHz = ~46.9 Hz per bin; bin 21 is ~984 Hz.
        val b = FftBands.bands(fftWithTone(512, 21, 100), 48000)
        val peak = b.indices.maxByOrNull { b[it] }!!
        assertTrue(b[peak] > 0.5f)
        // log-spaced from 40 Hz to 16 kHz, ~984 Hz sits in the middle of the range
        assertTrue(peak in 18..32)
        assertEquals(0f, b[0], 0f)
        assertEquals(0f, b[FftBands.BAND_COUNT - 1], 0f)
    }

    @Test fun levelIsClamped() {
        assertEquals(0f, FftBands.magnitudeToLevel(0f), 0f)
        assertEquals(1f, FftBands.magnitudeToLevel(180f), 0f)
    }

    @Test fun waveformIsCentredAndResampled() {
        val w = FftBands.waveform(ByteArray(1024) { 128.toByte() })
        assertEquals(FftBands.WAVE_POINTS, w.size)
        assertTrue(w.all { it == 0f })
        val loud = FftBands.waveform(ByteArray(1024) { 255.toByte() })
        assertTrue(loud.all { it > 0.9f })
    }

    @Test fun smoothingAttacksFastAndDecaysSlowly() {
        val up = FftBands.smooth(floatArrayOf(0.2f), floatArrayOf(0.9f))
        assertEquals(0.9f, up[0], 0f)
        val down = FftBands.smooth(floatArrayOf(0.9f), floatArrayOf(0f))
        assertTrue(down[0] in 0.5f..0.89f)
    }
}
