package com.ekoehler.expressivecutout.integrations

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.ekoehler.expressivecutout.data.NotificationPreviewSettings

/**
 * Evaluates whether notification text content is sensitive (messaging, chat, direct messages)
 * and whether the user's privacy settings permit accessing and displaying the message body.
 */
object NotificationPrivacyGate {

    private val KnownMessagingPackages = setOf(
        "com.microsoft.teams",
        "com.discord",
        "com.whatsapp",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.slack",
        "com.facebook.orca",
        "com.instagram.android",
        "com.viber.voip",
        "com.skype.raider",
    )

    /**
     * Determines whether [sbn] represents a sensitive messaging or direct communication notification.
     */
    fun isSensitive(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        if (sbn.packageName in KnownMessagingPackages) return true
        if (notification.category == Notification.CATEGORY_MESSAGE) return true
        val template = notification.extras?.getString(Notification.EXTRA_TEMPLATE)
        if (template != null && template.contains("MessagingStyle")) return true
        if (notification.extras?.containsKey(Notification.EXTRA_CONVERSATION_TITLE) == true) return true
        return false
    }

    /**
     * Determines whether reading the notification message body is permitted for [sbn] under [settings].
     */
    fun isContentAllowed(sbn: StatusBarNotification, settings: NotificationPreviewSettings): Boolean {
        if (!settings.enabled) return false
        val sensitive = isSensitive(sbn)
        if (!sensitive) {
            // Non-sensitive notifications are allowed unless the package was explicitly opted out.
            return sbn.packageName !in settings.disabledContentPackages
        }
        // Sensitive notifications require global sensitive permission in Permissions tab AND no per-app opt-out.
        if (!settings.allowSensitiveContentGlobally) return false
        return sbn.packageName !in settings.disabledContentPackages
    }
}
