package com.ekoehler.expressivecutout.integrations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSummarizerTest {

    @Test
    fun testPrivacyMasked() {
        val result = NotificationSummarizer.summarize(
            packageName = "com.slack",
            appName = "Slack",
            rawTitle = "Alex Vance",
            rawText = "Can you review the PR before deployment?",
            subText = null,
            convTitle = null,
            isContentAllowed = false,
        )
        assertTrue(result.isContentMasked)
        assertEquals("Content hidden for privacy", result.summary)
        assertEquals("Slack", result.contextTag)
    }

    @Test
    fun testTwoFactorLoginRequest() {
        val resultWithService = NotificationSummarizer.summarize(
            packageName = "com.duosecurity.duomobile",
            appName = "Duo Mobile",
            rawTitle = "Login Request",
            rawText = "Approve sign-in to Production Console",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertFalse(resultWithService.isContentMasked)
        assertEquals("Login request from Production Console", resultWithService.summary)

        val resultFallback = NotificationSummarizer.summarize(
            packageName = "com.duosecurity.duomobile",
            appName = "Duo Mobile",
            rawTitle = "Approve Login",
            rawText = null,
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("Login request from Duo Mobile", resultFallback.summary)
    }

    @Test
    fun testRedditNewPost() {
        val result = NotificationSummarizer.summarize(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            rawTitle = "r/androiddev • 2h",
            rawText = "Jetpack Compose Material 3 Expressive released",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertFalse(result.isContentMasked)
        assertEquals("New post in r/androiddev", result.summary)
    }

    @Test
    fun testRedditReactions() {
        val resultUpvote = NotificationSummarizer.summarize(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            rawTitle = "r/androiddev",
            rawText = "Someone upvoted your comment in r/androiddev",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("Someone reacted to your post in r/androiddev", resultUpvote.summary)

        val resultComment = NotificationSummarizer.summarize(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            rawTitle = "Someone replied to your comment",
            rawText = "Nice catch on the animation!",
            subText = "r/androiddev",
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("Someone commented on your post in r/androiddev", resultComment.summary)
    }

    @Test
    fun testEmailInboxAndSender() {
        val resultWithInbox = NotificationSummarizer.summarize(
            packageName = "com.google.android.gm",
            appName = "Gmail",
            rawTitle = null,
            rawText = "Here are the project milestones for Q3.",
            subText = "Work Inbox",
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("New email from Work Inbox", resultWithInbox.summary)

        val resultWithSender = NotificationSummarizer.summarize(
            packageName = "com.google.android.gm",
            appName = "Gmail",
            rawTitle = "Alice Vance",
            rawText = "Meeting notes attached",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("New email from Alice Vance", resultWithSender.summary)
    }

    @Test
    fun testMessagingConversations() {
        val resultDirect = NotificationSummarizer.summarize(
            packageName = "com.slack",
            appName = "Slack",
            rawTitle = "Alex Vance",
            rawText = "Can you review the PR before deployment?",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
            isSensitive = true,
        )
        assertEquals("New message from Alex Vance", resultDirect.summary)

        val resultChannel = NotificationSummarizer.summarize(
            packageName = "com.slack",
            appName = "Slack",
            rawTitle = "Alex Vance",
            rawText = "Deployment ready in staging",
            subText = "#general",
            convTitle = null,
            isContentAllowed = true,
            isSensitive = true,
        )
        assertEquals("New message from Alex Vance in #general", resultChannel.summary)
    }

    @Test
    fun testGeneralNotificationFallback() {
        val resultWithTitle = NotificationSummarizer.summarize(
            packageName = "com.ubercab",
            appName = "Uber",
            rawTitle = "Driver arriving now",
            rawText = "Toyota Camry • ABC 123",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("Driver arriving now", resultWithTitle.summary)

        val resultWithoutTitle = NotificationSummarizer.summarize(
            packageName = "com.example.app",
            appName = "Example",
            rawTitle = null,
            rawText = "Some background event",
            subText = null,
            convTitle = null,
            isContentAllowed = true,
        )
        assertEquals("Notification from Example", resultWithoutTitle.summary)
    }
}
