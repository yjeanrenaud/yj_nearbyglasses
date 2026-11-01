package ch.pocketpc.nearbyglasses

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import ch.pocketpc.nearbyglasses.model.DetectionEvent
import ch.pocketpc.nearbyglasses.scanner.BluetoothScanner
import ch.pocketpc.nearbyglasses.util.NotificationHelper
import ch.pocketpc.nearbyglasses.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BluetoothScanService : Service() {
    
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private var bluetoothScanner: BluetoothScanner? = null
    
    private val detectionListeners = mutableListOf<(DetectionEvent) -> Unit>()
    //private var lastNotificationTime = 0L
    private val lastNotificationTimeByMac = mutableMapOf<String, Long>()
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothScanService = this@BluetoothScanService
    }
    
    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        notificationHelper = NotificationHelper(this)
        //Log.d(TAG, "Service created")
        Log.d(TAG, getString(R.string.log_service_created))
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> startForegroundService()
            ACTION_STOP_SCAN -> stopScanningAndService()
        }
        return START_STICKY
    }
    
    private fun startForegroundService() {
        val notification = notificationHelper.createServiceNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                /*startForeground(
                    NotificationHelper.NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )*/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID_SERVICE,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
                }
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
            }
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }
        
        startScanning()
    }

    private val debugListeners = mutableListOf<(String) -> Unit>()

    fun addDebugListener(listener: (String) -> Unit) { debugListeners.add(listener) }
    fun removeDebugListener(listener: (String) -> Unit) { debugListeners.remove(listener) }

    private fun emitDebug(msg: String) {
        debugListeners.forEach { it(msg) }
    }

    fun startScanning() {
        if (bluetoothScanner?.isScanning?.value == true) {
            //Log.w(TAG, "Already scanning")
            Log.w(TAG, getString(R.string.log_already_scanning))
            return
        }
        
        val rssiThreshold = preferencesManager.rssiThreshold
        val debugEnabled = preferencesManager.debugEnabled
        val debugCompanyIds = preferencesManager.debugCompanyIds

        bluetoothScanner = BluetoothScanner(
            context = this,
            rssiThreshold = rssiThreshold,
            debugEnabled = debugEnabled,
            debugCompanyIds = debugCompanyIds,
            onDebugLog = { msg ->
                //Log.d(TAG, msg)          // still goes to Logcat
                //emitDebug(msg)           // now also goes to UI
                if (preferencesManager.canaryModeEnabled) return@BluetoothScanner
                val advOnly = preferencesManager.debugAdvOnly

                // Always send to UI (UI will filter too, but this reduces churn)
                if (!advOnly || msg.startsWith("ADV ")) {
                    emitDebug(msg)
                }

                // Optional: also reduce Logcat spam when ADV-only
                if (!advOnly || msg.startsWith("ADV ")) {
                    Log.d(TAG, msg)
                }
            },
            onDeviceDetected = { event ->
                handleDetection(event)
            }
        )

        // Removed coroutine launch to ensure isScanning() returns true immediately for UI updates
        //serviceScope.launch {
            val success = bluetoothScanner?.startScanning() ?: false
            if (success) {
                //Log.i(TAG, "Scanning started successfully")
                Log.i(TAG, getString(R.string.log_scanning_started))
            } else {
                //Log.e(TAG, "Failed to start scanning")
                Log.e(TAG, getString(R.string.log_scanning_failed))
            }
        //}
    }
    
    fun stopScanning() {
        bluetoothScanner?.stopScanning()
        bluetoothScanner = null
    }
    
    private fun stopScanningAndService() {
        stopScanning()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }


    private fun handleDetection(event: DetectionEvent) {

        // Always forward detections, including ignored devices.
        // MainActivity handles logging and the canary indicator.
        detectionListeners.forEach { it(event) }

        // Ignored devices must not trigger notifications.
        if (preferencesManager.isIgnoredMac(event.deviceAddress)) {
            return
        }

        // Canary Mode must never trigger detection notifications.
        if (preferencesManager.canaryModeEnabled) {
            return
        }

        // Respect the Notifications setting.
        if (!preferencesManager.notificationsEnabled) {
            return
        }

        // Per-device notification cooldown.
        val mac = event.deviceAddress.uppercase(java.util.Locale.ROOT)
        val currentTime = android.os.SystemClock.elapsedRealtime()
        val cooldown = preferencesManager.cooldownMs

        val lastTime = lastNotificationTimeByMac[mac]

        if (lastTime == null || currentTime - lastTime >= cooldown) {

            notificationHelper.showDetectionNotification(
                event,
                offerIgnored = preferencesManager.debugEnabled
            )

            lastNotificationTimeByMac[mac] = currentTime

        } else {
            Log.d(
                TAG,
                getString(R.string.log_notification_suppressed)
            )
        }
    }


    fun addDetectionListener(listener: (DetectionEvent) -> Unit) {
        detectionListeners.add(listener)
    }
    
    fun removeDetectionListener(listener: (DetectionEvent) -> Unit) {
        detectionListeners.remove(listener)
    }
    
    fun isScanning(): Boolean {
        return bluetoothScanner?.isScanning?.value == true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        serviceScope.cancel()
        //Log.d(TAG, "Service destroyed")
        Log.d(TAG, getString(R.string.log_service_destroyed))
    }
    
    companion object {
        private const val TAG = "BluetoothScanService"
        const val ACTION_START_SCAN = "ch.pocketpc.nearbyglasses.START_SCAN"
        const val ACTION_STOP_SCAN = "ch.pocketpc.nearbyglasses.STOP_SCAN"
    }
}
