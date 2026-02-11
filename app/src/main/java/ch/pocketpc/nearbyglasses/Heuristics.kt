package com.example.nearbyglasses

import android.bluetooth.le.ScanResult

object Heuristics {

    fun isSuspicious(result: ScanResult, keywords: List<String>, rssiThreshold: Int): Boolean {
        val name = (result.scanRecord?.deviceName ?: result.device.name ?: "")
            .trim()
            .lowercase()

        val nameMatch = keywords.any { it.isNotBlank() && it in name }
        val rssiOk = result.rssi >= rssiThreshold

        return nameMatch && rssiOk
    }

    fun displayName(result: ScanResult): String {
        val n = (result.scanRecord?.deviceName ?: result.device.name ?: "").trim()
        return if (n.isBlank()) "(unbenannt)" else n
    }
}
