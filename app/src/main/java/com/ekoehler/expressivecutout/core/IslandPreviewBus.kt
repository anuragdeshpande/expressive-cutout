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

    fun setActive(value: Boolean) {
        mutableActive.value = value
    }

    fun setExpandedPreview(value: Boolean?) {
        mutableExpandedPreview.value = value
    }

    fun setPreviewSignal(signal: CutoutSignal?) {
        mutablePreviewSignal.value = signal
    }
}
