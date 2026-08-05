package ch.pocketpc.nearbyglasses.model

import ch.pocketpc.nearbyglasses.R

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID //for more precise detections

@Parcelize
data class DetectionEvent(
    val timestamp: Long,
    val deviceAddress: String,
    val deviceName: String?,
    val rssi: Int,
    val companyId: String?,
    val companyName: String,
    val manufacturerData: String?,
    val detectionReason: String
) : Parcelable {

    fun toJson(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
            {
                "timestamp": $timestamp,
                "timestampFormatted": "${dateFormat.format(Date(timestamp))}",
                "deviceAddress": "$deviceAddress",
                "deviceName": ${deviceName?.let { "\"$it\"" } ?: "null"},
                "rssi": $rssi,
                "companyId": ${companyId?.let { "\"$it\"" } ?: "null"},
                "companyName": "$companyName",
                "manufacturerData": ${manufacturerData?.let { "\"$it\"" } ?: "null"},
                "detectionReason": "$detectionReason"
            }
        """.trimIndent()
    }

    fun toLogString(context: Context): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = dateFormat.format(Date(timestamp))
        val name = deviceName ?: context.getString(R.string.unknown_device)
        //return "[$time] ${deviceName ?: "Unknown"} (${rssi}dBm) - $detectionReason"
        return "[$time] $name (${rssi}dBm) - $detectionReason"

    }

    companion object {
        // Meta Platforms, Inc. (formerly Facebook)
        const val META_COMPANY_ID1 = 0x01AB
        const val META_COMPANY_ID2 = 0x058E
        // EssilorLuxottica (Luxottica Group S.p.A.) - parent of Oakley; confirmed on Oakley HSTN Meta via name match
        const val ESSILOR_COMPANY_ID = 0x0D53
        //Snap (Snapchat) Spectacles
        const val SNAP_COMPANY_ID = 0x03C2
        const val ZHUHAI_COMPANY_ID = 0x05D6
        const val PRIMARY_SERVICE_UUID_STRING = "7905FFF0-B5CE-4E99-A40F-4B1E122D00D0"
        val PRIMARY_SERVICE_UUID: UUID = UUID.fromString(PRIMARY_SERVICE_UUID_STRING)

        fun isSmartGlasses(context: Context, companyId: Int?,deviceName: String?, hasPrimaryService: Boolean = false): Pair<Boolean, String>
        {
            val reasons = mutableListOf<String>()

            // Check company ID
            if (companyId == META_COMPANY_ID1) {
                //reasons.add("Meta Company ID (0x01AB)")
                reasons.add(context.getString(
                    R.string.reason_meta_company_id,
                    "0x01AB"))
            }
            if (companyId == META_COMPANY_ID2) {
                //reasons.add("Meta Company ID (0x058E)")
                reasons.add(context.getString(
                    R.string.reason_meta_company_id,
                    "0x058E"))
            }

            if (companyId == ESSILOR_COMPANY_ID) {
                //reasons.add("EssilorLuxottica Company ID (0x0D53)")
                reasons.add(context.getString(
                    R.string.reason_essilor_company_id,
                    "0x0D53"))
            }

            if (companyId == SNAP_COMPANY_ID) {
                //reasons.add("Snap Company ID (0x03C2)")
                reasons.add(context.getString(
                    R.string.reason_snap_company_id,
                    "0x03C2"))
            }
            if (companyId == ZHUHAI_COMPANY_ID) {
                //reasons.add("Zhuhai Jieli technology ID (0x05D6)")
                reasons.add(context.getString(
                    R.string.reason_zhuhai_company_id,
                    "0x05D6"))
            }
            //Check for HeyCyan's primary service UUID
            if (hasPrimaryService) {
                reasons.add(
                    context.getString(
                        R.string.reason_primary_service_uuid,
                        PRIMARY_SERVICE_UUID_STRING
                    )
                )
            }
            // Check device name
            deviceName?.let { name ->
                val nameLower = name.lowercase()
                when {
                    //nameLower.contains("rayban") -> reasons.add("Device name contains 'rayban'")
                    //nameLower.contains("ray-ban") -> reasons.add("Device name contains 'ray-ban'")
                    //nameLower.contains("ray ban") -> reasons.add("Device name contains 'ray ban'")

                    nameLower.contains("rayban") -> reasons.add(
                        context.getString(R.string.reason_name_contains,"rayban")
                    )
                    nameLower.contains("ray-ban") -> reasons.add(
                        context.getString(R.string.reason_name_contains,"ray-ban")
                    )
                    nameLower.contains("ray ban") -> reasons.add(
                        context.getString(R.string.reason_name_contains,"ray ban")
                    )
                    nameLower.contains("heycyan") -> reasons.add(
                        context.getString(R.string.reason_name_contains,"HeyCyan")
                    ) //Nilox Smart AI Glasses and alike
                    nameLower.contains("oakley") -> reasons.add(
                        context.getString(R.string.reason_name_contains,"Oakley")
                    ) //Oakley HSTN Meta (e.g. "Oakley Meta 099H")

                    else -> {} // do nothing
                }
            }

            return Pair(reasons.isNotEmpty(), reasons.joinToString(", "))
        }

        fun getCompanyName(context: Context, companyId: Int): String {
            return when (companyId) {
                //META_COMPANY_ID1 -> "Meta Platforms, Inc."
                //META_COMPANY_ID2 -> "Meta Platforms, Inc."
                //ESSILOR_COMPANY_ID -> "EssilorLuxottica"
                META_COMPANY_ID1,
                META_COMPANY_ID2 ->
                    context.getString(R.string.company_meta)
                ESSILOR_COMPANY_ID ->
                    context.getString(R.string.company_essilor)
                SNAP_COMPANY_ID ->
                    context.getString(R.string.company_snap)
                else -> //"Unknown (0x${String.format("%04X", companyId)})"
                    context.getString(
                        R.string.company_unknown,
                        "0x${String.format("%04X", companyId)}"
                    )
            }
        }
    }
}
