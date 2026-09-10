package com.ekoehler.expressivecutout.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the device's live screen brightness and auto-brightness state.
 *
 * @param brightnessPercent The current active screen brightness percentage (0..100).
 * @param targetPercent The target brightness percentage the system is adjusting towards (0..100).
 * @param isAutoBrightness Whether adaptive / automatic brightness is currently active.
 */
data class BrightnessState(
    val brightnessPercent: Int = 0,
    val targetPercent: Int = 0,
    val isAutoBrightness: Boolean = false,
)

/**
 * Publishes the live screen brightness state across the app so the overlay and preview
 * can display real-time animated brightness adjustments.
 */
object BrightnessBus {

    private val _state = MutableStateFlow(BrightnessState())

    /** The live brightness snapshot observed by the overlay controller and UI. */
    val state: StateFlow<BrightnessState> = _state.asStateFlow()

    /** Updates the published brightness state. */
    fun update(state: BrightnessState) {
        _state.value = state
    }

    /** Emits a state change derived from the previous brightness snapshot. */
    fun update(transform: (BrightnessState) -> BrightnessState) {
        _state.value = transform(_state.value)
    }
}

/**
 * Translates raw framework brightness values (linear float `0.0..1.0` or integer `0..255`)
 * to the perceptual `0..100%` scale used by the Android system brightness slider and human vision.
 *
 * Uses the standard AOSP Hybrid Log-Gamma (HLG) transfer curve from `BrightnessUtils`
 * so displayed percentage values match what the user sees in Quick Settings.
 */
object BrightnessTranslation {

    private const val HLG_A = 0.17883277f
    private const val HLG_B = 0.28466892f
    private const val HLG_C = 0.55991073f
    private const val HLG_SCALE = 12f

    /**
     * Converts a raw linear brightness float (0.0..1.0) to a perceptual percentage (0..100).
     */
    fun linearToPercent(linearVal: Float): Int {
        if (linearVal <= 0f) return 0
        if (linearVal >= 1f) return 100

        val x = linearVal * HLG_SCALE
        val perceptualFraction = if (x <= 1f) {
            kotlin.math.sqrt(x) * 0.5f
        } else {
            HLG_A * kotlin.math.ln(x - HLG_B) + HLG_C
        }
        return (perceptualFraction * 100f).toInt().coerceIn(0, 100)
    }

    /**
     * Converts a raw system settings brightness integer (0..255) to a perceptual percentage (0..100).
     */
    fun rawIntToPercent(intVal: Int): Int {
        if (intVal <= 0) return 0
        if (intVal >= 255) return 100
        return linearToPercent(intVal / 255f)
    }
}
