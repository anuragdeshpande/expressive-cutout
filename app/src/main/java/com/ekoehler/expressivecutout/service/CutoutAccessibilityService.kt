package com.ekoehler.expressivecutout.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.ForegroundAppBus
import com.ekoehler.expressivecutout.core.IslandEventBus
import com.ekoehler.expressivecutout.data.VolumeIntegrationPreferences
import com.ekoehler.expressivecutout.data.VolumeIntegrationSettings
import com.ekoehler.expressivecutout.events.MediaPlaybackMonitor
import com.ekoehler.expressivecutout.events.SystemEventMonitor
import com.ekoehler.expressivecutout.events.VolumeMonitor
import com.ekoehler.expressivecutout.overlay.IslandOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The always-on host of the island. Its main purpose is to provide a context that can add
 * a TYPE_ACCESSIBILITY_OVERLAY window (no SYSTEM_ALERT_WINDOW required) and to keep the
 * overlay controller, system-event monitor, and volume monitor alive for the lifetime of the binding.
 *
 * It tracks which app is in the foreground, inspects assistant windows for live response text,
 * and intercepts volume keys when the volume integration is active.
 */
class CutoutAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlay: IslandOverlayController? = null
    private var systemEvents: SystemEventMonitor? = null
    private var mediaPlayback: MediaPlaybackMonitor? = null
    private var volumeMonitor: VolumeMonitor? = null
    private var volumeSettings = VolumeIntegrationSettings()
    private var volumeKeyRepeatJob: Job? = null
    private var lastAssistantKey: String? = null

    /**
     * Starts the overlay and the event monitors, and publishes the service so the rest of the
     * app can see that the island is live. Mirrored by [teardown].
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        overlay = IslandOverlayController(this).also { it.start() }
        systemEvents = SystemEventMonitor(this).also { it.start() }
        mediaPlayback = MediaPlaybackMonitor(this).also { it.start() }
        volumeMonitor = VolumeMonitor(this).also { it.start() }

        serviceScope.launch {
            VolumeIntegrationPreferences(this@CutoutAccessibilityService).settings.collect {
                volumeSettings = it
            }
        }

        instance = this
        _bound.value = true
    }

    /**
     * Whether the package is a voice assistant, checked against the known first-party and OEM
     * assistant packages so their windows can be inspected for a spoken answer.
     */
    private fun isAssistantPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.google.android.googlequicksearchbox" ||
            pkg == "com.google.android.apps.googleassistant" ||
            pkg == "com.google.android.apps.bard" ||
            pkg == "com.google.android.apps.gemini" ||
            pkg == "com.samsung.android.bixby.agent" ||
            pkg == "com.samsung.android.bixby.service" ||
            pkg == "com.amazon.dee.app" ||
            pkg == "com.openai.chatgpt" ||
            pkg == "com.microsoft.copilot" ||
            pkg.contains("assistant") ||
            pkg.contains("bixby") ||
            pkg.contains("gemini")
    }

    private val DISCLAIMER_PATTERNS = listOf(
        "can make mistakes",
        "gemini is ai",
        "gemini is an ai",
        "display inaccurate info",
        "check responses",
        "type, talk, or share",
        "ask gemini",
        "gemini advanced",
        "share screen with live"
    )

    /**
     * Whether a line of assistant text is boilerplate (a safety notice, a "check your results"
     * footer) rather than the answer itself.
     */
    private fun isDisclaimer(text: String): Boolean {
        val lower = text.lowercase()
        return DISCLAIMER_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Notes which app is in front, and inspects assistant windows when one is. Nothing here reads
     * content from any other app.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return

        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundAppBus.update(pkg)
        }

        if (isAssistantPackage(pkg)) {
            inspectAssistantWindow(pkg, ev)
        } else if (lastAssistantKey != null && ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // User navigated away from assistant app/overlay — dismiss assistant cutout
            lastAssistantKey = null
            IslandEventBus.emit(CutoutSignal.Assistant(packageName = pkg, active = false))
        }
    }

    /**
     * Reads the spoken answer out of an assistant window, emitting an inactive signal as soon as
     * the window is gone so the tile can't outlive the answer it shows.
     */
    private fun inspectAssistantWindow(pkg: String, event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source
        if (rootNode == null) {
            if (lastAssistantKey != null) {
                lastAssistantKey = null
                IslandEventBus.emit(CutoutSignal.Assistant(packageName = pkg, active = false))
            }
            return
        }

        val textList = mutableListOf<String>()
        collectTextNodes(rootNode, textList)

        if (textList.isEmpty()) {
            if (lastAssistantKey != null) {
                lastAssistantKey = null
                IslandEventBus.emit(CutoutSignal.Assistant(packageName = pkg, active = false))
            }
            return
        }

        val title = textList.firstOrNull { it.isNotBlank() }
        val responseText = textList.filter { it.isNotBlank() && it != title }.joinToString("\n").ifBlank { title }

        val lastKey = "$pkg|$title|$responseText"
        if (lastKey != lastAssistantKey) {
            lastAssistantKey = lastKey
            IslandEventBus.emit(
                CutoutSignal.Assistant(
                    packageName = pkg,
                    title = title,
                    text = responseText,
                    contentIntent = null,
                    active = true,
                ),
            )
        }
    }

    /**
     * Walks a node tree collecting usable text, skipping single characters and boilerplate that
     * would otherwise crowd out the answer.
     */
    private fun collectTextNodes(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank() && text.length > 1 && !isDisclaimer(text)) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, list)
        }
    }

    /**
     * Forward device rotations to the overlay so it can rebuild its top-of-screen window for the new
     * geometry — otherwise the touchable-region carve-out that lets the notification shade through
     * beside the pill goes stale in landscape and the band swallows the shade pull.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.onOrientationChanged(newConfig.orientation)
    }

    /** Required by the framework. The island has no interruptible work of its own. */
    override fun onInterrupt() {
        volumeKeyRepeatJob?.cancel()
        volumeKeyRepeatJob = null
    }

    /**
     * Intercepts hardware volume key events when the volume overlay integration is enabled,
     * suppressing the default Android volume rocker overlay and adjusting volume programmatically.
     * Long-pressing or holding down a volume key continues to repeat volume adjustments smoothly.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!volumeSettings.enabled || !volumeSettings.interceptVolumeKeys) {
            return super.onKeyEvent(event)
        }

        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
            KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
            KeyEvent.KEYCODE_VOLUME_MUTE -> AudioManager.ADJUST_TOGGLE_MUTE
            else -> return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    volumeKeyRepeatJob?.cancel()
                    volumeMonitor?.adjustMediaVolume(direction, volumeSettings.volumeStepSize)
                    if (direction != AudioManager.ADJUST_TOGGLE_MUTE) {
                        val repeatStepSize = if (volumeSettings.dynamicVolumeStep) {
                            (volumeSettings.volumeStepSize * 2).coerceAtMost(VolumeIntegrationSettings.MAX_VOLUME_STEP_SIZE)
                        } else {
                            volumeSettings.volumeStepSize
                        }
                        volumeKeyRepeatJob = serviceScope.launch {
                            delay(INITIAL_KEY_REPEAT_DELAY_MS)
                            while (isActive) {
                                volumeMonitor?.adjustMediaVolume(direction, repeatStepSize)
                                delay(KEY_REPEAT_INTERVAL_MS)
                            }
                        }
                    }
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                volumeKeyRepeatJob?.cancel()
                volumeKeyRepeatJob = null
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Tears everything down on unbind, which is when the user turns the service off in settings.
     */
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    /**
     * Tears everything down on destroy, since a service can be killed without ever being unbound.
     */
    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /**
     * Stops the monitors and the overlay and clears the published state. Written to be safe to call
     * twice, because unbind and destroy both reach it.
     */
    private fun teardown() {
        volumeKeyRepeatJob?.cancel()
        volumeKeyRepeatJob = null
        _bound.value = false
        instance = null
        volumeMonitor?.stop()
        volumeMonitor = null
        mediaPlayback?.stop()
        mediaPlayback = null
        systemEvents?.stop()
        systemEvents = null
        overlay?.stop()
        overlay = null
    }

    companion object {
        /**
         * The live service instance while bound, used by [performGlobal] to fire system-wide actions
         * for the expanded "center" shortcuts. Held statically (the service has no android:process, so
         * it's this same process) and cleared in [teardown] so it never outlives the binding.
         */
        private var instance: CutoutAccessibilityService? = null

        /**
         * Perform a system-wide [AccessibilityService] global action (e.g. lock screen, screenshot,
         * quick settings) if the service is bound. Best-effort: returns false when nothing is bound
         * or the action is rejected, so callers can fall back or ignore it.
         */
        fun performGlobal(action: Int): Boolean =
            runCatching { instance?.performGlobalAction(action) }.getOrNull() ?: false

        /** Adjusts the media stream volume level via the live service or fallback context. */
        fun setMediaVolume(volumeIndex: Int, showUi: Boolean = false, fallbackContext: Context? = null) {
            val monitor = instance?.volumeMonitor
            if (monitor != null) {
                monitor.setMediaVolume(volumeIndex, showUi)
            } else if (fallbackContext != null) {
                VolumeMonitor(fallbackContext).setMediaVolume(volumeIndex, showUi)
            }
        }

        /** Adjusts the media stream volume percentage via the live service or fallback context. */
        fun setMediaVolumePercent(percent: Int, showUi: Boolean = false, fallbackContext: Context? = null) {
            val monitor = instance?.volumeMonitor
            if (monitor != null) {
                monitor.setMediaVolumePercent(percent, showUi)
            } else if (fallbackContext != null) {
                VolumeMonitor(fallbackContext).setMediaVolumePercent(percent, showUi)
            }
        }

        /** Changes the ringer mode via the live service or fallback context. */
        fun setRingerMode(mode: Int, fallbackContext: Context? = null) {
            val monitor = instance?.volumeMonitor
            if (monitor != null) {
                monitor.setRingerMode(mode)
            } else if (fallbackContext != null) {
                VolumeMonitor(fallbackContext).setRingerMode(mode)
            }
        }

        /** Toggles Live Caption via the live service or fallback context. */
        fun toggleLiveCaption(fallbackContext: Context? = null) {
            val monitor = instance?.volumeMonitor
            if (monitor != null) {
                monitor.toggleLiveCaption()
            } else if (fallbackContext != null) {
                VolumeMonitor(fallbackContext).toggleLiveCaption()
            }
        }

        /** Launches the system volume panel bottom sheet via the live service or fallback context. */
        fun openVolumePanel(fallbackContext: Context? = null) {
            val monitor = instance?.volumeMonitor
            if (monitor != null) {
                monitor.openVolumePanel()
            } else if (fallbackContext != null) {
                VolumeMonitor(fallbackContext).openVolumePanel()
            }
        }

        private val _bound = MutableStateFlow(false)

        /**
         * True only while Android actually has this service bound — i.e. while the island is
         * really running. Deliberately separate from
         * [com.ekoehler.expressivecutout.permissions.Permissions.isAccessibilityGranted], which
         * reads the user's *consent* out of Settings.Secure: that stays "enabled" across a
         * reinstall or an app update while the binding is dead, so the app would otherwise report
         * itself healthy while nothing at all is listening. Lives in the companion object rather
         * than on the instance so the settings UI (same process — no android:process on the
         * service) can observe it without a binder of its own.
         */
        val bound: StateFlow<Boolean> = _bound.asStateFlow()

        private const val INITIAL_KEY_REPEAT_DELAY_MS = 300L
        private const val KEY_REPEAT_INTERVAL_MS = 60L
    }
}
