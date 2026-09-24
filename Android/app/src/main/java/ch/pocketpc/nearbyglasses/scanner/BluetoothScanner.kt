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
import ch.pocketpc.nearbyglasses.model.DetectionEvent.Companion.PRIMARY_SERVICE_UUID_STRING

class BluetoothScanner(
    private val context: Context,
    private val rssiThreshold: Int,
    private val debugEnabled: Boolean,
    private val onDebugLog: ((String) -> Unit)?,
    private val debugCompanyIds: Set<Int>,
    private val onDeviceDetected: (DetectionEvent) -> Unit
) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bleScanner: BluetoothLeScanner? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    //logger helper
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
            //Log.e(TAG, "Scan failed with error code: $errorCode")
            Log.e(TAG, context.getString(R.string.dbg_scan_failed, errorCode))
            _isScanning.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        if (!isBluetoothEnabled()) {
            //Log.w(TAG, "Bluetooth is not enabled")
            Log.w(TAG, context.getString(R.string.dbg_bluetooth_disabled))
            return false
        }
        
        if (_isScanning.value) {
            //Log.w(TAG, "Already scanning")
            Log.w(TAG, context.getString(R.string.dbg_already_scanning))
            return false
        }
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bleScanner == null) {
            //Log.e(TAG, "BLE scanner not available")
            Log.e(TAG, context.getString(R.string.dbg_ble_not_available))
            return false
        }
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        
        try {
            bleScanner?.startScan(null, scanSettings, scanCallback)
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
    fun stopScanning() {
        if (!_isScanning.value) {
            return
        }
        
        try {
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
        // Check RSSI threshold
        if (result.rssi < rssiThreshold) {
            if (debugEnabled) {
                //Log.d(TAG, "Filtered by RSSI: ${result.device.address} rssi=${result.rssi}")
                Log.d(TAG,context.getString(R.string.dbg_filtered_rssi,result.device.address, result.rssi)
                )
                //d("Filtered RSSI addr=${result.device.address} rssi=${result.rssi}")
                d(context.getString(R.string.dbg_filtered_rssi,result.device.address, result.rssi))
            }
            return
        }
        val canReadDeviceIdentity =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

        val deviceName: String? = when {
            // Prefer scan record name (doesn't require CONNECT)
            !result.scanRecord?.deviceName.isNullOrBlank() -> result.scanRecord?.deviceName

            // Only touch device.alias if CONNECT is granted
            canReadDeviceIdentity && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try { result.device.alias } catch (_: SecurityException) { null }
            }

            else -> null
        }
        /*val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result.device.alias ?: result.scanRecord?.deviceName
        } else {
            result.scanRecord?.deviceName
        }*/
        
        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        var companyId: Int? = null
        var manufacturerDataHex: String? = null
        
        // Extract company ID from manufacturer data
        if (manufacturerData != null && manufacturerData.size() > 0) {
            companyId = manufacturerData.keyAt(0)
            val data = manufacturerData.valueAt(0)
            manufacturerDataHex = data.joinToString("") { "%02X".format(it) }
        }
        //just d(...) is to fast for most UI
        //val companyIdStr = companyId?.let { "0x%04X".format(it) } ?: "none"
        //dThrottled("ADV addr=$deviceAddress name=${deviceName ?: "?"} rssi=${result.rssi} companyId=$companyIdStr")
        //dThrottled("ADV addr=$deviceAddress name=${deviceName ?: "?"} " + "rssi=${result.rssi} companyId=$companyIdStr len=${manufacturerDataHex?.length?.div(2) ?: 0}")
        val nameSafe = deviceName ?: context.getString(R.string.dbg_placeholder_unknown)
        val companySafe = companyId?.let { "0x%04X".format(it) }
            ?: context.getString(R.string.dbg_placeholder_none)
        dThrottled(
            context.getString(
                R.string.dbg_adv_short,
                deviceAddress,
                nameSafe,
                result.rssi,
                companySafe,
                manufacturerDataHex?.length?.div(2) ?: 0
            )
        )
        // check UUID of primary service announcement
        val scanRecord = result.scanRecord

        /*val hasPrimaryService =
            scanRecord
                ?.serviceUuids
                .orEmpty()
                .any { parcelUuid ->
                    parcelUuid.uuid == DetectionEvent.PRIMARY_SERVICE_UUID
                }*/
        // UUID may also appear as the key of advertised service data
        // try to make sure we spot that service UUID announcement
        val uuidInServiceList =
            scanRecord
                ?.serviceUuids
                .orEmpty()
                .any { parcelUuid ->
                    parcelUuid.uuid == DetectionEvent.PRIMARY_SERVICE_UUID
                }

        val uuidInServiceData =
            scanRecord
                ?.serviceData
                ?.keys
                .orEmpty()
                .any { parcelUuid ->
                    parcelUuid.uuid == DetectionEvent.PRIMARY_SERVICE_UUID
                }

        val hasPrimaryService =
            uuidInServiceList || uuidInServiceData

        // for to check if this is a smart glasses device (including our debug override)
        val (isSmartGlassesReal, reasonReal) = DetectionEvent.isSmartGlasses(context,companyId, deviceName, hasPrimaryService)
        //val overrideMatch = companyId != null && debugCompanyIds.contains(companyId)
        //only when debug is on AND company IDs are entered
        val overrideMatch = debugEnabled && companyId != null && debugCompanyIds.contains(companyId)

        val isSmartGlasses = isSmartGlassesReal || overrideMatch
        val reason = when {
            //overrideMatch -> "Debug override: Company ID 0x%04X matched".format(companyId)
            overrideMatch -> context.getString(
                R.string.reason_debug_override_company_id,
                "0x%04X".format(companyId)
            )
            else -> reasonReal
        }

        if (debugEnabled) {
            /*Log.d(
                TAG,
                "ADV addr=$deviceAddress name=${deviceName ?: "?"} rssi=${result.rssi} " +
                        "companyId=${companyId?.let { "0x%04X".format(it) } ?: "none"} " +
                        "mfgLen=${manufacturerDataHex?.length?.div(2) ?: 0} smartglasses=$isSmartGlasses reason=$reason"
            )*/

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
                )
            )
            if (hasPrimaryService) {
                Log.d(
                    TAG,
                    "UUID=$PRIMARY_SERVICE_UUID_STRING, address=$deviceAddress"
                )
            }
        }

        if (isSmartGlasses) {
            val event = DetectionEvent(
                timestamp = System.currentTimeMillis(),
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                rssi = result.rssi,
                companyId = companyId?.let { "0x${String.format("%04X", it)}" },
                //companyName = companyId?.let { DetectionEvent.getCompanyName(context,it) } ?: "Unknown",
                companyName = companyId?.let { DetectionEvent.getCompanyName(context, it) }
                    ?: context.getString(R.string.company_unknown_plain),
                manufacturerData = manufacturerDataHex,
                detectionReason = reason
            )
            
            //Log.d(TAG, "smart glasses detected: ${event.deviceName} (${event.rssi} dBm)")
            Log.d(TAG,context.getString(R.string.dbg_smart_glasses_detected,event.deviceName ?: context.getString(R.string.dbg_placeholder_unknown),event.rssi))
            onDeviceDetected(event)
        }
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    companion object {
        private const val TAG = "BluetoothScanner"
    }
}
