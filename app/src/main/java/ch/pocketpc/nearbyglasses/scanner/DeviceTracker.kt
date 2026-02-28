package ch.pocketpc.nearbyglasses.scanner

/**
 * Tracks BLE devices by fingerprint to deduplicate detections.
 * Since BLE uses randomized MAC addresses, we fingerprint devices
 * using companyId, manufacturer data prefix, length, and service UUIDs.
 */

data class DeviceFingerprint(
    val companyId: Int?,
    val mfgDataLength: Int,
    val mfgDataPrefix: String,
    val serviceUuidHash: Int
)

data class TrackedDevice(
    val fingerprint: DeviceFingerprint,
    var firstSeen: Long,
    var lastSeen: Long,
    var bestRssi: Int,
    var detectionCount: Int,
    var lastNotifiedAt: Long,
    var confidenceScore: Float
)

class DeviceTracker(
    private val deduplicationWindowMs: Long = 60_000L,
    private val maxTrackedDevices: Int = 50
) {
    private val trackedDevices = LinkedHashMap<DeviceFingerprint, TrackedDevice>()

    /**
     * Returns true if this is a NEW detection (not seen recently).
     * Returns false if it is a duplicate (already seen within the window).
     */
    fun trackAndCheckNew(
        companyId: Int?,
        mfgData: ByteArray?,
        serviceUuids: List<String>?,
        rssi: Int,
        confidenceScore: Float
    ): Pair<Boolean, TrackedDevice> {
        val fingerprint = createFingerprint(companyId, mfgData, serviceUuids)
        val now = System.currentTimeMillis()

        cleanupExpired(now)

        val existing = trackedDevices[fingerprint]
        if (existing != null) {
            existing.lastSeen = now
            existing.detectionCount++
            if (rssi > existing.bestRssi) existing.bestRssi = rssi
            if (confidenceScore > existing.confidenceScore) {
                existing.confidenceScore = confidenceScore
            }
            return Pair(false, existing)
        } else {
            val tracked = TrackedDevice(
                fingerprint = fingerprint,
                firstSeen = now,
                lastSeen = now,
                bestRssi = rssi,
                detectionCount = 1,
                lastNotifiedAt = 0L,
                confidenceScore = confidenceScore
            )
            trackedDevices[fingerprint] = tracked

            while (trackedDevices.size > maxTrackedDevices) {
                trackedDevices.entries.iterator().let {
                    if (it.hasNext()) { it.next(); it.remove() }
                }
            }

            return Pair(true, tracked)
        }
    }

    fun getActiveDeviceCount(): Int {
        val now = System.currentTimeMillis()
        return trackedDevices.count { (_, v) ->
            now - v.lastSeen < deduplicationWindowMs
        }
    }

    private fun createFingerprint(
        companyId: Int?,
        mfgData: ByteArray?,
        serviceUuids: List<String>?
    ): DeviceFingerprint {
        val prefix = mfgData?.take(4)
            ?.joinToString("") { "%02X".format(it) } ?: ""
        val uuidHash = serviceUuids?.sorted()?.hashCode() ?: 0
        return DeviceFingerprint(
            companyId = companyId,
            mfgDataLength = mfgData?.size ?: 0,
            mfgDataPrefix = prefix,
            serviceUuidHash = uuidHash
        )
    }

    private fun cleanupExpired(now: Long) {
        trackedDevices.entries.removeAll { (_, v) ->
            now - v.lastSeen > deduplicationWindowMs * 2
        }
    }

    fun clear() = trackedDevices.clear()
}
