package com.ekoehler.expressivecutout.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewCutoutWidthTest {

    @Test
    fun testShortContentProducesNarrowerWidthThanMax() {
        val widthPct = previewCutoutWidthPercent(
            appName = "Slack",
            headerText = "Slack • Now",
            summaryText = "New message",
            actionLabel = null,
            stackCount = 1,
            isMasked = false,
            displayWidthDp = 400,
            density = 2.5f,
            minWidthPercent = 40,
            maxWidthPercent = 88,
        )
        assertTrue("Expected widthPct ($widthPct) to be < 88", widthPct < 88)
        assertTrue("Expected widthPct ($widthPct) to be >= 40", widthPct >= 40)
    }

    @Test
    fun testLongContentClampsToMax() {
        val widthPct = previewCutoutWidthPercent(
            appName = "Reddit",
            headerText = "Reddit • Now",
            summaryText = "Someone reacted to your post in r/androiddev and commented on your submission",
            actionLabel = "Open Discussion",
            stackCount = 3,
            isMasked = false,
            displayWidthDp = 360,
            density = 2.5f,
            minWidthPercent = 40,
            maxWidthPercent = 88,
        )
        assertEquals(88, widthPct)
    }

    @Test
    fun testActionChipExpandsWidth() {
        val withoutChip = previewCutoutWidthPercent(
            appName = "App",
            headerText = "App • Now",
            summaryText = "Summary",
            actionLabel = null,
            stackCount = 1,
            isMasked = false,
            displayWidthDp = 500,
            density = 2.5f,
            minWidthPercent = 20,
            maxWidthPercent = 88,
        )
        val withChip = previewCutoutWidthPercent(
            appName = "App",
            headerText = "App • Now",
            summaryText = "Summary",
            actionLabel = "Approve Request",
            stackCount = 1,
            isMasked = false,
            displayWidthDp = 500,
            density = 2.5f,
            minWidthPercent = 20,
            maxWidthPercent = 88,
        )
        assertTrue("Width with chip ($withChip) should be greater than without chip ($withoutChip)", withChip > withoutChip)
    }
}
