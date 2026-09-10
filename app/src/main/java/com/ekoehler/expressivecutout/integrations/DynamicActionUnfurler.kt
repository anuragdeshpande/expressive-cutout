package com.ekoehler.expressivecutout.integrations

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Automatically detects and unfurls intermediate action reveal prompts (such as Duo Mobile's
 * "Show actions" or authenticator prompts) so that real authentication actions (Approve / Deny)
 * are populated and surfaced directly on the island preview.
 */
object DynamicActionUnfurler {
    private const val TAG = "DynamicActionUnfurler"

    private val Known2FaPackages = setOf(
        "com.duosecurity.duomobile",
        "com.azure.authenticator",
        "com.google.android.apps.authenticator2",
        "com.okta.android.auth",
        "com.authy.authy",
    )

    private val unfurledKeys = LinkedHashMap<String, Long>()
    private const val MAX_TRACKED_UNFURLS = 32

    /**
     * Inspects [sbn] and triggers any intermediate reveal intent if auto-unfurling is enabled.
     * Returns true if an unfurl trigger was fired.
     */
    fun maybeUnfurl(sbn: StatusBarNotification, autoUnfurlEnabled: Boolean): Boolean {
        if (!autoUnfurlEnabled) return false
        val key = sbn.key ?: return false
        val notification = sbn.notification ?: return false

        // Check if this is a known auth app or carries a "Show actions" intent
        val isAuthApp = sbn.packageName in Known2FaPackages
        val actions = notification.actions.orEmpty()
        if (actions.isEmpty()) return false

        val revealAction = actions.firstOrNull { action ->
            val title = action.title?.toString()?.lowercase() ?: ""
            title.contains("show action") || title.contains("expand") || title.contains("view action")
        }

        if (revealAction != null && isAuthApp) {
            val now = System.currentTimeMillis()
            val lastFired = unfurledKeys[key]
            if (lastFired == null || now - lastFired > 10_000L) {
                unfurledKeys[key] = now
                while (unfurledKeys.size > MAX_TRACKED_UNFURLS) {
                    unfurledKeys.remove(unfurledKeys.keys.first())
                }
                firePendingIntent(revealAction.actionIntent)
                return true
            }
        }
        return false
    }

    private fun firePendingIntent(intent: PendingIntent?) {
        if (intent == null) return
        runCatching { intent.send() }
            .onFailure { Log.w(TAG, "Failed to fire dynamic action unfurl intent", it) }
    }
}
