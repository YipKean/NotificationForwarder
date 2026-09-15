package com.notificationforwarder.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.notificationforwarder.app.network.EndpointValidator

enum class FilterMode { ALL_APPS, WHITELIST, BLACKLIST }
enum class AuthMode { NONE, BEARER, CUSTOM }

data class AppSettings(
    val webhookUrl: String,
    val webhookMethod: String,
    val forwardingEnabled: Boolean,
    val filterMode: FilterMode,
    val filterPackages: Set<String>,
    val authMode: AuthMode,
    val bearerToken: String,
    val customHeadersRaw: String,
    val queryParamsRaw: String,
    val payloadTemplateRaw: String,
    val maxRetries: Int,
    val batchSize: Int,
    val retentionHours: Int?,
    val policyRevision: Long
)

data class SettingsSaveResult(
    val success: Boolean,
    val policyChanged: Boolean = false,
    val retentionChanged: Boolean = false,
    val errorCode: String? = null
)

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("notif_settings", Context.MODE_PRIVATE)

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WEBHOOK_URL, value.trim()) }

    var forwardingEnabled: Boolean
        get() = prefs.getBoolean(KEY_FORWARDING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_FORWARDING_ENABLED, value) }

    var filterMode: FilterMode
        get() = FilterMode.WHITELIST
        set(value) = prefs.edit { putString(KEY_FILTER_MODE, value.name) }

    var filterPackages: Set<String>
        get() = parsePackages(prefs.getString(KEY_FILTER_PACKAGES, "") ?: "")
        set(value) = prefs.edit { putString(KEY_FILTER_PACKAGES, value.joinToString(",")) }

    var authMode: AuthMode
        get() = AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, AuthMode.NONE.name)!!)
        set(value) = prefs.edit { putString(KEY_AUTH_MODE, value.name) }

    var bearerToken: String
        get() = prefs.getString(KEY_BEARER_TOKEN, "") ?: ""
        set(value) = prefs.edit { putString(KEY_BEARER_TOKEN, value.trim()) }

    var customHeadersRaw: String
        get() = prefs.getString(KEY_CUSTOM_HEADERS_RAW, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_HEADERS_RAW, value) }

    var webhookMethod: String
        get() = prefs.getString(KEY_WEBHOOK_METHOD, "POST") ?: "POST"
        set(value) = prefs.edit { putString(KEY_WEBHOOK_METHOD, value.uppercase()) }

    var queryParamsRaw: String
        get() = prefs.getString(KEY_QUERY_PARAMS_RAW, "") ?: ""
        set(value) = prefs.edit { putString(KEY_QUERY_PARAMS_RAW, value) }

    var payloadTemplateRaw: String
        get() = prefs.getString(KEY_PAYLOAD_TEMPLATE_RAW, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PAYLOAD_TEMPLATE_RAW, value) }

    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRY, 10)
        set(value) = prefs.edit { putInt(KEY_MAX_RETRY, value.coerceIn(1, 20)) }

    var batchSize: Int
        get() = prefs.getInt(KEY_BATCH_SIZE, 20)
        set(value) = prefs.edit { putInt(KEY_BATCH_SIZE, value.coerceIn(1, 100)) }

    var retentionHours: Int?
        get() = prefs.getInt(KEY_RETENTION_HOURS, 24).takeIf { it >= 1 }
        set(value) = prefs.edit { putInt(KEY_RETENTION_HOURS, value ?: -1) }

    val policyRevision: Long
        get() = prefs.getLong(KEY_POLICY_REVISION, 0L)

    val hardeningVersion: Int
        get() = prefs.getInt(KEY_HARDENING_VERSION, 0)

    fun consumePurgeNotice(): Boolean {
        if (!prefs.getBoolean(KEY_PURGE_NOTICE, false)) return false
        return prefs.edit().putBoolean(KEY_PURGE_NOTICE, false).commit()
    }

    fun readAll(): AppSettings {
        val values = prefs.all
        val mode = runCatching {
            FilterMode.valueOf(values[KEY_FILTER_MODE] as? String ?: FilterMode.WHITELIST.name)
        }.getOrDefault(FilterMode.WHITELIST)
        val auth = runCatching {
            AuthMode.valueOf(values[KEY_AUTH_MODE] as? String ?: AuthMode.NONE.name)
        }.getOrDefault(AuthMode.NONE)
        val retention = (values[KEY_RETENTION_HOURS] as? Int ?: 24).takeIf { it >= 1 }
        return AppSettings(
            webhookUrl = values[KEY_WEBHOOK_URL] as? String ?: "",
            webhookMethod = (values[KEY_WEBHOOK_METHOD] as? String ?: "POST").uppercase(),
            forwardingEnabled = values[KEY_FORWARDING_ENABLED] as? Boolean ?: false,
            filterMode = FilterMode.WHITELIST,
            filterPackages = parsePackages(values[KEY_FILTER_PACKAGES] as? String ?: ""),
            authMode = auth,
            bearerToken = values[KEY_BEARER_TOKEN] as? String ?: "",
            customHeadersRaw = values[KEY_CUSTOM_HEADERS_RAW] as? String ?: "",
            queryParamsRaw = values[KEY_QUERY_PARAMS_RAW] as? String ?: "",
            payloadTemplateRaw = values[KEY_PAYLOAD_TEMPLATE_RAW] as? String ?: "",
            maxRetries = (values[KEY_MAX_RETRY] as? Int ?: 10).coerceIn(1, 20),
            batchSize = (values[KEY_BATCH_SIZE] as? Int ?: 20).coerceIn(1, 100),
            retentionHours = retention,
            policyRevision = values[KEY_POLICY_REVISION] as? Long ?: 0L
        )
    }

    fun saveSnapshot(next: AppSettings): SettingsSaveResult {
        if (!EndpointValidator.isValid(next.webhookUrl)) {
            return SettingsSaveResult(false, errorCode = "invalid_endpoint")
        }
        if (next.retentionHours != null && next.retentionHours !in 1..24) {
            return SettingsSaveResult(false, errorCode = "invalid_retention")
        }
        val current = readAll()
        val policyChanged = current.webhookUrl != next.webhookUrl ||
            current.webhookMethod != next.webhookMethod ||
            current.filterPackages != next.filterPackages ||
            current.authMode != next.authMode ||
            current.bearerToken != next.bearerToken ||
            current.customHeadersRaw != next.customHeadersRaw ||
            current.queryParamsRaw != next.queryParamsRaw ||
            current.payloadTemplateRaw != next.payloadTemplateRaw
        val canEnable = EndpointValidator.isValid(next.webhookUrl) &&
            next.filterPackages.isNotEmpty() && next.filterMode == FilterMode.WHITELIST
        val requestedEnabled = next.forwardingEnabled && canEnable
        val disableTransition = current.forwardingEnabled && !requestedEnabled
        val revisionChanged = policyChanged || disableTransition
        val editor = prefs.edit()
            .putString(KEY_WEBHOOK_URL, next.webhookUrl.trim())
            .putString(KEY_WEBHOOK_METHOD, next.webhookMethod.uppercase())
            .putString(KEY_FILTER_MODE, FilterMode.WHITELIST.name)
            .putString(KEY_FILTER_PACKAGES, next.filterPackages.joinToString(","))
            .putString(KEY_AUTH_MODE, next.authMode.name)
            .putString(KEY_BEARER_TOKEN, next.bearerToken.trim())
            .putString(KEY_CUSTOM_HEADERS_RAW, next.customHeadersRaw)
            .putString(KEY_QUERY_PARAMS_RAW, next.queryParamsRaw)
            .putString(KEY_PAYLOAD_TEMPLATE_RAW, next.payloadTemplateRaw)
            .putInt(KEY_MAX_RETRY, next.maxRetries.coerceIn(1, 20))
            .putInt(KEY_BATCH_SIZE, next.batchSize.coerceIn(1, 100))
            .putInt(KEY_RETENTION_HOURS, next.retentionHours ?: -1)
        if (revisionChanged) {
            editor.putBoolean(KEY_FORWARDING_ENABLED, false)
                .putLong(KEY_POLICY_REVISION, current.policyRevision + 1)
        } else {
            editor.putBoolean(KEY_FORWARDING_ENABLED, requestedEnabled)
                .putLong(KEY_POLICY_REVISION, current.policyRevision)
        }
        val committed = editor.commit()
        if (!committed) {
            return SettingsSaveResult(false, errorCode = "settings_write_failed")
        }
        return SettingsSaveResult(
            success = true,
            policyChanged = revisionChanged,
            retentionChanged = current.retentionHours != next.retentionHours
        )
    }

    fun disableAndClearAllowlist(): Boolean {
        return prefs.edit()
            .putBoolean(KEY_FORWARDING_ENABLED, false)
            .putString(KEY_FILTER_MODE, FilterMode.WHITELIST.name)
            .putString(KEY_FILTER_PACKAGES, "")
            .putBoolean(KEY_PURGE_NOTICE, true)
            .commit()
    }

    fun disableForSecurity(): Boolean {
        val current = readAll()
        return prefs.edit()
            .putBoolean(KEY_FORWARDING_ENABLED, false)
            .putLong(KEY_POLICY_REVISION, current.policyRevision + 1)
            .commit()
    }

    fun markHardeningComplete(): Boolean {
        return prefs.edit().putInt(KEY_HARDENING_VERSION, CURRENT_HARDENING_VERSION).commit()
    }

    fun parseQueryParams(): Map<String, String> = parseQueryParams(queryParamsRaw)

    fun parseQueryParams(raw: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx > 0) {
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (key.isNotEmpty()) map[key] = value
            }
        }
        return map
    }

    fun parseHeaders(): Map<String, String> = parseHeaders(customHeadersRaw)

    fun parseHeaders(raw: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains(':')) {
                return@forEach
            }
            val idx = trimmed.indexOf(':')
            val key = trimmed.substring(0, idx).trim()
            val value = trimmed.substring(idx + 1).trim()
            if (key.isNotEmpty()) {
                map[key] = value
            }
        }
        return map
    }

    companion object {
        const val CURRENT_HARDENING_VERSION = 1
        private const val KEY_HARDENING_VERSION = "hardening_version"
        private const val KEY_POLICY_REVISION = "policy_revision"
        private const val KEY_RETENTION_HOURS = "retention_hours"
        private const val KEY_PURGE_NOTICE = "purge_notice"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
        private const val KEY_FILTER_MODE = "filter_mode"
        private const val KEY_FILTER_PACKAGES = "filter_packages"
        private const val KEY_AUTH_MODE = "auth_mode"
        private const val KEY_BEARER_TOKEN = "bearer_token"
        private const val KEY_CUSTOM_HEADERS_RAW = "custom_headers_raw"
        private const val KEY_WEBHOOK_METHOD = "webhook_method"
        private const val KEY_QUERY_PARAMS_RAW = "query_params_raw"
        private const val KEY_PAYLOAD_TEMPLATE_RAW = "payload_template_raw"
        private const val KEY_MAX_RETRY = "max_retry"
        private const val KEY_BATCH_SIZE = "batch_size"

        fun parsePackages(raw: String): Set<String> {
            return raw.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    }
}
