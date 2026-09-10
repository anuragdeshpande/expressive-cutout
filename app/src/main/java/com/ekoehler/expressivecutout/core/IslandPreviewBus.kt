package com.ekoehler.expressivecutout.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Signals that the settings screen wants the real overlay pinned open so size/position/radius
 * changes can be seen live on the actual cutout. [expandedPreview] mirrors which tab the user
 * is editing, so the pinned island shows the matching (collapsed or expanded) state.
 */
object IslandPreviewBus {

    private val mutableActive = MutableStateFlow(false)
    val active: StateFlow<Boolean> = mutableActive

    private val mutableExpandedPreview = MutableStateFlow<Boolean?>(false)
    val expandedPreview: StateFlow<Boolean?> = mutableExpandedPreview

    private val mutablePreviewSignal = MutableStateFlow<CutoutSignal?>(null)
    val previewSignal: StateFlow<CutoutSignal?> = mutablePreviewSignal

    private val mutablePreviewStackSignals = MutableStateFlow<List<CutoutSignal>>(emptyList())
    val previewStackSignals: StateFlow<List<CutoutSignal>> = mutablePreviewStackSignals

    private val mutableActivePreviewCount = MutableStateFlow(0)
    val activePreviewCount: StateFlow<Int> = mutableActivePreviewCount

    private val mutableRotationCycle = MutableStateFlow(0)
    val rotationCycle: StateFlow<Int> = mutableRotationCycle

    private val mutableCycleRequest = MutableStateFlow(0)
    val cycleRequest: StateFlow<Int> = mutableCycleRequest

    /** Increments to signal that the preview stack was cycled via flick gesture. */
    fun notifyRotated() {
        mutableRotationCycle.value++
    }

    /** Increments to signal that the real overlay should animate its deck cycle. */
    fun requestCycle() {
        mutableCycleRequest.value++
    }

    fun updateActivePreviewCount(count: Int) {
        mutableActivePreviewCount.value = count
    }

    fun setActive(value: Boolean) {
        mutableActive.value = value
    }

    fun setExpandedPreview(value: Boolean?) {
        mutableExpandedPreview.value = value
    }

    fun setPreviewSignal(signal: CutoutSignal?) {
        mutablePreviewSignal.value = signal
        if (signal != null) {
            mutablePreviewStackSignals.value = listOf(signal)
        } else {
            mutablePreviewStackSignals.value = emptyList()
        }
    }

    fun setPreviewStack(signals: List<CutoutSignal>) {
        mutablePreviewStackSignals.value = signals
        mutablePreviewSignal.value = signals.firstOrNull()
    }
}
