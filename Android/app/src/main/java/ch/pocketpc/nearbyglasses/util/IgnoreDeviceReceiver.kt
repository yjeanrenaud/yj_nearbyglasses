package ch.pocketpc.nearbyglasses.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

class IgnoreDeviceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != ACTION_IGNORE_MAC) return

        val preferences = PreferencesManager(context)

        // Only allow notification add2ignore in debug mode
        if (!preferences.debugEnabled) return

        val mac = PreferencesManager.normaliseMac(
            intent.getStringExtra(EXTRA_MAC).orEmpty()
        ) ?: return

        // add the mac
        val added = preferences.addIgnoredMac(mac)
        // Dismiss only this device's notification.
        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            -1
        )

        if (notificationId != -1) {
            NotificationManagerCompat.from(context)
                .cancel(notificationId)
        }
        Toast.makeText(
            context,
            if (added) {
                "$mac added to ignore list"
            } else {
                "$mac is already ignored"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val ACTION_IGNORE_MAC =
            "ch.pocketpc.nearbyglasses.action.IGNORE_MAC"

        const val EXTRA_MAC =
            "ch.pocketpc.nearbyglasses.extra.IGNORE_MAC"
        const val EXTRA_NOTIFICATION_ID =
            "ch.pocketpc.nearbyglasses.extra.NOTIFICATION_ID"
    }
}