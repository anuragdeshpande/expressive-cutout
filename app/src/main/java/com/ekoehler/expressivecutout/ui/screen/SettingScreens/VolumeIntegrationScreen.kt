package com.ekoehler.expressivecutout.ui.screen

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekoehler.expressivecutout.R
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.IslandPreviewBus
import com.ekoehler.expressivecutout.core.VolumeBus
import com.ekoehler.expressivecutout.core.VolumeState
import com.ekoehler.expressivecutout.permissions.Permissions
import com.ekoehler.expressivecutout.ui.AppViewModel
import com.ekoehler.expressivecutout.ui.components.ColorPickerCard
import com.ekoehler.expressivecutout.ui.screen.AdjustableSlider
import com.ekoehler.expressivecutout.ui.screen.SettingsToggleCard
import kotlin.math.roundToInt

/** Sample volume state shown on the live cutout preview while this screen is active. */
private val PREVIEW_VOLUME_STATE = VolumeState(
    mediaVolume = 10,
    maxMediaVolume = 15,
    ringerMode = AudioManager.RINGER_MODE_NORMAL,
    isLiveCaptionEnabled = false,
)

/** The blue accent used by default for volume overlay styling. */
private val VOLUME_DEFAULT_ACCENT = Color(0xFF60A5FA)

/**
 * Settings screen for the Volume Overlay integration: toggle master enablement, hardware key
 * interception, ringer mode controls, live captions button, auto-expand, dismiss duration,
 * and icon container color.
 */
@Composable
internal fun VolumeIntegrationScreen(
    viewModel: AppViewModel,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val settings by viewModel.volumeIntegration.collectAsStateWithLifecycle()
    var durationSlider by remember(settings.dismissDurationSeconds) {
        mutableFloatStateOf(settings.dismissDurationSeconds.toFloat())
    }
    var stepSizeSlider by remember(settings.volumeStepSize) {
        mutableFloatStateOf(settings.volumeStepSize.toFloat())
    }

    // Pin the real overlay open with a volume preview while on this screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        fun refresh() {
            val granted = Permissions.isAccessibilityGranted(context)
            IslandPreviewBus.setActive(granted)
            if (granted) {
                IslandPreviewBus.setExpandedPreview(null)
                val currentVol = VolumeBus.state.value.takeIf { it.maxMediaVolume > 0 } ?: PREVIEW_VOLUME_STATE
                IslandPreviewBus.setPreviewSignal(CutoutSignal.Volume(currentVol))
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> refresh()
                Lifecycle.Event.ON_PAUSE -> {
                    IslandPreviewBus.setActive(false)
                    IslandPreviewBus.setExpandedPreview(false)
                    IslandPreviewBus.setPreviewSignal(null)
                }
                else -> Unit
            }
        }
        refresh()
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            IslandPreviewBus.setActive(false)
            IslandPreviewBus.setExpandedPreview(false)
            IslandPreviewBus.setPreviewSignal(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Master enable toggle
        SettingsToggleCard(
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            title = stringResource(R.string.integration_volume_enabled_title),
            description = stringResource(R.string.integration_volume_enabled_desc),
            checked = settings.enabled,
            onCheckedChange = viewModel::setVolumeEnabled,
        )

        AnimatedVisibility(visible = settings.enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Hardware volume key interception
                SettingsToggleCard(
                    shape = RoundedCornerShape(4.dp),
                    title = stringResource(R.string.integration_volume_intercept_title),
                    description = stringResource(R.string.integration_volume_intercept_desc),
                    checked = settings.interceptVolumeKeys,
                    onCheckedChange = viewModel::setVolumeInterceptVolumeKeys,
                )

                // Ringer mode switcher toggle
                SettingsToggleCard(
                    shape = RoundedCornerShape(4.dp),
                    title = stringResource(R.string.integration_volume_ringer_modes_title),
                    description = stringResource(R.string.integration_volume_ringer_modes_desc),
                    checked = settings.showRingerModes,
                    onCheckedChange = viewModel::setVolumeShowRingerModes,
                )

                // Live caption button toggle
                SettingsToggleCard(
                    shape = RoundedCornerShape(4.dp),
                    title = stringResource(R.string.integration_volume_live_caption_title),
                    description = stringResource(R.string.integration_volume_live_caption_desc),
                    checked = settings.showLiveCaption,
                    onCheckedChange = viewModel::setVolumeShowLiveCaption,
                )

                // Expand island on volume change
                SettingsToggleCard(
                    shape = RoundedCornerShape(4.dp),
                    title = stringResource(R.string.integration_volume_expand_on_key_title),
                    description = stringResource(R.string.integration_volume_expand_on_key_desc),
                    checked = settings.expandOnVolumeKey,
                    onCheckedChange = viewModel::setVolumeExpandOnVolumeKey,
                )

                // Auto-dismiss duration slider card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AdjustableSlider(
                            label = stringResource(R.string.integration_volume_dismiss_title),
                            valueText = "${durationSlider.roundToInt()} s",
                            value = durationSlider,
                            valueRange = 1f..10f,
                            step = 1f,
                            onValueChange = { durationSlider = it },
                            onCommit = { viewModel.setVolumeDismissDurationSeconds(durationSlider.roundToInt()) },
                        )
                    }
                }

                // Volume step size slider card
                val liveVolumeState by VolumeBus.state.collectAsStateWithLifecycle()
                val maxVol = liveVolumeState.maxMediaVolume.takeIf { it > 0 } ?: 25
                val percentPerStep = 100f / maxVol
                val totalPercent = (stepSizeSlider.roundToInt() * percentPerStep).roundToInt().coerceIn(0, 100)
                val stepCount = stepSizeSlider.roundToInt()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AdjustableSlider(
                            label = stringResource(R.string.integration_volume_step_title),
                            valueText = if (stepCount == 1) "1 step ($totalPercent%)" else "$stepCount steps ($totalPercent%)",
                            value = stepSizeSlider,
                            valueRange = 1f..10f,
                            step = 1f,
                            onValueChange = { stepSizeSlider = it },
                            onCommit = { viewModel.setVolumeStepSize(stepSizeSlider.roundToInt()) },
                        )
                    }
                }

                // Dynamic volume adjustment toggle
                SettingsToggleCard(
                    shape = RoundedCornerShape(4.dp),
                    title = stringResource(R.string.integration_volume_dynamic_step_title),
                    description = stringResource(R.string.integration_volume_dynamic_step_desc),
                    checked = settings.dynamicVolumeStep,
                    onCheckedChange = viewModel::setVolumeDynamicVolumeStep,
                )

                // Icon container color picker
                ColorPickerCard(
                    label = stringResource(R.string.shows_when_empty_container_color),
                    selected = settings.iconContainerColor,
                    onSelect = viewModel::setVolumeIconContainerColor,
                    defaultLabel = stringResource(R.string.label_default),
                    defaultColor = VOLUME_DEFAULT_ACCENT,
                    shape = RoundedCornerShape(32.dp),
                )
            }
        }
    }
}
