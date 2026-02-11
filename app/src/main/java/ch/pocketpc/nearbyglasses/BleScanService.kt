package com.example.nearbyglasses

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat

class BleScanService : Service() {

    companion object {
        const val ACTION_LOG_EVENT = "com.example.nearbyglasses.LOG_EVENT"
        const val EXTRA_NAME = "name"
        const val EXTRA_RSSI = "rssi"
        const val EXTRA_TIME = "time"
    }

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var lastWarnAt = 0L

    override fun onCreate() {
        super.onCreate()
        Notifs.ensureChannels(this)

        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = btManager.adapter
        scanner = adapter?.bluetoothLeScanner

        startForeground(
            Notifs.SERVICE_NOTIF_ID,
            Notifs.buildServiceNotification(this, "Suche nach Geräten in der Nähe…")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startScan()
        return START_STICKY
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startScan() {
        if (isScanning) return
        val s = scanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        s.startScan(null, settings, callback)
        isScanning = true
    }

    private fun stopScan() {
        if (!isScanning) return
        scanner?.stopScan(callback)
        isScanning = false
    }

    private fun vibrateAlert() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        // Kurzes Muster: 100ms an, 80ms aus, 120ms an
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 120), -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 80, 120), -1)
        }
    }

    private fun broadcastLog(name: String, rssi: Int, timeMs: Long) {
        val i = Intent(ACTION_LOG_EVENT).apply {
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_RSSI, rssi)
            putExtra(EXTRA_TIME, timeMs)
        }
        sendBroadcast(i)
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val keywords = Settings.getKeywords(this@BleScanService)
            val rssiThreshold = Settings.getRssiThreshold(this@BleScanService)
            val cooldownMs = Settings.getCooldownMs(this@BleScanService)

            if (!Heuristics.isSuspicious(result, keywords, rssiThreshold)) return

            val now = System.currentTimeMillis()
            val name = Heuristics.displayName(result)

            // Immer ins Log senden (auch wenn Cooldown blockt) — optional
            broadcastLog(name, result.rssi, now)

            if (now - lastWarnAt < cooldownMs) return
            lastWarnAt = now

            vibrateAlert()

            val nm = NotificationManagerCompat.from(this@BleScanService)
            nm.notify(Notifs.WARN_NOTIF_ID, Notifs.buildWarning(this@BleScanService, name))

            // Service-Status-Notification aktualisieren
            nm.notify(
                Notifs.SERVICE_NOTIF_ID,
                Notifs.buildServiceNotification(
                    this@BleScanService,
                    "Verdächtig: $name (RSSI ${result.rssi} dBm)"
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            val nm = NotificationManagerCompat.from(this@BleScanService)
            nm.notify(
                Notifs.SERVICE_NOTIF_ID,
                Notifs.buildServiceNotification(this@BleScanService, "Scan fehlgeschlagen: $errorCode")
            )
        }
    }
}
