package ch.pocketpc.nearbyglasses.model

import ch.pocketpc.nearbyglasses.R

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Parcelize
data class DetectionEvent(
    val timestamp: Long,
    val deviceAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val companyId: String?,
    val companyName: String,
    val manufacturerData: String?,
    val detectionReason: String,
    val confidenceScore: Float = 0f
) : Parcelable {

    fun toJson(): String {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("timestampFormatted",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(timestamp)))
            put("deviceAddress", deviceAddress)
            put("deviceName", deviceName ?: JSONObject.NULL)
            put("rssi", rssi)
            put("companyId", companyId ?: JSONObject.NULL)
            put("companyName", companyName)
            put("manufacturerData", manufacturerData ?: JSONObject.NULL)
            put("detectionReason", detectionReason)
            put("confidenceScore", confidenceScore.toDouble())
        }.toString(2)
    }

    fun toLogString(context: Context): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = dateFormat.format(Date(timestamp))
        val name = deviceName ?: context.getString(R.string.unknown_device)
        val pct = (confidenceScore * 100).toInt()
        return "[$time] $name (${rssi}dBm, ${pct}%) - $detectionReason"
    }

    companion object {
        // Meta Platforms, Inc. (formerly Facebook)
        const val META_COMPANY_ID1 = 0x01AB
        const val META_COMPANY_ID2 = 0x058E
        // EssilorLuxottica
        const val ESSILOR_COMPANY_ID = 0x0D53
        // Snap (Snapchat) Spectacles
        const val SNAP_COMPANY_ID = 0x03C2

        // Service UUIDs
        const val META_SERVICE_UUID = "0000fd5f-0000-1000-8000-00805f9b34fb"

        /**
         * Multi-criteria confidence scoring for smart glasses detection.
         * Returns Triple(score, isAboveThreshold, reasons)
         */
        fun evaluateSmartGlasses(
            context: Context,
            companyId: Int?,
            deviceName: String?,
            serviceUuids: List<String>?,
            manufacturerDataLength: Int?,
            confidenceThreshold: Float = 0.50f
        ): Triple<Float, Boolean, String> {
            var score = 0.0f
            val reasons = mutableListOf<String>()

            // Company ID scoring
            when (companyId) {
                META_COMPANY_ID1 -> {
                    score += 0.30f
                    reasons.add(context.getString(R.string.reason_meta_company_id, "0x01AB"))
                }
                META_COMPANY_ID2 -> {
                    score += 0.30f
                    reasons.add(context.getString(R.string.reason_meta_company_id, "0x058E"))
                }
                ESSILOR_COMPANY_ID -> {
                    score += 0.30f
                    reasons.add(context.getString(R.string.reason_essilor_company_id, "0x0D53"))
                }
                SNAP_COMPANY_ID -> {
                    score += 0.35f
                    reasons.add(context.getString(R.string.reason_snap_company_id, "0x03C2"))
                }
            }

            // Service UUID scoring
            serviceUuids?.forEach { uuid ->
                if (uuid.lowercase() == META_SERVICE_UUID) {
                    score += 0.30f
                    reasons.add(context.getString(R.string.reason_service_uuid, "0xFD5F"))
                }
            }

            // Device name scoring
            deviceName?.lowercase()?.let { name ->
                when {
                    name.contains("ray-ban") || name.contains("rayban") || name.contains("ray ban") -> {
                        score += 0.25f
                        reasons.add(context.getString(R.string.reason_name_contains, "Ray-Ban"))
                    }
                    name.contains("spectacles") -> {
                        score += 0.25f
                        reasons.add(context.getString(R.string.reason_name_contains, "Spectacles"))
                    }
                    name.contains("oakley") -> {
                        score += 0.25f
                        reasons.add(context.getString(R.string.reason_name_contains, "Oakley"))
                    }
                }
            }

            // Manufacturer data length heuristic
            manufacturerDataLength?.let { len ->
                if (len in 1..15) {
                    score += 0.15f
                    reasons.add(context.getString(R.string.reason_short_payload, len))
                } else if (len > 30) {
                    score -= 0.15f
                    reasons.add(context.getString(R.string.reason_long_payload, len))
                }
            }

            val clamped = score.coerceIn(0.0f, 1.0f)
            return Triple(clamped, clamped >= confidenceThreshold, reasons.joinToString(", "))
        }

        /**
         * Legacy method kept for backward compatibility.
         */
        @Deprecated("Use evaluateSmartGlasses() for confidence scoring",
            replaceWith = ReplaceWith("evaluateSmartGlasses(context, companyId, deviceName, null, null)"))
        fun isSmartGlasses(context: Context, companyId: Int?, deviceName: String?): Pair<Boolean, String> {
            val (_, isAbove, reason) = evaluateSmartGlasses(context, companyId, deviceName, null, null)
            return Pair(isAbove, reason)
        }

        fun getCompanyName(context: Context, companyId: Int): String {
            return when (companyId) {
                META_COMPANY_ID1,
                META_COMPANY_ID2 ->
                    context.getString(R.string.company_meta)
                ESSILOR_COMPANY_ID ->
                    context.getString(R.string.company_essilor)
                SNAP_COMPANY_ID ->
                    context.getString(R.string.company_snap)
                else ->
                    context.getString(
                        R.string.company_unknown,
                        "0x${String.format("%04X", companyId)}"
                    )
            }
        }
    }
}
