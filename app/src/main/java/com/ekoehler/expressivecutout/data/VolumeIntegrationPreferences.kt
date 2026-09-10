package com.ekoehler.expressivecutout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/** Backing store for the volume integration's settings. */
private val Context.volumeIntegrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "volume_integration_prefs")

/** The volume integration's configuration settings. */
data class VolumeIntegrationSettings(
    /** Whether the volume cutout overlay integration is enabled. */
    val enabled: Boolean = DEFAULT_ENABLED,
    /** Whether hardware volume key presses are intercepted to hide system UI. */
    val interceptVolumeKeys: Boolean = DEFAULT_INTERCEPT_VOLUME_KEYS,
    /** How many volume points to shift per hardware volume key click (1..10). */
    val volumeStepSize: Int = DEFAULT_VOLUME_STEP_SIZE,
    /** Whether holding down a volume key dynamically increases the step size. */
    val dynamicVolumeStep: Boolean = DEFAULT_DYNAMIC_VOLUME_STEP,
    /** Whether the 3-mode ringer switcher (Silent, Vibrate, Normal) is visible in the expanded card. */
    val showRingerModes: Boolean = DEFAULT_SHOW_RINGER_MODES,
    /** Whether the Live Caption toggle button is visible in the expanded card. */
    val showLiveCaption: Boolean = DEFAULT_SHOW_LIVE_CAPTION,
    /** Whether volume key presses automatically expand the island. */
    val expandOnVolumeKey: Boolean = DEFAULT_EXPAND_ON_VOLUME_KEY,
    /** Auto-dismiss duration in seconds after volume changes stop (1..10). */
    val dismissDurationSeconds: Int = DEFAULT_DISMISS_DURATION_SECONDS,
    /** Colour of the icon container disc behind the volume glyph. Null = default. */
    val iconContainerColor: CutoutColor? = null,
) {
    companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_INTERCEPT_VOLUME_KEYS = true
        const val DEFAULT_VOLUME_STEP_SIZE = 1
        const val MIN_VOLUME_STEP_SIZE = 1
        const val MAX_VOLUME_STEP_SIZE = 10
        const val DEFAULT_DYNAMIC_VOLUME_STEP = false
        const val DEFAULT_SHOW_RINGER_MODES = true
        const val DEFAULT_SHOW_LIVE_CAPTION = true
        const val DEFAULT_EXPAND_ON_VOLUME_KEY = false
        const val DEFAULT_DISMISS_DURATION_SECONDS = 3
        const val MIN_DISMISS_DURATION_SECONDS = 1
        const val MAX_DISMISS_DURATION_SECONDS = 10
    }
}

/** Persists the volume integration's options in DataStore. */
class VolumeIntegrationPreferences(private val context: Context) : JsonSerializable {

    val settings: Flow<VolumeIntegrationSettings> = context.volumeIntegrationDataStore.data.map { prefs ->
        VolumeIntegrationSettings(
            enabled = prefs[ENABLED] ?: VolumeIntegrationSettings.DEFAULT_ENABLED,
            interceptVolumeKeys = prefs[INTERCEPT_VOLUME_KEYS] ?: VolumeIntegrationSettings.DEFAULT_INTERCEPT_VOLUME_KEYS,
            volumeStepSize = (prefs[VOLUME_STEP_SIZE] ?: VolumeIntegrationSettings.DEFAULT_VOLUME_STEP_SIZE)
                .coerceIn(VolumeIntegrationSettings.MIN_VOLUME_STEP_SIZE, VolumeIntegrationSettings.MAX_VOLUME_STEP_SIZE),
            dynamicVolumeStep = prefs[DYNAMIC_VOLUME_STEP] ?: VolumeIntegrationSettings.DEFAULT_DYNAMIC_VOLUME_STEP,
            showRingerModes = prefs[SHOW_RINGER_MODES] ?: VolumeIntegrationSettings.DEFAULT_SHOW_RINGER_MODES,
            showLiveCaption = prefs[SHOW_LIVE_CAPTION] ?: VolumeIntegrationSettings.DEFAULT_SHOW_LIVE_CAPTION,
            expandOnVolumeKey = prefs[EXPAND_ON_VOLUME_KEY] ?: VolumeIntegrationSettings.DEFAULT_EXPAND_ON_VOLUME_KEY,
            dismissDurationSeconds = (prefs[DISMISS_DURATION_SECONDS] ?: VolumeIntegrationSettings.DEFAULT_DISMISS_DURATION_SECONDS)
                .coerceIn(VolumeIntegrationSettings.MIN_DISMISS_DURATION_SECONDS, VolumeIntegrationSettings.MAX_DISMISS_DURATION_SECONDS),
            iconContainerColor = CutoutColor.deserialize(prefs[ICON_CONTAINER_COLOR]),
        )
    }

    /** Exports the current [VolumeIntegrationSettings] as a JSON string. */
    override suspend fun toJson(): String {
        val s = settings.first()
        return JSONObject().apply {
            put("enabled", s.enabled)
            put("interceptVolumeKeys", s.interceptVolumeKeys)
            put("volumeStepSize", s.volumeStepSize)
            put("dynamicVolumeStep", s.dynamicVolumeStep)
            put("showRingerModes", s.showRingerModes)
            put("showLiveCaption", s.showLiveCaption)
            put("expandOnVolumeKey", s.expandOnVolumeKey)
            put("dismissDurationSeconds", s.dismissDurationSeconds)
            put("iconContainerColor", s.iconContainerColor?.serialize() ?: JSONObject.NULL)
        }.toString()
    }

    /** Applies the [VolumeIntegrationSettings] object exported by [toJson]; absent fields are left as-is. */
    override suspend fun fromJson(json: String) {
        val obj = JSONObject(json)
        context.volumeIntegrationDataStore.edit {
            if (obj.has("enabled")) it[ENABLED] = obj.getBoolean("enabled")
            if (obj.has("interceptVolumeKeys")) it[INTERCEPT_VOLUME_KEYS] = obj.getBoolean("interceptVolumeKeys")
            if (obj.has("volumeStepSize")) {
                it[VOLUME_STEP_SIZE] = obj.getInt("volumeStepSize")
                    .coerceIn(VolumeIntegrationSettings.MIN_VOLUME_STEP_SIZE, VolumeIntegrationSettings.MAX_VOLUME_STEP_SIZE)
            }
            if (obj.has("dynamicVolumeStep")) it[DYNAMIC_VOLUME_STEP] = obj.getBoolean("dynamicVolumeStep")
            if (obj.has("showRingerModes")) it[SHOW_RINGER_MODES] = obj.getBoolean("showRingerModes")
            if (obj.has("showLiveCaption")) it[SHOW_LIVE_CAPTION] = obj.getBoolean("showLiveCaption")
            if (obj.has("expandOnVolumeKey")) it[EXPAND_ON_VOLUME_KEY] = obj.getBoolean("expandOnVolumeKey")
            if (obj.has("dismissDurationSeconds")) {
                it[DISMISS_DURATION_SECONDS] = obj.getInt("dismissDurationSeconds")
                    .coerceIn(VolumeIntegrationSettings.MIN_DISMISS_DURATION_SECONDS, VolumeIntegrationSettings.MAX_DISMISS_DURATION_SECONDS)
            }
            if (obj.has("iconContainerColor")) {
                val raw = if (obj.isNull("iconContainerColor")) null else obj.optString("iconContainerColor")
                val color = CutoutColor.deserialize(raw)
                if (color == null) it.remove(ICON_CONTAINER_COLOR) else it[ICON_CONTAINER_COLOR] = color.serialize()
            }
        }
    }

    suspend fun setEnabled(enabled: Boolean) = context.volumeIntegrationDataStore.edit {
        it[ENABLED] = enabled
    }

    suspend fun setInterceptVolumeKeys(enabled: Boolean) = context.volumeIntegrationDataStore.edit {
        it[INTERCEPT_VOLUME_KEYS] = enabled
    }

    suspend fun setVolumeStepSize(step: Int) = context.volumeIntegrationDataStore.edit {
        it[VOLUME_STEP_SIZE] = step.coerceIn(
            VolumeIntegrationSettings.MIN_VOLUME_STEP_SIZE,
            VolumeIntegrationSettings.MAX_VOLUME_STEP_SIZE,
        )
    }

    suspend fun setDynamicVolumeStep(enabled: Boolean) = context.volumeIntegrationDataStore.edit {
        it[DYNAMIC_VOLUME_STEP] = enabled
    }

    suspend fun setShowRingerModes(visible: Boolean) = context.volumeIntegrationDataStore.edit {
        it[SHOW_RINGER_MODES] = visible
    }

    suspend fun setShowLiveCaption(visible: Boolean) = context.volumeIntegrationDataStore.edit {
        it[SHOW_LIVE_CAPTION] = visible
    }

    suspend fun setExpandOnVolumeKey(enabled: Boolean) = context.volumeIntegrationDataStore.edit {
        it[EXPAND_ON_VOLUME_KEY] = enabled
    }

    suspend fun setDismissDurationSeconds(seconds: Int) = context.volumeIntegrationDataStore.edit {
        it[DISMISS_DURATION_SECONDS] = seconds.coerceIn(
            VolumeIntegrationSettings.MIN_DISMISS_DURATION_SECONDS,
            VolumeIntegrationSettings.MAX_DISMISS_DURATION_SECONDS,
        )
    }

    suspend fun setIconContainerColor(color: CutoutColor?) = context.volumeIntegrationDataStore.edit {
        if (color == null) it.remove(ICON_CONTAINER_COLOR) else it[ICON_CONTAINER_COLOR] = color.serialize()
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val INTERCEPT_VOLUME_KEYS = booleanPreferencesKey("intercept_volume_keys")
        val VOLUME_STEP_SIZE = intPreferencesKey("volume_step_size")
        val DYNAMIC_VOLUME_STEP = booleanPreferencesKey("dynamic_volume_step")
        val SHOW_RINGER_MODES = booleanPreferencesKey("show_ringer_modes")
        val SHOW_LIVE_CAPTION = booleanPreferencesKey("show_live_caption")
        val EXPAND_ON_VOLUME_KEY = booleanPreferencesKey("expand_on_volume_key")
        val DISMISS_DURATION_SECONDS = intPreferencesKey("dismiss_duration_seconds")
        val ICON_CONTAINER_COLOR = stringPreferencesKey("icon_container_color")
    }
}
