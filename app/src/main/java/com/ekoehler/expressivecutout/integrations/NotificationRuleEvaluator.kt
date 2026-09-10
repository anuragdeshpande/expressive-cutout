package com.ekoehler.expressivecutout.integrations

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.data.AppFilterRule
import com.ekoehler.expressivecutout.data.NotificationMode
import com.ekoehler.expressivecutout.data.NotificationPreviewSettings
import com.ekoehler.expressivecutout.data.PreferredActionType
import com.ekoehler.expressivecutout.data.SubFilterRule

/**
 * Result of evaluating a notification against user preview rules, privacy gate, and action preferences.
 */
data class EvaluationResult(
    val mode: NotificationMode,
    val contextTag: String?,
    val summary: String?,
    val isContentMasked: Boolean,
    val primaryAction: CutoutSignal.Notification.Action?,
)

/**
 * Evaluates incoming notifications to assign display mode, summary context, and primary action.
 */
object NotificationRuleEvaluator {

    /**
     * Evaluates [sbn] and surfaceable [actions] against [settings] and [appName].
     */
    fun evaluate(
        sbn: StatusBarNotification,
        actions: List<CutoutSignal.Notification.Action>,
        appName: String?,
        settings: NotificationPreviewSettings,
    ): EvaluationResult {
        if (!settings.enabled) {
            return EvaluationResult(
                mode = NotificationMode.NORMAL,
                contextTag = null,
                summary = null,
                isContentMasked = false,
                primaryAction = null,
            )
        }

        val isContentAllowed = NotificationPrivacyGate.isContentAllowed(sbn, settings)
        val appRule = settings.appRules[sbn.packageName]
        val matchingSubFilter = findMatchingSubFilter(sbn, appRule?.subFilters.orEmpty())

        val targetMode = matchingSubFilter?.mode
            ?: appRule?.mode
            ?: settings.defaultMode

        val preferredActionType = matchingSubFilter?.preferredAction
            ?: appRule?.preferredAction
            ?: PreferredActionType.AUTO

        val preferredActionLabel = appRule?.preferredActionLabel
        val primaryAction = resolvePrimaryAction(actions, preferredActionType, preferredActionLabel)

        val summaryResult = NotificationSummarizer.summarize(sbn, appName, isContentAllowed)

        return EvaluationResult(
            mode = targetMode,
            contextTag = summaryResult.contextTag,
            summary = summaryResult.summary,
            isContentMasked = summaryResult.isContentMasked,
            primaryAction = primaryAction,
        )
    }

    private fun findMatchingSubFilter(
        sbn: StatusBarNotification,
        subFilters: List<SubFilterRule>,
    ): SubFilterRule? {
        if (subFilters.isEmpty()) return null
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val convTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString().orEmpty()
        val channelId = sbn.notification?.channelId.orEmpty()
        val groupKey = sbn.groupKey.orEmpty()

        val fullSearchContent = "$title $text $subText $convTitle $channelId $groupKey".lowercase()

        return subFilters.firstOrNull { rule ->
            val pat = rule.pattern.trim().lowercase()
            if (pat.isBlank()) false else fullSearchContent.contains(pat)
        }
    }

    private fun resolvePrimaryAction(
        actions: List<CutoutSignal.Notification.Action>,
        preferred: PreferredActionType,
        preferredLabel: String? = null,
    ): CutoutSignal.Notification.Action? {
        if (actions.isEmpty()) return null
        if (!preferredLabel.isNullOrBlank()) {
            val labelMatch = actions.firstOrNull { it.title.equals(preferredLabel, ignoreCase = true) }
                ?: actions.firstOrNull { it.title.contains(preferredLabel, ignoreCase = true) }
            if (labelMatch != null) return labelMatch
        }
        return when (preferred) {
            PreferredActionType.APPROVE -> actions.firstOrNull { action ->
                val t = action.title.lowercase()
                t.contains("approve") || t.contains("accept") || t.contains("yes") || t.contains("allow") || t.contains("confirm")
            } ?: actions.firstOrNull()

            PreferredActionType.REJECT -> actions.firstOrNull { action ->
                val t = action.title.lowercase()
                t.contains("reject") || t.contains("deny") || t.contains("decline") || t.contains("no") || t.contains("dismiss")
            } ?: actions.firstOrNull()

            PreferredActionType.REPLY -> actions.firstOrNull { it.reply != null || it.title.lowercase().contains("reply") }
                ?: actions.firstOrNull()

            PreferredActionType.ARCHIVE -> actions.firstOrNull { action ->
                val t = action.title.lowercase()
                t.contains("archive") || t.contains("delete") || t.contains("trash")
            } ?: actions.firstOrNull()

            PreferredActionType.MARK_READ -> actions.firstOrNull { action ->
                val t = action.title.lowercase()
                t.contains("read") || t.contains("mark")
            } ?: actions.firstOrNull()

            PreferredActionType.AUTO -> actions.firstOrNull()
        }
    }
}
