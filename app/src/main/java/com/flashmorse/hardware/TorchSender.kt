package com.flashmorse.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.flashmorse.core.Morse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class TorchSender(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String? = runCatching {
        manager.cameraIdList.firstOrNull { id ->
            val characteristics = manager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()
    val available get() = cameraId != null
    fun off() { cameraId?.let { runCatching { manager.setTorchMode(it, false) } } }
    suspend fun send(message: String, wpm: Int, onPulse: (Boolean) -> Unit) =
        withContext(Dispatchers.Default) {
            val id = cameraId ?: error("No rear flashlight")
            try {
                val unit = 1200L / wpm.coerceIn(1, 25)
                for (pulse in Morse.pulses(message)) {
                    manager.setTorchMode(id, pulse.on)
                    onPulse(pulse.on)
                    delay(pulse.units * unit)
                }
            } finally { off(); onPulse(false) }
        }
}
