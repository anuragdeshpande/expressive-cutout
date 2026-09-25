package com.ekoehler.expressivecutout.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying on-device AI notification summarization and word count constraints.
 */
class LocalAiSummarizerTest {

    @Test
    fun testSixToSevenWordTargetForLongText() {
        val input = "Can you please review the pull request for the navigation bar redesign when you get a chance today"
        val summary = LocalAiSummarizer.summarize(
            title = "Alex Vance",
            text = input,
            appName = "Slack",
        )
        val wordCount = summary.split(" ").filter { it.isNotBlank() }.size
        assertTrue("Expected 6 or 7 words, got $wordCount: \"$summary\"", wordCount in 6..7)
    }

    @Test
    fun testGreetingAndFluffRemoval() {
        val input = "Hey team, just wanted to let you know that the deployment to staging succeeded and is ready for QA"
        val summary = LocalAiSummarizer.summarize(
            title = "DevOps",
            text = input,
            appName = "Slack",
        )
        val wordCount = summary.split(" ").filter { it.isNotBlank() }.size
        assertTrue("Expected 6 or 7 words, got $wordCount: \"$summary\"", wordCount in 6..7)
        assertTrue("Greeting should be removed", !summary.startsWith("Hey", ignoreCase = true))
        assertTrue("Fluff should be removed", !summary.contains("just wanted to", ignoreCase = true))
    }

    @Test
    fun testVerificationCodeExtraction() {
        val input = "Your Google verification code is 849201. Never share this code."
        val summary = LocalAiSummarizer.summarize(
            title = "Google",
            text = input,
            appName = "Messages",
        )
        assertEquals("Verification code is 849201", summary)
    }

    @Test
    fun testDeliveryPatternExtraction() {
        val input = "Your Amazon order has been delivered to your front door."
        val summary = LocalAiSummarizer.summarize(
            title = "Amazon",
            text = input,
            appName = "Amazon",
        )
        assertEquals("Amazon order delivered", summary)
    }

    @Test
    fun testModalQuestionStripping() {
        val input = "Can you review the PR before deployment?"
        val summary = LocalAiSummarizer.summarize(
            title = "Alex Vance",
            text = input,
            appName = "Slack",
        )
        val wordCount = summary.split(" ").filter { it.isNotBlank() }.size
        assertTrue("Expected 5 to 7 words, got $wordCount: \"$summary\"", wordCount in 5..7)
        assertEquals("Review the PR before deployment", summary)
    }

    @Test
    fun testEmptyContentFallsBackToTitleOrApp() {
        val summaryWithTitle = LocalAiSummarizer.summarize(
            title = "Driver arriving in 2 minutes",
            text = null,
            appName = "Uber",
        )
        assertEquals("Driver arriving in 2 minutes", summaryWithTitle)

        val summaryWithoutTitle = LocalAiSummarizer.summarize(
            title = null,
            text = null,
            appName = "Notes",
        )
        assertEquals("Notification from Notes", summaryWithoutTitle)
    }
}
