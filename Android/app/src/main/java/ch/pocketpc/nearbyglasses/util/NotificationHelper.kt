package ch.pocketpc.nearbyglasses.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.pocketpc.nearbyglasses.MainActivity
import ch.pocketpc.nearbyglasses.R
import ch.pocketpc.nearbyglasses.model.DetectionEvent

class NotificationHelper(private val context: Context) {

    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        const val CHANNEL_ID_DETECTION = "glasses_detection"
        const val CHANNEL_ID_SERVICE = "glasses_service"
        //const val NOTIFICATION_ID_DETECTION = 1001
        const val NOTIFICATION_ID_SERVICE = 1002

        fun detectionNotificationId(mac: String): Int {
            // Stable ID derived from the Bluetooth MAC
            // Keep IDs in a range separate from service notification 1002
            return 0x40000000 or
                    (mac.uppercase(java.util.Locale.ROOT).hashCode() and 0x3FFFFFFF)
        }

    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Detection channel (high priority)
            val detectionChannel = NotificationChannel(
                CHANNEL_ID_DETECTION,
                context.getString(R.string.channel_detection_name), //"Smart Glasses Detection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_detection_description) //description = "Alerts when smart glasses are detected"
                enableVibration(true)
                setShowBadge(true)
            }

            // Service channel (low priority)
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                context.getString(R.string.channel_service_name), //"Background Scanning Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_description) //description = "Shows when the app is scanning in the background"
                setShowBadge(false)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(detectionChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }


    fun showDetectionNotification(event: DetectionEvent,offerIgnored: Boolean = false) {
        // Unique, stable notification ID for each MAC address
        val notificationId = detectionNotificationId(event.deviceAddress)
//            if (offerIgnored) {
//                putExtra(
//                    MainActivity.EXTRA_IGNORED_MAC,
//                    event.deviceAddress
//                )
//            }

        // Differentiate PendingIntents for different devices
        val deviceUri = android.net.Uri.Builder()
            .scheme("nearbyglasses")
            .authority("detection")
            .appendPath(event.deviceAddress)
            .build()

        // Open MainActivity when tapping the notification body
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            data = deviceUri
        }
        /*  val pendingIntent = PendingIntent.getActivity(
              context,
              0,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          ) */
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        // Ignore button is only available in debug mode
        val ignorePendingIntent = if (offerIgnored) {

            val ignoreIntent = Intent(
                context,
                IgnoreDeviceReceiver::class.java
            ).apply {
                action = IgnoreDeviceReceiver.ACTION_IGNORE_MAC
                data = deviceUri

                putExtra(
                    IgnoreDeviceReceiver.EXTRA_MAC,
                    event.deviceAddress
                )

                putExtra(
                    IgnoreDeviceReceiver.EXTRA_NOTIFICATION_ID,
                    notificationId
                )
            }

            PendingIntent.getBroadcast(
                context,
                notificationId,
                ignoreIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        } else {
            null
        }

        val deviceName = event.deviceName
            ?: context.getString(R.string.notification_unknown_device)

        // Notification details
        val details = context.getString(
            R.string.notification_bigtext,
            deviceName,
            event.rssi,
            event.detectionReason,
            event.companyName
        ) + if (offerIgnored) {
            "\nMAC: ${event.deviceAddress}"
        } else {
            ""
        }

        // Build detection notification
        val builder = NotificationCompat.Builder(
            context,
            CHANNEL_ID_DETECTION
        )
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(context.getString(R.string.notifyText))
            //.setContentText("${event.deviceName ?: "Unknown device"} detected (RSSI: ${event.rssi} dBm)")
            .setContentText(
                context.getString(
                    R.string.notification_detected_text,
                    deviceName,
                    event.rssi
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(details)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Add Ignore button only in debug mode
        if (ignorePendingIntent != null) {
            builder.addAction(
                0,
                context.getString(R.string.btnIgnore),
                ignorePendingIntent
            )
        }

        // Different MAC addresses produce separate notifications
        notificationManager.notify(
            notificationId,
            builder.build()
        )
    }


    fun createServiceNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_bluetooth_searching)
            //.setContentTitle("Scanning for smart glasses nearby")
            //.setContentText("Background scanning is active")
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
