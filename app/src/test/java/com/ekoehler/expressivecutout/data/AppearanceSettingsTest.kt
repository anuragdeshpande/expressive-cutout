package com.ekoehler.expressivecutout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying appearance settings defaults and configuration.
 */
class AppearanceSettingsTest {

    @Test
    fun `default appearance settings enable full notification text`() {
        val settings = AppearanceSettings()
        assertTrue(
            "Show full notification text should default to true",
            settings.showFullNotificationText,
        )
    }

    @Test
    fun `custom appearance settings preserve full notification text toggle`() {
        val settings = AppearanceSettings(showFullNotificationText = false)
        assertFalse(
            "Show full notification text should be configurable",
            settings.showFullNotificationText,
        )
    }

    @Test
    fun `default appearance settings enable virtual LED indicator`() {
        val settings = AppearanceSettings()
        assertTrue(
            "Show virtual LED indicator should default to true",
            settings.showVirtualLed,
        )
    }

    @Test
    fun `custom appearance settings preserve virtual LED indicator toggle`() {
        val settings = AppearanceSettings(showVirtualLed = false)
        assertFalse(
            "Show virtual LED indicator should be configurable",
            settings.showVirtualLed,
        )
    }
}
