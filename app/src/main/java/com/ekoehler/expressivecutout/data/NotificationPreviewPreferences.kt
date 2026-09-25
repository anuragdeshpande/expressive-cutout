package com.ekoehler.expressivecutout.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notificationPreviewDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_preview_prefs")

/**
 * The display mode for a notification on the dynamic island.
 */
enum class NotificationMode {
    /** Collapsed icon only — standard minimal island. */
    NORMAL,

    /** 2-row preview layout with context tag, summary, and primary action. */
    PREVIEW,

    /** Full expanded card immediately upon arrival. */
    AUTO_EXPAND,
}

/**
 * Which summary engine to use when summarizing notification content.
 */
enum class SummaryEngineType {
    /** Local on-device AI or fast structured heuristic extractor. */
    ON_DEVICE_AI,

    /** Cloud AI via user-provided API key. */
    CLOUD_BYOK,
}

/**
 * Primary action button preference for an app or sub-filter.
 */
enum class PreferredActionType {
    /** Pick the first surfaceable action from the notification. */
    AUTO,

    /** Prefer positive/approval action (e.g. Approve, Accept, Yes). */
    APPROVE,

    /** Prefer negative/rejection action (e.g. Reject, Deny, Decline). */
    REJECT,

    /** Prefer quick reply action with inline text input. */
    REPLY,

    /** Prefer archive action. */
    ARCHIVE,

    /** Prefer mark-as-read action. */
    MARK_READ,
}

/**
 * A fine-grained sub-filter rule within an app (e.g. for specific accounts, subreddits, or channels).
 */
data class SubFilterRule(
    val id: String,
    val name: String,
    val pattern: String,
    val mode: NotificationMode = NotificationMode.NORMAL,
    val preferredAction: PreferredActionType = PreferredActionType.AUTO,
) {
    /** Serializes this sub-filter rule to a JSON object. */
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id)
        put(KEY_NAME, name)
        put(KEY_PATTERN, pattern)
        put(KEY_MODE, mode.name)
        put(KEY_ACTION, preferredAction.name)
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_PATTERN = "pattern"
        private const val KEY_MODE = "mode"
        private const val KEY_ACTION = "action"

        /** Deserializes a [SubFilterRule] from JSON. */
        fun fromJson(json: JSONObject): SubFilterRule? {
            val id = json.optString(KEY_ID).takeIf { it.isNotBlank() } ?: return null
            val name = json.optString(KEY_NAME, id)
            val pattern = json.optString(KEY_PATTERN, "")
            val mode = runCatching { NotificationMode.valueOf(json.optString(KEY_MODE)) }.getOrDefault(NotificationMode.NORMAL)
            val action = runCatching { PreferredActionType.valueOf(json.optString(KEY_ACTION)) }.getOrDefault(PreferredActionType.AUTO)
            return SubFilterRule(id = id, name = name, pattern = pattern, mode = mode, preferredAction = action)
        }
    }
}

/**
 * Per-app notification preview rules and sub-filters.
 */
data class AppFilterRule(
    val packageName: String,
    val mode: NotificationMode = NotificationMode.NORMAL,
    val preferredAction: PreferredActionType = PreferredActionType.AUTO,
    val preferredActionLabel: String? = null,
    val subFilters: List<SubFilterRule> = emptyList(),
) {
    /** Serializes this app filter rule to a JSON object. */
    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_PACKAGE, packageName)
        put(KEY_MODE, mode.name)
        put(KEY_ACTION, preferredAction.name)
        preferredActionLabel?.let { put(KEY_ACTION_LABEL, it) }
        val arr = JSONArray()
        subFilters.forEach { arr.put(it.toJson()) }
        put(KEY_SUBFILTERS, arr)
    }

    companion object {
        private const val KEY_PACKAGE = "packageName"
        private const val KEY_MODE = "mode"
        private const val KEY_ACTION = "action"
        private const val KEY_ACTION_LABEL = "actionLabel"
        private const val KEY_SUBFILTERS = "subFilters"

        /** Deserializes an [AppFilterRule] from JSON. */
        fun fromJson(json: JSONObject): AppFilterRule? {
            val pkg = json.optString(KEY_PACKAGE).takeIf { it.isNotBlank() } ?: return null
            val mode = runCatching { NotificationMode.valueOf(json.optString(KEY_MODE)) }.getOrDefault(NotificationMode.NORMAL)
            val action = runCatching { PreferredActionType.valueOf(json.optString(KEY_ACTION)) }.getOrDefault(PreferredActionType.AUTO)
            val actionLabel = json.optString(KEY_ACTION_LABEL).takeIf { it.isNotBlank() }
            val subArray = json.optJSONArray(KEY_SUBFILTERS)
            val subFilters = buildList {
                if (subArray != null) {
                    for (i in 0 until subArray.length()) {
                        val subJson = subArray.optJSONObject(i) ?: continue
                        val id = subJson.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val name = subJson.optString("name", id)
                        val pattern = subJson.optString("pattern", "")
                        val subMode = runCatching { NotificationMode.valueOf(subJson.optString("mode")) }.getOrDefault(NotificationMode.PREVIEW)
                        val subAction = runCatching { PreferredActionType.valueOf(subJson.optString("action")) }.getOrDefault(PreferredActionType.AUTO)
                        add(SubFilterRule(id = id, name = name, pattern = pattern, mode = subMode, preferredAction = subAction))
                    }
                }
            }
            return AppFilterRule(
                packageName = pkg,
                mode = mode,
                preferredAction = action,
                preferredActionLabel = actionLabel,
                subFilters = subFilters,
            )
        }
    }
}

/**
 * Snapshot of all notification preview integration settings.
 */
data class NotificationPreviewSettings(
    val enabled: Boolean = true,
    val allowSensitiveContentGlobally: Boolean = false,
    val autoUnfurlDynamicActions: Boolean = true,
    val defaultMode: NotificationMode = NotificationMode.NORMAL,
    val summaryEngine: SummaryEngineType = SummaryEngineType.ON_DEVICE_AI,
    val cloudApiKey: String? = null,
    val disabledContentPackages: Set<String> = emptySet(),
    val appRules: Map<String, AppFilterRule> = emptyMap(),
    val maxStackSize: Int = DEFAULT_MAX_STACK_SIZE,
) {
    companion object {
        const val DEFAULT_MAX_STACK_SIZE = 5
        const val MIN_STACK_SIZE = 1
        const val MAX_STACK_SIZE = 10
    }
}

/**
 * Manages persistence for notification preview settings, privacy opt-outs, and per-app rules.
 */
class NotificationPreviewPreferences(private val context: Context) : JsonSerializable {

    val settings: Flow<NotificationPreviewSettings> = context.notificationPreviewDataStore.data.map { prefs ->
        val enabled = prefs[KEY_ENABLED] ?: true
        val allowSensitive = prefs[KEY_ALLOW_SENSITIVE_GLOBALLY] ?: false
        val autoUnfurl = prefs[KEY_AUTO_UNFURL_2FA] ?: true
        val defaultMode = runCatching { NotificationMode.valueOf(prefs[KEY_DEFAULT_MODE] ?: "") }.getOrDefault(NotificationMode.NORMAL)
        val summaryEngine = runCatching { SummaryEngineType.valueOf(prefs[KEY_SUMMARY_ENGINE] ?: "") }.getOrDefault(SummaryEngineType.ON_DEVICE_AI)
        val cloudApiKey = prefs[KEY_CLOUD_API_KEY]
        val disabledPackages = prefs[KEY_DISABLED_CONTENT_PACKAGES].orEmpty()
        val appRulesJson = prefs[KEY_APP_RULES_JSON]
        val appRules = parseAppRules(appRulesJson)
        val maxStackSize = prefs[KEY_MAX_STACK_SIZE] ?: NotificationPreviewSettings.DEFAULT_MAX_STACK_SIZE

        NotificationPreviewSettings(
            enabled = enabled,
            allowSensitiveContentGlobally = allowSensitive,
            autoUnfurlDynamicActions = autoUnfurl,
            defaultMode = defaultMode,
            summaryEngine = summaryEngine,
            cloudApiKey = cloudApiKey,
            disabledContentPackages = disabledPackages,
            appRules = appRules,
            maxStackSize = maxStackSize,
        )
    }

    /** Enables or disables the notification previews integration globally. */
    suspend fun setEnabled(enabled: Boolean) = context.notificationPreviewDataStore.edit { it[KEY_ENABLED] = enabled }

    /** Sets whether sensitive message content is allowed globally (from Permissions tab). */
    suspend fun setAllowSensitiveContentGlobally(allow: Boolean) = context.notificationPreviewDataStore.edit {
        it[KEY_ALLOW_SENSITIVE_GLOBALLY] = allow
    }

    /** Sets whether 2FA / auth notification dynamic actions are automatically unfurled. */
    suspend fun setAutoUnfurlDynamicActions(autoUnfurl: Boolean) = context.notificationPreviewDataStore.edit {
        it[KEY_AUTO_UNFURL_2FA] = autoUnfurl
    }

    /** Sets the default notification display mode. */
    suspend fun setDefaultMode(mode: NotificationMode) = context.notificationPreviewDataStore.edit {
        it[KEY_DEFAULT_MODE] = mode.name
    }

    /** Sets the maximum number of concurrent stacked notification preview cards. */
    suspend fun setMaxStackSize(size: Int) = context.notificationPreviewDataStore.edit {
        it[KEY_MAX_STACK_SIZE] = size.coerceIn(
            NotificationPreviewSettings.MIN_STACK_SIZE,
            NotificationPreviewSettings.MAX_STACK_SIZE,
        )
    }

    /** Sets the summary engine type. */
    suspend fun setSummaryEngine(engine: SummaryEngineType) = context.notificationPreviewDataStore.edit {
        it[KEY_SUMMARY_ENGINE] = engine.name
    }

    /** Sets or clears the BYOK cloud API key. */
    suspend fun setCloudApiKey(key: String?) = context.notificationPreviewDataStore.edit {
        if (key.isNullOrBlank()) it.remove(KEY_CLOUD_API_KEY) else it[KEY_CLOUD_API_KEY] = key
    }

    /** Sets whether content access is allowed or disabled for a specific app package. */
    suspend fun setContentAllowedForApp(packageName: String, allowed: Boolean) = context.notificationPreviewDataStore.edit { prefs ->
        val current = prefs[KEY_DISABLED_CONTENT_PACKAGES].orEmpty()
        prefs[KEY_DISABLED_CONTENT_PACKAGES] = if (allowed) current - packageName else current + packageName
    }

    /** Saves or updates an [AppFilterRule]. */
    suspend fun saveAppRule(rule: AppFilterRule) = context.notificationPreviewDataStore.edit { prefs ->
        val currentRules = parseAppRules(prefs[KEY_APP_RULES_JSON]).toMutableMap()
        currentRules[rule.packageName] = rule
        prefs[KEY_APP_RULES_JSON] = serializeAppRules(currentRules)
    }

    /** Removes an app rule for [packageName]. */
    suspend fun removeAppRule(packageName: String) = context.notificationPreviewDataStore.edit { prefs ->
        val currentRules = parseAppRules(prefs[KEY_APP_RULES_JSON]).toMutableMap()
        currentRules.remove(packageName)
        prefs[KEY_APP_RULES_JSON] = serializeAppRules(currentRules)
    }

    override suspend fun toJson(): String = JSONObject().apply {
        val current = settings.first()
        put("enabled", current.enabled)
        put("allowSensitiveContentGlobally", current.allowSensitiveContentGlobally)
        put("autoUnfurlDynamicActions", current.autoUnfurlDynamicActions)
        put("defaultMode", current.defaultMode.name)
        put("summaryEngine", current.summaryEngine.name)
        put("maxStackSize", current.maxStackSize)
        put("disabledContentPackages", JSONArray(current.disabledContentPackages))
        put("appRules", serializeAppRules(current.appRules))
    }.toString()

    override suspend fun fromJson(json: String) {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return
        context.notificationPreviewDataStore.edit { prefs ->
            if (root.has("enabled")) prefs[KEY_ENABLED] = root.optBoolean("enabled", true)
            if (root.has("allowSensitiveContentGlobally")) prefs[KEY_ALLOW_SENSITIVE_GLOBALLY] = root.optBoolean("allowSensitiveContentGlobally", false)
            if (root.has("autoUnfurlDynamicActions")) prefs[KEY_AUTO_UNFURL_2FA] = root.optBoolean("autoUnfurlDynamicActions", true)
            if (root.has("defaultMode")) prefs[KEY_DEFAULT_MODE] = root.optString("defaultMode", NotificationMode.NORMAL.name)
            if (root.has("summaryEngine")) prefs[KEY_SUMMARY_ENGINE] = root.optString("summaryEngine", SummaryEngineType.ON_DEVICE_AI.name)
            if (root.has("maxStackSize")) prefs[KEY_MAX_STACK_SIZE] = root.optInt("maxStackSize", NotificationPreviewSettings.DEFAULT_MAX_STACK_SIZE)
            if (root.has("disabledContentPackages")) {
                val arr = root.optJSONArray("disabledContentPackages")
                val set = buildSet {
                    if (arr != null) {
                        for (i in 0 until arr.length()) add(arr.optString(i))
                    }
                }
                prefs[KEY_DISABLED_CONTENT_PACKAGES] = set
            }
            if (root.has("appRules")) {
                prefs[KEY_APP_RULES_JSON] = root.optString("appRules")
            }
        }
    }

    private companion object {
        private val KEY_ENABLED = booleanPreferencesKey("notif_preview_enabled")
        private val KEY_ALLOW_SENSITIVE_GLOBALLY = booleanPreferencesKey("allow_sensitive_globally")
        private val KEY_AUTO_UNFURL_2FA = booleanPreferencesKey("auto_unfurl_2fa")
        private val KEY_DEFAULT_MODE = stringPreferencesKey("default_mode")
        private val KEY_MAX_STACK_SIZE = intPreferencesKey("max_stack_size")
        private val KEY_SUMMARY_ENGINE = stringPreferencesKey("summary_engine")
        private val KEY_CLOUD_API_KEY = stringPreferencesKey("cloud_api_key")
        private val KEY_DISABLED_CONTENT_PACKAGES = stringSetPreferencesKey("disabled_content_packages")
        private val KEY_APP_RULES_JSON = stringPreferencesKey("app_rules_json")

        private fun parseAppRules(jsonString: String?): Map<String, AppFilterRule> {
            if (jsonString.isNullOrBlank()) return emptyMap()
            return runCatching {
                val json = JSONObject(jsonString)
                val map = mutableMapOf<String, AppFilterRule>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val ruleJson = json.optJSONObject(key) ?: continue
                    AppFilterRule.fromJson(ruleJson)?.let { map[key] = it }
                }
                map
            }.getOrDefault(emptyMap())
        }

        private fun serializeAppRules(rules: Map<String, AppFilterRule>): String {
            val json = JSONObject()
            rules.forEach { (k, v) -> json.put(k, v.toJson()) }
            return json.toString()
        }
    }
}
