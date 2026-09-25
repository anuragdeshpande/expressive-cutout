package com.ekoehler.expressivecutout.overlay

import android.provider.Settings
import androidx.compose.ui.graphics.Color
import com.ekoehler.expressivecutout.core.SystemEventType
import com.ekoehler.expressivecutout.core.VolumeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies HUD identification and split conditions for volume and brightness events. */
class SplitHudTest {

    private fun hudVolumeEvent(): IslandEvent = IslandEvent(
        id = 1L,
        icon = IslandIcon.Vector(SystemEventType.WIFI_CONNECTED.defaultIcon),
        label = "Media",
        accent = Color.White,
        volume = VolumeOverlayOptions(volumeState = VolumeState()),
    )

    private fun hudBrightnessEvent(): IslandEvent = IslandEvent(
        id = 2L,
        icon = IslandIcon.Vector(SystemEventType.WIFI_CONNECTED.defaultIcon),
        label = "Brightness",
        accent = Color.White,
        actionIntentAction = Settings.ACTION_DISPLAY_SETTINGS,
    )

    private fun normalEvent(): IslandEvent = IslandEvent(
        id = 3L,
        icon = IslandIcon.Vector(SystemEventType.WIFI_CONNECTED.defaultIcon),
        label = "Notification",
        accent = Color.White,
    )

    /** Tests that [IslandEvent.isHudEvent] correctly distinguishes HUD events from normal notifications. */
    @Test
    fun testIsHudEventIdentification() {
        assertTrue(hudVolumeEvent().isHudEvent)
        assertTrue(hudBrightnessEvent().isHudEvent)
        assertFalse(normalEvent().isHudEvent)
    }

    /** Tests that [usesSplitHudCutout] returns true only when a HUD event has an active satellite and is collapsed. */
    @Test
    fun testUsesSplitHudCutoutConditions() {
        val volume = hudVolumeEvent()
        val brightness = hudBrightnessEvent()
        val satellite = normalEvent()

        assertTrue(usesSplitHudCutout(event = volume, satellite = satellite, expanded = false))
        assertTrue(usesSplitHudCutout(event = brightness, satellite = satellite, expanded = false))
        assertFalse(usesSplitHudCutout(event = volume, satellite = null, expanded = false))
        assertFalse(usesSplitHudCutout(event = volume, satellite = satellite, expanded = true))
        assertFalse(usesSplitHudCutout(event = normalEvent(), satellite = satellite, expanded = false))
        assertFalse(usesSplitHudCutout(event = null, satellite = satellite, expanded = false))
    }
}
