package com.ekoehler.expressivecutout.integrations

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Result of summarizing a notification for the 2-row preview state.
 */
data class SummaryResult(
    val contextTag: String?,
    val summary: String,
    val isContentMasked: Boolean,
)

/**
 * Intelligence and heuristic engine for extracting concise context tags and smart titles from notifications.
 */
object NotificationSummarizer {

    /**
     * Extracts a concise [SummaryResult] from [sbn] taking into account [isContentAllowed].
     */
    fun summarize(
        sbn: StatusBarNotification,
        appName: String?,
        isContentAllowed: Boolean,
    ): SummaryResult {
        val notification = sbn.notification
        val extras = notification?.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val rawText = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val convTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        return summarize(
            packageName = sbn.packageName,
            appName = appName,
            rawTitle = rawTitle,
            rawText = rawText,
            subText = subText,
            convTitle = convTitle,
            isContentAllowed = isContentAllowed,
            category = notification?.category,
            isSensitive = NotificationPrivacyGate.isSensitive(sbn),
        )
    }

    /**
     * Internal overload taking raw notification fields to facilitate pure JVM unit testing.
     */
    fun summarize(
        packageName: String,
        appName: String?,
        rawTitle: String?,
        rawText: String?,
        subText: String?,
        convTitle: String?,
        isContentAllowed: Boolean,
        category: String? = null,
        isSensitive: Boolean = false,
    ): SummaryResult {
        val pkg = packageName.lowercase()
        val displayApp = appName ?: "Notification"

        if (!isContentAllowed) {
            return SummaryResult(
                contextTag = displayApp,
                summary = "Content hidden for privacy",
                isContentMasked = true,
            )
        }

        val contextTag: String
        val smartTitle: String

        when {
            isTwoFactor(pkg) -> {
                contextTag = displayApp
                smartTitle = parseTwoFactor(pkg, displayApp, rawTitle, rawText)
            }

            isReddit(pkg) -> {
                contextTag = extractSubreddit(rawTitle, rawText, subText) ?: displayApp
                smartTitle = parseReddit(rawTitle, rawText, subText)
            }

            isEmail(pkg, category) -> {
                val inboxOrSender = when {
                    !subText.isNullOrBlank() && (subText.contains("inbox", ignoreCase = true) || rawTitle.isNullOrBlank()) -> subText
                    !rawTitle.isNullOrBlank() -> rawTitle
                    else -> null
                }
                contextTag = if (inboxOrSender != null) "$displayApp • $inboxOrSender" else displayApp
                smartTitle = if (!rawText.isNullOrBlank()) {
                    LocalAiSummarizer.summarize(rawTitle, rawText, displayApp)
                } else {
                    parseEmail(displayApp, rawTitle, subText)
                }
            }

            isMessaging(pkg, category, isSensitive) -> {
                val sender = rawTitle?.takeIf { it.isNotBlank() }
                val channel = subText?.takeIf { it.isNotBlank() }
                val group = convTitle?.takeIf { it.isNotBlank() }
                val senderOrGroup = when {
                    group != null && sender != null && !group.equals(sender, ignoreCase = true) -> "$sender in $group"
                    channel != null && sender != null -> "$sender in $channel"
                    group != null -> group
                    sender != null -> sender
                    else -> null
                }
                contextTag = if (senderOrGroup != null) "$displayApp • $senderOrGroup" else displayApp
                smartTitle = if (!rawText.isNullOrBlank()) {
                    LocalAiSummarizer.summarize(rawTitle, rawText, displayApp)
                } else {
                    parseMessaging(displayApp, rawTitle, subText, convTitle)
                }
            }

            else -> {
                contextTag = displayApp
                smartTitle = if (!rawText.isNullOrBlank()) {
                    LocalAiSummarizer.summarize(rawTitle, rawText, displayApp)
                } else {
                    parseGeneral(displayApp, rawTitle)
                }
            }
        }

        return SummaryResult(
            contextTag = contextTag,
            summary = smartTitle,
            isContentMasked = false,
        )
    }

    /** Determines whether the package belongs to a known 2FA authenticator provider. */
    private fun isTwoFactor(pkg: String): Boolean =
        pkg.contains("duo") || pkg.contains("authenticator") || pkg.contains("okta") ||
            pkg.contains("authy") || pkg.contains("bitwarden") || pkg.contains("1password") ||
            pkg.contains("onepassword") || pkg.contains("yubico") || pkg.contains("pingidentity")

    /** Determines whether the package belongs to Reddit or a Reddit client. */
    private fun isReddit(pkg: String): Boolean =
        pkg.contains("reddit")

    /** Determines whether the notification represents an email message. */
    private fun isEmail(pkg: String, category: String?): Boolean =
        pkg.contains("gmail") || pkg.contains(".gm") || pkg.contains("outlook") ||
            pkg.contains("spark") || pkg.contains("email") || pkg.contains("mail") ||
            pkg.contains("proton") || pkg.contains("k9") || pkg.contains("thunderbird") ||
            category == Notification.CATEGORY_EMAIL

    /** Determines whether the notification represents an instant messaging conversation. */
    private fun isMessaging(pkg: String, category: String?, isSensitive: Boolean): Boolean =
        isSensitive ||
            category == Notification.CATEGORY_MESSAGE ||
            pkg.contains("slack") || pkg.contains("whatsapp") || pkg.contains("teams") ||
            pkg.contains("discord") || pkg.contains("telegram") || pkg.contains("signal") ||
            pkg.contains("messenger") || pkg.contains("messages")

    /** Parses a concise 2FA login verification prompt. */
    private fun parseTwoFactor(
        pkg: String,
        appName: String,
        rawTitle: String?,
        rawText: String?,
    ): String {
        val targetService = extractAuthService(rawText) ?: rawTitle?.takeIf {
            !it.contains("approve", ignoreCase = true) && !it.contains("login", ignoreCase = true) && !it.contains("sign-in", ignoreCase = true)
        } ?: appName
        return "Login request from $targetService"
    }

    /** Extracts a service or console name following login keywords in text. */
    private fun extractAuthService(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = AuthServicePattern.find(text)
        return match?.groupValues?.get(1)?.trim()
    }

    /** Parses Reddit notifications into new post announcements or reaction events. */
    private fun parseReddit(rawTitle: String?, rawText: String?, subText: String?): String {
        val subreddit = extractSubreddit(rawTitle, rawText, subText)
        val combined = "${rawTitle.orEmpty()} ${rawText.orEmpty()}".lowercase()

        val isReaction = combined.contains("upvote") || combined.contains("upvoted") ||
            combined.contains("reaction") || combined.contains("reacted") ||
            combined.contains("like") || combined.contains("liked") || combined.contains("karma")
        val isComment = combined.contains("reply") || combined.contains("replied") ||
            combined.contains("comment") || combined.contains("commented")

        return when {
            isReaction -> {
                if (subreddit != null) "Someone reacted to your post in $subreddit"
                else "Someone reacted to your post"
            }
            isComment -> {
                if (subreddit != null) "Someone commented on your post in $subreddit"
                else "Someone commented on your post"
            }
            subreddit != null -> "New post in $subreddit"
            else -> "New post on Reddit"
        }
    }

    /** Extracts a subreddit name (e.g. "r/androiddev") from titles or text. */
    private fun extractSubreddit(rawTitle: String?, rawText: String?, subText: String?): String? {
        if (subText != null && subText.startsWith("r/")) return subText.substringBefore(" •").substringBefore(":")
        if (rawTitle != null && rawTitle.startsWith("r/")) return rawTitle.substringBefore(" •").substringBefore(":")
        val searchPool = "${rawTitle.orEmpty()} ${subText.orEmpty()} ${rawText.orEmpty()}"
        return SubredditPattern.find(searchPool)?.value
    }

    /** Parses email notifications into sender or inbox-oriented subject announcements. */
    private fun parseEmail(appName: String, rawTitle: String?, subText: String?): String {
        val inboxOrSender = when {
            !subText.isNullOrBlank() && (subText.contains("inbox", ignoreCase = true) || rawTitle.isNullOrBlank()) -> subText
            !rawTitle.isNullOrBlank() -> rawTitle
            !subText.isNullOrBlank() -> subText
            else -> appName
        }
        return "New email from $inboxOrSender"
    }

    /** Parses messaging conversations into sender or channel-oriented announcements. */
    private fun parseMessaging(
        appName: String,
        rawTitle: String?,
        subText: String?,
        convTitle: String?,
    ): String {
        val sender = rawTitle?.takeIf { it.isNotBlank() }
        val channel = subText?.takeIf { it.isNotBlank() }
        val group = convTitle?.takeIf { it.isNotBlank() }

        return when {
            group != null && sender != null && !group.equals(sender, ignoreCase = true) ->
                "New message from $sender in $group"
            channel != null && sender != null ->
                "New message from $sender in $channel"
            group != null ->
                "New message in $group"
            sender != null ->
                "New message from $sender"
            else ->
                "New message from $appName"
        }
    }

    /** Parses general notifications, preferring the notification title over body content. */
    private fun parseGeneral(appName: String, rawTitle: String?): String {
        return if (!rawTitle.isNullOrBlank() && !rawTitle.equals(appName, ignoreCase = true)) {
            rawTitle
        } else {
            "Notification from $appName"
        }
    }

    /** Regex capturing the target service name from an authentication prompt body. */
    private val AuthServicePattern = Regex("""(?:sign-?in to|login to|login for|access to)\s+([^,\.\n]+)""", RegexOption.IGNORE_CASE)

    /** Regex capturing standard subreddit format (e.g. r/androiddev). */
    private val SubredditPattern = Regex("""r/[A-Za-z0-9_]+""")
}
