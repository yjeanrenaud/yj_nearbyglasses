package ch.pocketpc.nearbyglasses.model

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray

data class PayloadHints(
    val typicalMinLength: Int?,
    val typicalMaxLength: Int?,
    val knownPrefixes: List<String>
)

data class GlassesSignature(
    val companyId: Int,
    val companyName: String,
    val serviceUuid: String?,
    val hasCamera: Boolean,
    val knownProducts: List<String>,
    val knownFalsePositives: List<String>,
    val deviceNamePatterns: List<String>,
    val payloadHints: PayloadHints?,
    val baseConfidence: Float,
    val notes: String?
)

data class WatchlistEntry(
    val companyId: Int,
    val companyName: String,
    val reason: String,
    val status: String
)

class SignatureDatabase {
    private var signatures: List<GlassesSignature> = emptyList()
    private var watchlist: List<WatchlistEntry> = emptyList()
    private var version: Int = 0

    companion object {
        private const val TAG = "SignatureDatabase"

        fun loadFromAssets(context: Context): SignatureDatabase {
            val db = SignatureDatabase()
            try {
                val jsonString = context.assets.open("signatures.json")
                    .bufferedReader().use { it.readText() }
                db.parseJson(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load signatures.json from assets", e)
            }
            return db
        }
    }

    private fun parseJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            version = root.optInt("version", 0)

            val sigArray = root.optJSONArray("signatures") ?: JSONArray()
            val sigs = mutableListOf<GlassesSignature>()
            for (i in 0 until sigArray.length()) {
                val obj = sigArray.getJSONObject(i)
                sigs.add(parseSignature(obj))
            }
            signatures = sigs

            val watchArray = root.optJSONArray("watchlist") ?: JSONArray()
            val wl = mutableListOf<WatchlistEntry>()
            for (i in 0 until watchArray.length()) {
                val obj = watchArray.getJSONObject(i)
                wl.add(WatchlistEntry(
                    companyId = parseCompanyId(obj.getString("companyId")),
                    companyName = obj.getString("companyName"),
                    reason = obj.optString("reason", ""),
                    status = obj.optString("status", "UNKNOWN")
                ))
            }
            watchlist = wl
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing signatures JSON", e)
        }
    }

    private fun parseSignature(obj: JSONObject): GlassesSignature {
        val hintsObj = obj.optJSONObject("payloadHints")
        val hints = hintsObj?.let {
            PayloadHints(
                typicalMinLength = if (it.has("typicalMinLength")) it.getInt("typicalMinLength") else null,
                typicalMaxLength = if (it.has("typicalMaxLength")) it.getInt("typicalMaxLength") else null,
                knownPrefixes = jsonArrayToStringList(it.optJSONArray("knownPrefixes"))
            )
        }

        return GlassesSignature(
            companyId = parseCompanyId(obj.getString("companyId")),
            companyName = obj.getString("companyName"),
            serviceUuid = obj.optString("serviceUuid", null),
            hasCamera = obj.optBoolean("hasCamera", false),
            knownProducts = jsonArrayToStringList(obj.optJSONArray("knownProducts")),
            knownFalsePositives = jsonArrayToStringList(obj.optJSONArray("knownFalsePositives")),
            deviceNamePatterns = jsonArrayToStringList(obj.optJSONArray("deviceNamePatterns")),
            payloadHints = hints,
            baseConfidence = obj.optDouble("baseConfidence", 0.30).toFloat(),
            notes = obj.optString("notes", null)
        )
    }

    private fun parseCompanyId(hex: String): Int {
        return hex.removePrefix("0x").toInt(16)
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun getSignatureByCompanyId(id: Int): GlassesSignature? {
        return signatures.find { it.companyId == id }
    }

    fun getAllActiveCompanyIds(): Set<Int> {
        return signatures.map { it.companyId }.toSet()
    }

    fun getServiceUuidForCompanyId(id: Int): String? {
        return signatures.find { it.companyId == id }?.serviceUuid
    }

    fun getDeviceNamePatterns(): List<String> {
        return signatures.flatMap { it.deviceNamePatterns }.distinct()
    }

    fun getAllSignatures(): List<GlassesSignature> = signatures

    fun getVersion(): Int = version
}
