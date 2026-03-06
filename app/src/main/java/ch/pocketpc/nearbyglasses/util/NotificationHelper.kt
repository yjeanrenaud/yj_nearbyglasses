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
        const val NOTIFICATION_ID_DETECTION = 1001
        const val NOTIFICATION_ID_SERVICE = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val detectionChannel = NotificationChannel(
                CHANNEL_ID_DETECTION,
                context.getString(R.string.channel_detection_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_detection_description)
                enableVibration(true)
                setShowBadge(true)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_description)
                setShowBadge(false)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(detectionChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun showDetectionNotification(event: DetectionEvent) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deviceName = event.deviceName
            ?: context.getString(R.string.notification_unknown_device)

        val confidencePct = (event.confidenceScore * 100).toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_DETECTION)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(context.getString(R.string.notifyText))
            .setContentText(
                context.getString(
                    R.string.notification_detected_text_with_score,
                    deviceName,
                    event.rssi,
                    confidencePct
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(
                            R.string.notification_bigtext_with_score,
                            deviceName,
                            event.rssi,
                            event.detectionReason,
                            event.companyName,
                            confidencePct
                        )
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID_DETECTION, notification)
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
            .setContentTitle(context.getString(R.string.notification_service_title))
            .setContentText(context.getString(R.string.notification_service_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
