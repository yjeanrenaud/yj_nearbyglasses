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
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_BATTERY_SAVER = "battery_saver"

        private const val DEFAULT_RSSI_THRESHOLD = -75
        private const val DEFAULT_COOLDOWN_MS = 10000L
        private const val DEFAULT_FOREGROUND_SERVICE = true
        private const val DEFAULT_NOTIFICATIONS = true
        private const val DEFAULT_LOGGING_ENABLED = true
        private const val DEFAULT_DEBUG_ENABLED = false
        private const val DEFAULT_DEBUG_MAX_LINES = 200
        private const val DEFAULT_DEBUG_ADVONLY = true
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.50f
        private const val DEFAULT_BATTERY_SAVER = true
    }

    var rssiThreshold: Int
        get() {
            val raw = prefs.getString(KEY_RSSI_THRESHOLD, DEFAULT_RSSI_THRESHOLD.toString()) ?: DEFAULT_RSSI_THRESHOLD.toString()
            return raw.toIntOrNull() ?: DEFAULT_RSSI_THRESHOLD
        }
        set(value) = prefs.edit().putInt(KEY_RSSI_THRESHOLD, value).apply()

    var cooldownMs: Long
        get() {
            val defaultValue = DEFAULT_COOLDOWN_MS
            val raw = prefs.getString(KEY_COOLDOWN_MS, defaultValue.toString())
            val parsed = raw?.toLongOrNull() ?: defaultValue
            return parsed.coerceIn(0L, 600_000L)
        }
        set(value) = prefs.edit().putLong(KEY_COOLDOWN_MS, value).apply()

    var foregroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOREGROUND_SERVICE, DEFAULT_FOREGROUND_SERVICE)
        set(value) = prefs.edit().putBoolean(KEY_FOREGROUND_SERVICE, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_NOTIFICATIONS, DEFAULT_NOTIFICATIONS)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_NOTIFICATIONS, value).apply()

    var loggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOGGING_ENABLED, DEFAULT_LOGGING_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_LOGGING_ENABLED, value).apply()

    var debugAdvOnly: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_ADVONLY, DEFAULT_DEBUG_ADVONLY)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_ADVONLY, value).apply()

    var debugEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_ENABLED, DEFAULT_DEBUG_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_ENABLED, value).apply()

    val debugMaxLines: Int
        get() {
            val raw = prefs.getString(KEY_DEBUG_MAX_LINES, DEFAULT_DEBUG_MAX_LINES.toString())
            return raw?.toIntOrNull()?.coerceIn(50, 5000) ?: DEFAULT_DEBUG_MAX_LINES
        }

    val debugCompanyIds: Set<Int>
        get() {
            val raw = prefs.getString(KEY_DEBUG_COMPANY_IDS, "") ?: ""
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token ->
                    val t = token.lowercase(Locale.ROOT)
                    when {
                        t.startsWith("0x") -> t.removePrefix("0x").toIntOrNull(16)
                        t.all { it.isDigit() } -> t.toIntOrNull(10)
                        else -> t.toIntOrNull(16)
                    }
                }
                .toSet()
        }

    var confidenceThreshold: Float
        get() {
            val raw = prefs.getString(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD.toString())
            return raw?.toFloatOrNull()?.coerceIn(0.1f, 1.0f) ?: DEFAULT_CONFIDENCE_THRESHOLD
        }
        set(value) = prefs.edit().putString(KEY_CONFIDENCE_THRESHOLD, value.toString()).apply()

    var batterySaverEnabled: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_SAVER, DEFAULT_BATTERY_SAVER)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_SAVER, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
