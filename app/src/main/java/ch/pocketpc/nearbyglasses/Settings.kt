package com.example.nearbyglasses

import android.content.Context

object Settings {
    private const val PREFS = "glasses_prefs"

    private const val KEY_KEYWORDS = "keywords"
    private const val KEY_RSSI = "rssi_threshold"
    private const val KEY_COOLDOWN = "cooldown_ms"

    private val defaultKeywords = "ray-ban,rayban,meta,stories"
    private const val defaultRssi = -75
    private const val defaultCooldownMs = 15_000L

    fun getKeywords(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_KEYWORDS, defaultKeywords) ?: defaultKeywords
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
    }

    fun getRssiThreshold(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_RSSI, defaultRssi)

    fun getCooldownMs(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_COOLDOWN, defaultCooldownMs)

    fun save(ctx: Context, keywordsCsv: String, rssi: Int, cooldownSeconds: Int) {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString(KEY_KEYWORDS, keywordsCsv)
            .putInt(KEY_RSSI, rssi)
            .putLong(KEY_COOLDOWN, cooldownSeconds.toLong() * 1000L)
            .apply()
    }
}
