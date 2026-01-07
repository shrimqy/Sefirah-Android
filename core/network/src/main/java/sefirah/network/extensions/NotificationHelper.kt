package sefirah.network.extensions

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import sefirah.clipboard.ClipboardChangeActivity
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.domain.model.PendingDeviceApproval
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.Actions
import sefirah.network.NetworkService.Companion.DEVICE_ID_EXTRA

fun NetworkService.showPairingVerificationNotification(approval: PendingDeviceApproval) {
    val mainIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val mainPendingIntent: PendingIntent = PendingIntent.getActivity(
        this,
        approval.deviceId.hashCode(),
        mainIntent,
        PendingIntent.FLAG_IMMUTABLE
    )

    // Approve Intent
    val approveIntent = Intent(this, NetworkService::class.java).apply {
        action = Actions.APPROVE_DEVICE.name
        putExtra(DEVICE_ID_EXTRA, approval.deviceId)
    }
    val approvePendingIntent: PendingIntent = PendingIntent.getService(
        this,
        approval.deviceId.hashCode() + 1,
        approveIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Reject Intent
    val rejectIntent = Intent(this, NetworkService::class.java).apply {
        action = Actions.REJECT_DEVICE.name
        putExtra(DEVICE_ID_EXTRA, approval.deviceId)
    }
    val rejectPendingIntent: PendingIntent = PendingIntent.getService(
        this,
        approval.deviceId.hashCode() + 2,
        rejectIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val contentText = getString(
        R.string.notification_pairing_request_text,
        approval.deviceName,
        approval.verificationCode
    )

    notificationCenter.showNotification(
        channelId = AppNotifications.PAIRING_REQUEST_CHANNEL,
        notificationId = AppNotifications.PAIRING_REQUEST_ID + approval.deviceId.hashCode(),
    ) {
        setContentTitle(getString(R.string.notification_pairing_request_title))
        setContentText(contentText)
        setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        setContentIntent(mainPendingIntent)
        setAutoCancel(true)
        setPriority(NotificationCompat.PRIORITY_MAX)
        setCategory(NotificationCompat.CATEGORY_SOCIAL)
        setDefaults(NotificationCompat.DEFAULT_ALL)
        addAction(R.drawable.ic_launcher_foreground, getString(R.string.notification_pairing_approve), approvePendingIntent)
        addAction(R.drawable.ic_launcher_foreground, getString(R.string.notification_pairing_reject), rejectPendingIntent)
    }
}

fun NetworkService.cancelPairingVerificationNotification(deviceId: String) {
    notificationCenter.cancelNotification(AppNotifications.PAIRING_REQUEST_ID + deviceId.hashCode())
}

/**
 * Sets the foreground notification based on connection state
 * @param deviceName Device name(s) to display (comma-separated if multiple)
 * @param deviceId Device ID for disconnect action (only used when single device connected)
 * @param notificationId Notification ID to use
 */
fun NetworkService.setNotification(
    deviceName: String?,
    deviceId: String?,
    notificationId: Int
) {
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

    val contentText =  if (deviceName.isNullOrEmpty()) {
        getString(R.string.notification_status_disconnected)
    } else getString(R.string.notification_status_connected, deviceName)

    notificationBuilder = notificationCenter.showNotification(
        channelId = AppNotifications.DEVICE_CONNECTION_CHANNEL,
        notificationId = notificationId,
    ) {
        setContentTitle(getString(R.string.notification_device_connection))
        setContentText(contentText)
        setContentIntent(mainPendingIntent)
        setOngoing(true)
        setSilent(true)
        setShowWhen(false)

        // Only add actions if connected
        if (!deviceName.isNullOrEmpty()) {
            // Disconnect action - only if single device (deviceId provided)
            if (deviceId != null) {
                val disconnectIntent = Intent(this@setNotification, NetworkService::class.java).apply {
                    action = Actions.DISCONNECT.name
                    putExtra(DEVICE_ID_EXTRA, deviceId)
                }
                val disconnectPendingIntent: PendingIntent =
                    PendingIntent.getService(this@setNotification, 0, disconnectIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                addAction(R.drawable.ic_launcher_foreground, getString(R.string.notification_disconnect_action), disconnectPendingIntent)
            }
            addAction(R.drawable.ic_launcher_foreground, getString(R.string.notification_clipboard_action), clipboardPendingIntent)
        }
    }
    startForeground(notificationId, notificationBuilder.build())
}