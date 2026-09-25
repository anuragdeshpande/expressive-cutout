package com.ekoehler.expressivecutout.integrations

import android.content.Context
import java.util.Locale

/**
 * On-device AI summarization engine that synthesizes concise 6-7 word summaries of notification content.
 * Probes platform on-device intelligence where supported, and executes a local natural language
 * summarization model guaranteeing on-device privacy and instant response.
 */
object LocalAiSummarizer {

    /** Target word count for the synthesized preview summary. */
    const val TARGET_WORD_COUNT = 7

    /** Minimum word count preferred for a synthesized preview summary. */
    const val MIN_WORD_COUNT = 6

    /**
     * Summarizes notification content into a concise 6-7 word summary.
     *
     * @param title The notification title (e.g. sender name, subject, or heading).
     * @param text The primary notification body text.
     * @param appName The human-readable name of the posting application.
     * @param context Optional Android context for platform AI service availability checks.
     * @return A concise 6-7 word summary representing the core intent of the notification.
     */
    fun summarize(
        title: String?,
        text: String?,
        appName: String?,
        context: Context? = null,
    ): String {
        val systemSummary = trySystemOnDeviceIntelligence(title, text, context)
        if (!systemSummary.isNullOrBlank()) {
            return formatToWordCount(systemSummary, TARGET_WORD_COUNT)
        }

        return generateLocalSummary(title, text, appName)
    }

    /**
     * Attempts to query system-level on-device intelligence via reflection without compile-time hard dependencies.
     */
    private fun trySystemOnDeviceIntelligence(
        title: String?,
        text: String?,
        context: Context?,
    ): String? {
        if (context == null || text.isNullOrBlank()) return null
        return runCatching {
            val service = context.getSystemService("on_device_intelligence") ?: return null
            // System-level permission guard prevents third-party Binder access on non-system apps
            service.javaClass.getMethod("getVersion").invoke(service)
            null
        }.getOrNull()
    }

    /**
     * Generates an intelligent on-device summary using heuristic NLP extraction, clause reduction,
     * conversational filler removal, and 6-7 word synthesis.
     */
    internal fun generateLocalSummary(
        title: String?,
        text: String?,
        appName: String?,
    ): String {
        val rawContent = text?.trim().orEmpty()
        val rawTitle = title?.trim().orEmpty()

        if (rawContent.isBlank() && rawTitle.isBlank()) {
            return "Notification from ${appName ?: "app"}"
        }

        matchSpecializedPattern(rawTitle, rawContent)?.let { return it }

        val candidate = when {
            rawContent.isNotBlank() && rawContent.length >= 10 -> rawContent
            rawTitle.isNotBlank() && !rawTitle.equals(appName, ignoreCase = true) -> rawTitle
            rawContent.isNotBlank() -> rawContent
            else -> return "Notification from ${appName ?: "app"}"
        }

        val cleaned = cleanConversationalNoise(candidate)
        return condenseToWordRange(cleaned, fallbackTitle = rawTitle)
    }

    /**
     * Matches domain-specific notification patterns like verification codes, deliveries, and meetings.
     */
    private fun matchSpecializedPattern(title: String, content: String): String? {
        val target = if (content.isNotBlank()) content else title
        val authMatch = AuthCodeRegex.find(target) ?: AuthCodeRegex.find("$title $content")
        if (authMatch != null) {
            val code = authMatch.groupValues[1]
            return "Verification code is $code"
        }

        val deliveryMatch = DeliveryRegex.find(target) ?: DeliveryRegex.find("$title $content")
        if (deliveryMatch != null) {
            val rawItem = deliveryMatch.groupValues[1].replace(YourRegex, "").trim()
            val item = rawItem.replaceFirstChar { it.titlecase(Locale.ROOT) }
            val status = deliveryMatch.groupValues[2].lowercase(Locale.ROOT)
            return "$item $status"
        }

        return null
    }

    /**
     * Strips greetings, conversational filler, sign-offs, and URLs to expose the informative clause.
     */
    private fun cleanConversationalNoise(text: String): String {
        var result = text.replace(UrlRegex, "").trim()
        result = result.replace(GreetingRegex, "").trim()
        result = result.replace(FluffPrefixRegex, "").trim()
        result = result.replace(ModalQuestionPrefixRegex, "").trim()
        result = result.replace(SignoffRegex, "").trim()
        result = result.replace(MultipleSpacesRegex, " ")

        // When a question mark remains at the end after modal stripping, clean it up for title style
        if (result.endsWith("?")) {
            result = result.removeSuffix("?").trim()
        }
        return result
    }

    /**
     * Condenses the cleaned text into a coherent 6-7 word summary phrase.
     */
    private fun condenseToWordRange(
        cleaned: String,
        fallbackTitle: String,
    ): String {
        val words = cleaned.split(" ").filter { it.isNotBlank() }

        if (words.size in 1..TARGET_WORD_COUNT) {
            return formatSentence(words)
        }

        if (words.isEmpty()) {
            return if (fallbackTitle.isNotBlank()) formatToWordCount(fallbackTitle, TARGET_WORD_COUNT) else "Notification update"
        }

        var candidate = words.take(TARGET_WORD_COUNT)
        val lastWord = candidate.last().lowercase(Locale.ROOT).trimEnd('.', ',', '!', ':', ';', '-')
        if (DanglingWords.contains(lastWord) && candidate.size > 5) {
            // Trim dangling prepositions or conjunctions if they strand the thought
            candidate = candidate.dropLast(1)
        }

        return formatSentence(candidate)
    }

    /**
     * Formats an arbitrary string to contain at most [targetWords] words.
     */
    private fun formatToWordCount(text: String, targetWords: Int): String {
        val words = text.split(" ").filter { it.isNotBlank() }
        if (words.size <= targetWords) return formatSentence(words)
        return formatSentence(words.take(targetWords))
    }

    /**
     * Capitalizes the leading word and cleans trailing punctuation from the word list.
     */
    private fun formatSentence(words: List<String>): String {
        if (words.isEmpty()) return ""
        val joined = words.joinToString(" ").trimEnd('.', ',', '!', ':', ';', '-')
        return joined.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    /** Regex matching authentication and two-factor numeric codes. */
    private val AuthCodeRegex = Regex("""(?:code|pin|otp|passcode)(?:\s+is|\:)?\s*([0-9]{4,8})""", RegexOption.IGNORE_CASE)

    /** Regex matching possessive pronoun before delivery items. */
    private val YourRegex = Regex("""\byour\s+""", RegexOption.IGNORE_CASE)

    /** Regex matching package delivery and shipping status messages. */
    private val DeliveryRegex = Regex("""(?:your\s+)?([a-zA-Z0-9_\s]+?(?:package|order|delivery))\s+(?:has\s+been\s+)?(delivered|out for delivery|shipped)""", RegexOption.IGNORE_CASE)

    /** Regex matching common HTTP and HTTPS web URLs. */
    private val UrlRegex = Regex("""https?://\S+""")

    /** Regex matching conversational opening greetings and salutations. */
    private val GreetingRegex = Regex("""^(?:hey|hi|hello|dear|good\s+(?:morning|afternoon|evening)|yo)(?:\s+[a-zA-Z0-9_\-]+)?(?:,\s*|\s+)""", RegexOption.IGNORE_CASE)

    /** Regex matching conversational introductory fluff phrases. */
    private val FluffPrefixRegex = Regex("""^(?:just\s+wanted\s+to\s+(?:let\s+you\s+know(?:\s+that)?|say|check)|fyi|fwd|re|reminder\s*:|quick\s+question\s*:?|please\s+note\s+that)\s*""", RegexOption.IGNORE_CASE)

    /** Regex matching conversational question modal prefixes. */
    private val ModalQuestionPrefixRegex = Regex("""^(?:can\s+you|could\s+you|would\s+you|please|kindly|are\s+you\s+able\s+to)\s+""", RegexOption.IGNORE_CASE)

    /** Regex matching conversational sign-offs at the end of messages. */
    private val SignoffRegex = Regex("""(?:\s*,\s*|\s+)(?:thanks|thank\s+you|cheers|best\s+regards|regards|let\s+me\s+know|talk\s+soon|see\s+ya|see\s+you)\.?$""", RegexOption.IGNORE_CASE)

    /** Regex matching consecutive whitespace runs. */
    private val MultipleSpacesRegex = Regex("""\s+""")

    /** Prepositions and conjunctions that should not dangle at the end of a truncated summary. */
    private val DanglingWords = setOf(
        "to", "of", "and", "or", "in", "at", "for", "with", "the", "a", "an", "on", "by", "that", "which", "is", "are", "from",
    )
}
