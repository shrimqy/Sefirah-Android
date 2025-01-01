package sefirah.network.extensions

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import sefirah.clipboard.ClipboardChangeActivity
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions

fun NetworkService.setNotification(isConnected: Boolean, deviceName: String? = null) {
    // Disconnect Intent
    val disconnectIntent = Intent(this, NetworkService::class.java).apply {
        action = Actions.STOP.name
        putExtra(EXTRA_NOTIFICATION_ID, 0)
    }
    val disconnectPendingIntent: PendingIntent =
        PendingIntent.getService(this, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE)

    // Main Activity Intent
    val mainIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val mainPendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

    // Clipboard copy action intent
    val clipboardIntent = Intent(this, ClipboardChangeActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
    }
    val clipboardPendingIntent: PendingIntent =
        PendingIntent.getActivity(this, 0, clipboardIntent, PendingIntent.FLAG_IMMUTABLE)

    val contentText = if (isConnected) "Connected to $deviceName" else "Trying to connect to $deviceName"
    val actionText = if (isConnected) "Disconnect" else "Stop"

    notificationBuilder = notificationCenter.showNotification(
        channelId = AppNotifications.DEVICE_CONNECTION_CHANNEL,
        notificationId = AppNotifications.DEVICE_CONNECTION_ID,
    ) {
        setContentTitle(getString(R.string.notification_device_connection))
        setContentText(contentText)
        setContentIntent(mainPendingIntent)
        setOngoing(true)
        setSilent(true)
        .addAction(R.drawable.ic_launcher_foreground, actionText, disconnectPendingIntent)
        .addAction(R.drawable.ic_launcher_foreground, getString(R.string.notification_clipboard_action), clipboardPendingIntent)
    }
    startForeground(AppNotifications.DEVICE_CONNECTION_ID, notificationBuilder.build())
}