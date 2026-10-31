package org.videolan.vlc.gui.music.visualizer

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow

/** Pure helpers that turn the raw bytes from android.media.audiofx.Visualizer into drawable numbers. */
object FftBands {
    const val BAND_COUNT = 48
    const val WAVE_POINTS = 128
    private const val MIN_HZ = 40f
    private const val MAX_HZ = 16000f

    /**
     * Visualizer FFT layout: [0] = DC real, [1] = Nyquist real, then (real, imag) pairs for bins 1..n/2-1.
     * Returns [bandCount] values in 0..1, log-spaced in frequency, on a dB scale.
     */
    fun bands(fft: ByteArray, sampleRateHz: Int, bandCount: Int = BAND_COUNT): FloatArray {
        val out = FloatArray(bandCount)
        val bins = fft.size / 2
        if (bins < 2 || sampleRateHz <= 0) return out
        val hzPerBin = sampleRateHz / 2f / bins
        val maxHz = minOf(MAX_HZ, sampleRateHz / 2f)
        val ratio = (maxHz / MIN_HZ).pow(1f / bandCount)
        var lo = MIN_HZ
        for (b in 0 until bandCount) {
            val hi = lo * ratio
            val first = (lo / hzPerBin).toInt().coerceIn(1, bins - 1)
            val last = (hi / hzPerBin).toInt().coerceIn(first, bins - 1)
            var peak = 0f
            for (k in first..last) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                peak = maxOf(peak, hypot(re, im))
            }
            out[b] = magnitudeToLevel(peak)
            lo = hi
        }
        return out
    }

    /** Magnitude (0..~181 for 8-bit data) to 0..1 on a dB scale. */
    fun magnitudeToLevel(magnitude: Float): Float {
        if (magnitude <= 0f) return 0f
        val db = 20f * (ln(magnitude) / ln(10f))
        return ((db - 6f) / 36f).coerceIn(0f, 1f)
    }

    /** Waveform bytes are unsigned and centred on 128. Returns [points] values in -1..1. */
    fun waveform(wave: ByteArray, points: Int = WAVE_POINTS): FloatArray {
        val out = FloatArray(points)
        if (wave.isEmpty()) return out
        for (i in 0 until points) {
            val src = (i.toLong() * wave.size / points).toInt()
            out[i] = ((wave[src].toInt() and 0xFF) - 128) / 128f
        }
        return out
    }

    /** Fast attack, slow decay so bars jump up with the beat and fall back smoothly. */
    fun smooth(previous: FloatArray, current: FloatArray, decay: Float = 0.82f): FloatArray {
        val out = FloatArray(current.size)
        for (i in current.indices) {
            val prev = previous.getOrElse(i) { 0f }
            out[i] = if (current[i] >= prev) current[i] else prev * decay + current[i] * (1f - decay)
        }
        return out
    }

    /** Loudness of the lowest bands, used by the glow style. */
    fun bass(bands: FloatArray): Float = if (bands.isEmpty()) 0f else bands.take(6).average().toFloat()
}
