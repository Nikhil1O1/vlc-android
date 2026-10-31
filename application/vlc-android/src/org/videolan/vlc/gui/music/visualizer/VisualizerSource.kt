package org.videolan.vlc.gui.music.visualizer

import android.media.audiofx.Visualizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Immutable
class VisualizerFrame(val bands: FloatArray, val waveform: FloatArray, val bass: Float)

/**
 * Reads the live sound VLC is playing through its audio session.
 * Emits null once if the session cannot be captured (no permission, offloaded output, ...),
 * so the UI can hide itself. The Visualizer is released as soon as the collector goes away.
 */
object VisualizerSource {
    fun frames(sessionId: Int): Flow<VisualizerFrame?> = callbackFlow {
        if (sessionId <= 0) { trySend(null); close(); return@callbackFlow }
        val visualizer = try {
            Visualizer(sessionId)
        } catch (e: Exception) {
            trySend(null); close(); return@callbackFlow
        }
        try {
            visualizer.enabled = false
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
            var bands = FloatArray(FftBands.BAND_COUNT)
            var wave = FloatArray(FftBands.WAVE_POINTS)
            val rate = minOf(Visualizer.getMaxCaptureRate(), 30_000)
            visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    wave = FftBands.waveform(waveform)
                }

                override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
                    bands = FftBands.smooth(bands, FftBands.bands(fft, samplingRate / 1000))
                    trySend(VisualizerFrame(bands, wave, FftBands.bass(bands)))
                }
            }, rate, true, true)
            visualizer.enabled = true
        } catch (e: Exception) {
            runCatching { visualizer.release() }
            trySend(null); close(); return@callbackFlow
        }
        awaitClose {
            runCatching { visualizer.enabled = false }
            runCatching { visualizer.setDataCaptureListener(null, 0, false, false) }
            runCatching { visualizer.release() }
        }
    }
}
