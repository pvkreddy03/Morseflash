package com.flashmorse.hardware

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.flashmorse.core.LightDecoder

/** Experimental center-region luminance detector. Camera hardware testing required. */
class CameraMorseAnalyzer(
    private val decoder: LightDecoder,
    private val onReading: (Boolean, String, String, Int) -> Unit
) : ImageAnalysis.Analyzer {
    private var base = 30.0
    private var state = false
    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            var sum = 0L
            var count = 0
            val x0 = image.width * 2 / 5
            val x1 = image.width * 3 / 5
            val y0 = image.height * 2 / 5
            val y1 = image.height * 3 / 5
            for (y in y0 until y1 step 3) {
                for (x in x0 until x1 step 3) {
                    val pos = y * yPlane.rowStride + x * yPlane.pixelStride
                    if (pos < buffer.limit()) {
                        sum += (buffer.get(pos).toInt() and 255).toLong()
                        count++
                    }
                }
            }
            if (count == 0) return
            val brightness = (sum / count).toInt()
            if (!state) base = base * 0.985 + brightness * 0.015
            val on = if (state) brightness > maxOf(65.0, base + 25.0)
                else brightness > maxOf(95.0, base + 45.0)
            state = on
            decoder.accept(on, SystemClock.elapsedRealtime())
            onReading(on, decoder.text, decoder.symbols, brightness)
        } finally { image.close() }
    }
}
