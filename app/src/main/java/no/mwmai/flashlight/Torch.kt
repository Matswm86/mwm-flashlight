package no.mwmai.flashlight

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the camera service reports about the flash right now. */
data class TorchState(val on: Boolean = false, val busy: Boolean = false)

/**
 * The camera flash used as a torch. Needs no permission: Android lets any app
 * switch the flash on and off without opening the camera.
 */
class Torch(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)

    /** Back camera with a flash, else any camera with one, else null. */
    private val cameraId: String? = runCatching {
        val withFlash = manager.cameraIdList.filter {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }.getOrNull()

    val available: Boolean get() = cameraId != null

    /** Number of brightness steps. 1 means the phone only has one brightness. */
    // Brightness control arrived in Android 13, and only on phones whose flash
    // driver supports it; everything else reports a single level. The keys
    // themselves do not exist before Android 13, hence the check up front.
    val maxLevel: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        strength(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
    } else {
        1
    }

    val defaultLevel: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        strength(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL)
    } else {
        1
    }

    private val _state = MutableStateFlow(TorchState())
    val state: StateFlow<TorchState> = _state.asStateFlow()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) _state.value = TorchState(on = enabled, busy = false)
        }

        // Another app has the camera open, so the flash cannot be used.
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) _state.value = TorchState(on = false, busy = true)
        }
    }

    fun listen() {
        if (available) manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun unlisten() {
        if (available) manager.unregisterTorchCallback(callback)
    }

    /** Switches the flash; returns false when the camera service refused. */
    fun set(on: Boolean, level: Int = maxLevel): Boolean {
        val id = cameraId ?: return false
        return try {
            if (on && maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.turnOnTorchWithStrengthLevel(id, level.coerceIn(1, maxLevel))
            } else {
                manager.setTorchMode(id, on)
            }
            true
        } catch (e: CameraAccessException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun strength(key: CameraCharacteristics.Key<Int>): Int {
        val id = cameraId ?: return 1
        return runCatching { manager.getCameraCharacteristics(id).get(key) }.getOrNull()?.coerceAtLeast(1) ?: 1
    }
}
