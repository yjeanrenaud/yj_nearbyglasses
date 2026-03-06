package ch.pocketpc.nearbyglasses.scanner

import ch.pocketpc.nearbyglasses.R

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import ch.pocketpc.nearbyglasses.model.DetectionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothScanner(
    private val context: Context,
    private val rssiThreshold: Int,
    private val debugEnabled: Boolean,
    private val onDebugLog: ((String) -> Unit)?,
    private val debugCompanyIds: Set<Int>,
    private val confidenceThreshold: Float = 0.50f,
    private val batterySaverEnabled: Boolean = true,
    private val scanDurationMs: Long = 5000L,
    private val scanPauseMs: Long = 10000L,
    private val onDeviceDetected: (DetectionEvent) -> Unit
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val deviceTracker = DeviceTracker()
    private var scanJob: Job? = null

    private fun d(msg: String) {
        if (debugEnabled) onDebugLog?.invoke(msg)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            processScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { processScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, context.getString(R.string.dbg_scan_failed, errorCode))
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, context.getString(R.string.dbg_bluetooth_disabled))
            return false
        }

        if (_isScanning.value) {
            Log.w(TAG, context.getString(R.string.dbg_already_scanning))
            return false
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bleScanner == null) {
            Log.e(TAG, context.getString(R.string.dbg_ble_not_available))
            return false
        }

        try {
            if (batterySaverEnabled) {
                startCyclicScan()
            } else {
                startContinuousScan()
            }
            _isScanning.value = true
            if (debugEnabled) {
                Log.i(TAG, context.getString(R.string.dbg_ble_started_verbose, rssiThreshold))
            } else {
                Log.i(TAG, context.getString(R.string.dbg_ble_started_simple, rssiThreshold))
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.dbg_ble_start_error), e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startContinuousScan() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        bleScanner?.startScan(null, scanSettings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startCyclicScan() {
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        scanJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            while (isActive) {
                try {
                    bleScanner?.startScan(null, scanSettings, scanCallback)
                    delay(scanDurationMs)
                    bleScanner?.stopScan(scanCallback)
                    delay(scanPauseMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in cyclic scan", e)
                    delay(1000)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!_isScanning.value) {
            return
        }

        try {
            scanJob?.cancel()
            scanJob = null
            bleScanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.i(TAG, context.getString(R.string.dbg_ble_stopped))
        } catch (e: Exception) {
            Log.e(TAG, context.getString(R.string.dbg_ble_stop_error), e)
        }
    }

    private var lastUiDebugAt = 0L

    private fun dThrottled(msg: String, minIntervalMs: Long = 250) {
        if (!debugEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastUiDebugAt < minIntervalMs) return
        lastUiDebugAt = now
        onDebugLog?.invoke(msg)
    }

    private fun processScanResult(result: ScanResult) {
        val deviceAddress = result.device.address
        if (result.rssi < rssiThreshold) {
            if (debugEnabled) {
                Log.d(TAG, context.getString(R.string.dbg_filtered_rssi, result.device.address, result.rssi))
                d(context.getString(R.string.dbg_filtered_rssi, result.device.address, result.rssi))
            }
            return
        }

        val canReadDeviceIdentity =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        val deviceName: String? = when {
            !result.scanRecord?.deviceName.isNullOrBlank() -> result.scanRecord?.deviceName
            canReadDeviceIdentity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try { result.device.alias } catch (_: SecurityException) { null }
            }
            else -> null
        }

        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        var companyId: Int? = null
        var manufacturerDataHex: String? = null
        var manufacturerRawData: ByteArray? = null

        if (manufacturerData != null && manufacturerData.size() > 0) {
            companyId = manufacturerData.keyAt(0)
            val data = manufacturerData.valueAt(0)
            manufacturerRawData = data
            manufacturerDataHex = data.joinToString("") { "%02X".format(it) }
        }

        // Extract Service UUIDs
        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() }

        val nameSafe = deviceName ?: context.getString(R.string.dbg_placeholder_unknown)
        val companySafe = companyId?.let { "0x%04X".format(it) }
            ?: context.getString(R.string.dbg_placeholder_none)
        val uuidStr = serviceUuids?.joinToString() ?: context.getString(R.string.dbg_placeholder_none)
        dThrottled(
            context.getString(
                R.string.dbg_adv_short,
                deviceAddress,
                nameSafe,
                result.rssi,
                companySafe,
                manufacturerDataHex?.length?.div(2) ?: 0
            ) + " uuids=$uuidStr"
        )

        // Confidence scoring
        val mfgLen = manufacturerRawData?.size
        val (score, isAboveThreshold, reasonScored) = DetectionEvent.evaluateSmartGlasses(
            context, companyId, deviceName, serviceUuids, mfgLen, confidenceThreshold
        )

        val overrideMatch = debugEnabled && companyId != null && debugCompanyIds.contains(companyId)
        val isSmartGlasses = isAboveThreshold || overrideMatch
        val reason = when {
            overrideMatch -> context.getString(
                R.string.reason_debug_override_company_id,
                "0x%04X".format(companyId)
            )
            else -> reasonScored
        }

        if (debugEnabled) {
            Log.d(
                TAG,
                context.getString(
                    R.string.dbg_adv_full,
                    deviceAddress,
                    nameSafe,
                    result.rssi,
                    companySafe,
                    manufacturerDataHex?.length?.div(2) ?: 0,
                    isSmartGlasses,
                    reason
                ) + " score=%.0f%%".format(score * 100)
            )
        }

        if (isSmartGlasses) {
            // Device tracking / deduplication
            val (isNew, _) = deviceTracker.trackAndCheckNew(
                companyId, manufacturerRawData, serviceUuids, result.rssi, score
            )
            if (!isNew) {
                return
            }

            val event = DetectionEvent(
                timestamp = System.currentTimeMillis(),
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                rssi = result.rssi,
                companyId = companyId?.let { "0x${String.format("%04X", it)}" },
                companyName = companyId?.let { DetectionEvent.getCompanyName(context, it) }
                    ?: context.getString(R.string.company_unknown_plain),
                manufacturerData = manufacturerDataHex,
                detectionReason = reason,
                confidenceScore = score
            )

            Log.d(TAG, context.getString(R.string.dbg_smart_glasses_detected, event.deviceName ?: context.getString(R.string.dbg_placeholder_unknown), event.rssi))
            onDeviceDetected(event)
        }
    }

    fun getActiveDeviceCount(): Int = deviceTracker.getActiveDeviceCount()

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    companion object {
        private const val TAG = "BluetoothScanner"
    }
}
