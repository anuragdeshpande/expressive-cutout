package com.ekoehler.expressivecutout.events

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ekoehler.expressivecutout.core.CutoutSignal
import com.ekoehler.expressivecutout.core.IslandEventBus
import com.ekoehler.expressivecutout.core.VolumeBus
import com.ekoehler.expressivecutout.core.VolumeState

/**
 * Monitors system volume, ringer mode, and Live Caption changes across the device and
 * publishes updates to [VolumeBus] and [IslandEventBus]. Also provides helper methods to
 * control audio volume and trigger system sound intents safely.
 */
class VolumeMonitor(private val context: Context) {

    private val audioManager = context.getSystemService<AudioManager>()
    private val notificationManager = context.getSystemService<NotificationManager>()

    private val captionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            publishCurrentState()
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_VOLUME_CHANGED, AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    publishCurrentState()
                }
            }
        }
    }

    /** Starts observing volume broadcasts and caption settings. Mirrored by [stop]. */
    fun start() {
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        runCatching {
            val captionUri = Settings.Secure.getUriFor(KEY_CAPTIONING_ENABLED)
            if (captionUri != null) {
                context.contentResolver.registerContentObserver(captionUri, false, captionObserver)
            }
            val odiUri = Settings.Secure.getUriFor(KEY_ODI_CAPTIONS_ENABLED)
            if (odiUri != null) {
                context.contentResolver.registerContentObserver(odiUri, false, captionObserver)
            }
        }

        publishCurrentState()
    }

    /** Stops observing volume and caption changes. */
    fun stop() {
        runCatching { context.unregisterReceiver(volumeReceiver) }
        runCatching { context.contentResolver.unregisterContentObserver(captionObserver) }
    }

    /** Reads the live audio volume and ringer mode state from the framework. */
    fun readCurrentState(): VolumeState {
        val am = audioManager ?: return VolumeState()
        val media = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxMedia = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val ringer = am.ringerMode
        val isMuted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        val captionsEnabled = isLiveCaptionEnabled()
        return VolumeState(
            mediaVolume = media,
            maxMediaVolume = maxMedia,
            ringerMode = ringer,
            isLiveCaptionEnabled = captionsEnabled,
            isMuted = isMuted,
        )
    }

    /** Updates [VolumeBus] and optionally emits [CutoutSignal.Volume] to [IslandEventBus]. */
    fun publishCurrentState(emitSignal: Boolean = false) {
        val state = readCurrentState()
        VolumeBus.update(state)
        if (emitSignal) {
            IslandEventBus.emit(CutoutSignal.Volume(state))
        }
    }

    /** Sets the media stream volume to [volumeIndex] (0..max). */
    fun setMediaVolume(volumeIndex: Int, showUi: Boolean = false) {
        val am = audioManager ?: return
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, volumeIndex, flags)
            publishCurrentState(emitSignal = true)
        }
    }

    /** Sets the media stream volume percentage (0..100). */
    fun setMediaVolumePercent(percent: Int, showUi: Boolean = false) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val targetIndex = ((percent.coerceIn(0, 100) / 100f) * max).toInt().coerceIn(0, max)
        setMediaVolume(targetIndex, showUi)
    }

    /** Adjusts the media stream volume up or down by [stepSize] steps. */
    fun adjustMediaVolume(direction: Int, stepSize: Int = 1, showUi: Boolean = false) {
        val am = audioManager ?: return
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        runCatching {
            if (stepSize <= 1 || direction == AudioManager.ADJUST_TOGGLE_MUTE) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
            } else {
                val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val delta = if (direction == AudioManager.ADJUST_RAISE) stepSize else -stepSize
                val target = (current + delta).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, flags)
            }
            publishCurrentState(emitSignal = true)
        }
    }

    /**
     * Changes system ringer mode to [mode] ([AudioManager.RINGER_MODE_NORMAL],
     * [AudioManager.RINGER_MODE_VIBRATE], [AudioManager.RINGER_MODE_SILENT]).
     */
    fun setRingerMode(mode: Int) {
        val am = audioManager ?: return
        val hasDndPermission = notificationManager?.isNotificationPolicyAccessGranted ?: true
        runCatching {
            if (mode == AudioManager.RINGER_MODE_SILENT && !hasDndPermission) {
                // Without DND policy access on Android N+, fallback to vibrate
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                am.ringerMode = mode
            }
            publishCurrentState(emitSignal = false)
        }
    }

    /** Toggles system Live Caption on or off, falling back to settings if not permitted. */
    fun toggleLiveCaption() {
        val currentlyEnabled = isLiveCaptionEnabled()
        val target = if (currentlyEnabled) 0 else 1
        val updatedOdi = runCatching {
            Settings.Secure.putInt(context.contentResolver, KEY_ODI_CAPTIONS_ENABLED, target)
        }.getOrDefault(false)
        val updatedCaption = runCatching {
            Settings.Secure.putInt(context.contentResolver, KEY_CAPTIONING_ENABLED, target)
        }.getOrDefault(false)

        if (updatedOdi || updatedCaption) {
            publishCurrentState(emitSignal = false)
        } else {
            openLiveCaptionSettings()
        }
    }

    /** Launches the native Android SystemUI Volume Panel bottom sheet, falling back to Settings panel or Sound settings. */
    fun openVolumePanel() {
        val sysUiIntent = Intent(ACTION_LAUNCH_VOLUME_PANEL_DIALOG).apply {
            setPackage(PACKAGE_SYSTEM_UI)
        }
        val hasSysUiReceiver = runCatching {
            context.packageManager.queryBroadcastReceivers(sysUiIntent, 0).isNotEmpty()
        }.getOrDefault(false)

        if (hasSysUiReceiver) {
            runCatching { context.sendBroadcast(sysUiIntent) }
            return
        }

        val panelIntent = Intent(ACTION_PANEL_VOLUME).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val started = runCatching {
            context.startActivity(panelIntent)
            true
        }.getOrDefault(false)

        if (!started) {
            val soundSettingsIntent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(soundSettingsIntent) }
        }
    }

    /** Launches system Caption settings. */
    fun openLiveCaptionSettings() {
        val captionIntent = Intent(Settings.ACTION_CAPTIONING_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(captionIntent) }
    }

    private fun isLiveCaptionEnabled(): Boolean {
        val cr = context.contentResolver
        val captioning = runCatching { Settings.Secure.getInt(cr, KEY_CAPTIONING_ENABLED, 0) }.getOrDefault(0) == 1
        val odi = runCatching { Settings.Secure.getInt(cr, KEY_ODI_CAPTIONS_ENABLED, 0) }.getOrDefault(0) == 1
        return captioning || odi
    }

    companion object {
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
        const val ACTION_LAUNCH_VOLUME_PANEL_DIALOG = "com.android.systemui.action.LAUNCH_VOLUME_PANEL_DIALOG"
        const val ACTION_PANEL_VOLUME = "android.settings.panel.action.VOLUME"
        private const val PACKAGE_SYSTEM_UI = "com.android.systemui"
        private const val KEY_CAPTIONING_ENABLED = "accessibility_captioning_enabled"
        private const val KEY_ODI_CAPTIONS_ENABLED = "odi_captions_volume_ui_enabled"
    }
}
