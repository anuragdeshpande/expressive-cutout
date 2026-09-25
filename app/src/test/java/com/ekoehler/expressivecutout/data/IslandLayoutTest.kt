package com.ekoehler.expressivecutout.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandLayoutTest {

    @Test
    fun testDefaultTopMarginDp() {
        assertEquals(48, IslandDimensions.DEFAULT_TOP_MARGIN_DP)
        assertEquals(48, IslandLayout.DEFAULT_EXPANDED.topMarginDp)
    }

    @Test
    fun testTopMarginClamping() {
        val clampedMin = IslandDimensions.of(
            widthPercent = 90,
            heightDp = 108,
            offsetXDp = 0,
            offsetYDp = 6,
            cornerTopLeftDp = 30,
            cornerTopRightDp = 30,
            cornerBottomLeftDp = 30,
            cornerBottomRightDp = 30,
            topMarginDp = -10,
        )
        assertEquals(IslandDimensions.MIN_TOP_MARGIN_DP, clampedMin.topMarginDp)

        val clampedMax = IslandDimensions.of(
            widthPercent = 90,
            heightDp = 108,
            offsetXDp = 0,
            offsetYDp = 6,
            cornerTopLeftDp = 30,
            cornerTopRightDp = 30,
            cornerBottomLeftDp = 30,
            cornerBottomRightDp = 30,
            topMarginDp = 200,
        )
        assertEquals(IslandDimensions.MAX_TOP_MARGIN_DP, clampedMax.topMarginDp)
    }

    @Test
    fun testCustomTopMargin() {
        val custom = IslandDimensions.of(
            widthPercent = 90,
            heightDp = 108,
            offsetXDp = 0,
            offsetYDp = 6,
            cornerTopLeftDp = 30,
            cornerTopRightDp = 30,
            cornerBottomLeftDp = 30,
            cornerBottomRightDp = 30,
            topMarginDp = 24,
        )
        assertEquals(24, custom.topMarginDp)
    }

    @Test
    fun testAsCallCutoutCarriesTopMargin() {
        val dims = IslandDimensions.of(
            widthPercent = 90,
            heightDp = 108,
            offsetXDp = 0,
            offsetYDp = 6,
            cornerTopLeftDp = 30,
            cornerTopRightDp = 30,
            cornerBottomLeftDp = 30,
            cornerBottomRightDp = 30,
            topMarginDp = 28,
        )
        val callCutout = dims.asCallCutout()
        assertEquals(28, callCutout.topMarginDp)
    }

    /**
     * Verifies that [asSplitHudCutout] computes the expected pill width and horizontal offset
     * when the satellite bubble is parked to the right of the cutout.
     */
    @Test
    fun testAsSplitHudCutoutRightSatellite() {
        val base = IslandLayout.DEFAULT_COLLAPSED
        val splitHud = base.asSplitHudCutout(
            displayWidthDp = 400,
            contentWidthDp = 60f,
            cameraRightEdgeDp = 16f,
            satelliteOnLeft = false,
        )
        // width = 60 + 32 + 4 = 96dp -> 96 * 100 / 400 = 24%
        assertEquals(24, splitHud.widthPercent)
        // offsetX = (4 - 60) / 2 = -28dp (shifted left to clear camera hole)
        assertEquals(-28, splitHud.offsetXDp)
        assertEquals(base.heightDp, splitHud.heightDp)
    }

    /**
     * Verifies that [asSplitHudCutout] shifts the pill to the right when the satellite bubble
     * is positioned to the left of the cutout.
     */
    @Test
    fun testAsSplitHudCutoutLeftSatellite() {
        val base = IslandLayout.DEFAULT_COLLAPSED
        val splitHud = base.asSplitHudCutout(
            displayWidthDp = 400,
            contentWidthDp = 60f,
            cameraRightEdgeDp = 16f,
            satelliteOnLeft = true,
        )
        // width = 60 + 32 + 4 = 96dp -> 24%
        assertEquals(24, splitHud.widthPercent)
        // offsetX = (60 - 4) / 2 = 28dp (shifted right to clear camera hole)
        assertEquals(28, splitHud.offsetXDp)
        assertEquals(base.heightDp, splitHud.heightDp)
    }
}
