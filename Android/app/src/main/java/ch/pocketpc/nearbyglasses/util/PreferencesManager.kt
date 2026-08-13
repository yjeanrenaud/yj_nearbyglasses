package ch.pocketpc.nearbyglasses.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.Locale

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    companion object {
        private const val KEY_RSSI_THRESHOLD = "rssi_threshold"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_FOREGROUND_SERVICE = "foreground_service"
        private const val KEY_ENABLE_NOTIFICATIONS = "enable_notifications"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_DEBUG_ENABLED = "debug_enabled"
        private const val KEY_DEBUG_MAX_LINES = "debug_max_lines"

        private const val KEY_DEBUG_ADVONLY = "debug_advonly"
        private const val KEY_DEBUG_COMPANY_IDS = "debug_company_ids"
        private const val KEY_IGNORED_DETECTIONS = "ignored_detections"

        //set default values
        private const val DEFAULT_RSSI_THRESHOLD = -75
        private const val DEFAULT_COOLDOWN_MS = 10000L // 10 seconds
        private const val DEFAULT_FOREGROUND_SERVICE = true
        private const val DEFAULT_NOTIFICATIONS = true
        private const val DEFAULT_LOGGING_ENABLED = true
        private const val DEFAULT_DEBUG_ENABLED = false
        private const val DEFAULT_DEBUG_MAX_LINES = 200
        private const val DEFAULT_DEBUG_ADVONLY = true
        private const val KEY_CANARY_MODE = "canary_mode"
        private const val DEFAULT_CANARY_MODE = true
    }

    var canaryModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CANARY_MODE, DEFAULT_CANARY_MODE)
        set(value) = prefs.edit().putBoolean(KEY_CANARY_MODE, value).apply()
    var rssiThreshold: Int
        //get() = prefs.getInt(KEY_RSSI_THRESHOLD, DEFAULT_RSSI_THRESHOLD)
        get() {
            val key = KEY_RSSI_THRESHOLD
            val raw = prefs.getString(key, DEFAULT_RSSI_THRESHOLD.toString()) ?: DEFAULT_RSSI_THRESHOLD.toString()
            return raw.toIntOrNull() ?: DEFAULT_RSSI_THRESHOLD
        }
        set(value) = prefs.edit().putInt(KEY_RSSI_THRESHOLD, value).apply()

    //getters
    var cooldownMs: Long
        //get() = prefs.getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)
        get() {
            val defaultValue = DEFAULT_COOLDOWN_MS // 10000L
            // Prefer string because ListPreference/EditTextPreference store strings
            val raw = prefs.getString(KEY_COOLDOWN_MS, defaultValue.toString())
            val parsed = raw?.toLongOrNull() ?: defaultValue
            //return raw?.toLongOrNull() ?: defaultValue
            // clamp between 0 and 10 minutes
            return parsed.coerceIn(0L, 600_000L)
        }
        set(value) = prefs.edit().putLong(KEY_COOLDOWN_MS, value).apply()
    
    var foregroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOREGROUND_SERVICE, DEFAULT_FOREGROUND_SERVICE)
        set(value) = prefs.edit().putBoolean(KEY_FOREGROUND_SERVICE, value).apply()
    
    var notificationsEnabled: Boolean
        get() = !canaryModeEnabled && prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, DEFAULT_NOTIFICATIONS)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_NOTIFICATIONS, value).apply()

    var loggingEnabled: Boolean
        get() = !canaryModeEnabled && prefs.getBoolean(KEY_LOGGING_ENABLED, DEFAULT_LOGGING_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_LOGGING_ENABLED, value).apply()

    var debugEnabled: Boolean
        get() = !canaryModeEnabled && prefs.getBoolean(KEY_DEBUG_ENABLED, DEFAULT_DEBUG_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_ENABLED, value).apply()
    
    var debugAdvOnly: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_ADVONLY, DEFAULT_DEBUG_ADVONLY)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_ADVONLY, value).apply()

    val debugMaxLines: Int
        get() {
            val raw = prefs.getString(KEY_DEBUG_MAX_LINES, DEFAULT_DEBUG_MAX_LINES.toString())
            return raw?.toIntOrNull()?.coerceIn(50, 5000) ?: DEFAULT_DEBUG_MAX_LINES
        }

    val debugCompanyIds: Set<Int>
        get() {
            val raw = prefs.getString(KEY_DEBUG_COMPANY_IDS, "") ?: ""
            return parseCompanyIds(raw)
        }

    val ignoredDetectionCompanyIds: Set<Int>
        get() {
            val raw = prefs.getString(KEY_IGNORED_DETECTIONS, "") ?: ""
            return parseCompanyIds(raw)
        }

    val ignoredDetectionTokens: Set<String>
        get() {
            val raw = prefs.getString(KEY_IGNORED_DETECTIONS, "") ?: ""
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter { parseCompanyId(it) == null }
                .map { it.lowercase(Locale.ROOT) }
                .toSet()
        }

    private fun parseCompanyIds(raw: String): Set<Int> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parseCompanyId(it) }
            .toSet()
    }

    private fun parseCompanyId(token: String): Int? {
        val t = token.lowercase(Locale.ROOT)
        return when {
            t.startsWith("0x") -> t.removePrefix("0x").toIntOrNull(16)
            t.all { it.isDigit() } -> t.toIntOrNull(10)
            t.matches(Regex("[0-9a-f]+")) -> t.toIntOrNull(16)
            else -> null
        }
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
