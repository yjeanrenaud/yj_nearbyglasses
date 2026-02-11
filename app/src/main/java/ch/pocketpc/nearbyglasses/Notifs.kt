package com.example.nearbyglasses

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifs {
    const val CHANNEL_ID = "scan_status"
    const val WARN_CHANNEL_ID = "warnings"
    const val SERVICE_NOTIF_ID = 1000
    const val WARN_NOTIF_ID = 2000

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java)

        val status = NotificationChannel(
            CHANNEL_ID,
            "Scan Status",
            NotificationManager.IMPORTANCE_LOW
        )
        val warn = NotificationChannel(
            WARN_CHANNEL_ID,
            "Warnungen",
            NotificationManager.IMPORTANCE_HIGH
        )
        mgr.createNotificationChannel(status)
        mgr.createNotificationChannel(warn)
    }

    fun buildServiceNotification(ctx: Context, text: String) =
        NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BLE Scan aktiv")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun buildWarning(ctx: Context, deviceName: String) =
        NotificationCompat.Builder(ctx, WARN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Mögliche Smart Glasses in der Nähe")
            .setContentText("Gefunden: $deviceName")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
}
