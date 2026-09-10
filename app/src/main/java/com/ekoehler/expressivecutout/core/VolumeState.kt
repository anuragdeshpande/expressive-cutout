package com.ekoehler.expressivecutout.core

import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the device's audio volume and ringer mode state.
 *
 * @param mediaVolume The current media stream volume index.
 * @param maxMediaVolume The maximum media stream volume index.
 * @param ringerMode The current system ringer mode ([AudioManager.RINGER_MODE_NORMAL],
 * [AudioManager.RINGER_MODE_VIBRATE], or [AudioManager.RINGER_MODE_SILENT]).
 * @param isLiveCaptionEnabled Whether system Live Caption is currently enabled.
 * @param isMuted Whether the media stream is muted.
 */
data class VolumeState(
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val isLiveCaptionEnabled: Boolean = false,
    val isMuted: Boolean = false,
) {
    /** The media volume percentage between 0 and 100. */
    val mediaVolumePercent: Int
        get() = if (maxMediaVolume > 0) {
            ((mediaVolume.toFloat() / maxMediaVolume) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
}

/**
 * Publishes the live volume and ringer state across the app so the overlay and preview
 * can read current volume changes without querying [AudioManager] per frame.
 */
object VolumeBus {

    private val _state = MutableStateFlow(VolumeState())

    /** The live volume snapshot observed by the overlay controller and UI. */
    val state: StateFlow<VolumeState> = _state.asStateFlow()

    /** Updates the published volume state. */
    fun update(state: VolumeState) {
        _state.value = state
    }

    /** Emits a state change derived from the previous volume snapshot. */
    fun update(transform: (VolumeState) -> VolumeState) {
        _state.value = transform(_state.value)
    }
}
