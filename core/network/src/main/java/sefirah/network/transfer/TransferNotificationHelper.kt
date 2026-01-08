package sefirah.network.transfer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import sefirah.common.R
import sefirah.common.notifications.AppNotifications
import sefirah.common.notifications.NotificationCenter
import sefirah.network.NetworkService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles file transfer notifications.
 * Only responsible for displaying notifications, not formatting strings.
 */
@Singleton
class TransferNotificationHelper @Inject constructor(
    private val context: Context,
    private val notificationCenter: NotificationCenter
) {
    private val builders = mutableMapOf<String, NotificationCompat.Builder>()

    fun showProgress(
        transferId: String,
        title: String
    ) {
        val notificationId = transferId.hashCode()
        
        val cancelIntent = Intent(context, NetworkService::class.java).apply {
            action = NetworkService.Companion.Actions.CANCEL_TRANSFER.name
            putExtra(NetworkService.EXTRA_TRANSFER_ID, transferId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_PROGRESS_CHANNEL,
            notificationId = notificationId
        ) {
            setContentTitle(title)
            setContentText(context.getString(R.string.notification_transfer_preparing))
            setProgress(0, 0, true)
            setOngoing(true)
            setSilent(true)
            setAutoCancel(false)
            addAction(R.drawable.ic_close, context.getString(R.string.cancel), cancelPendingIntent)
        }
        builders[transferId] = builder
    }

    fun updateProgress(
        transferId: String,
        title: String,
        subText: String,
        contentText: String,
        progress: Int
    ) {
        val builder = builders[transferId] ?: return
        val notificationId = transferId.hashCode()
        
        notificationCenter.modifyNotification(builder, notificationId) {
            setContentTitle(title)
            setSubText(subText)
            setContentText(contentText)
            setProgress(100, progress, false)
        }
    }

    fun showCompleted(
        transferId: String,
        fileCount: Int,
        fileUri: Uri? = null,
        mimeType: String? = null
    ) {
        val notificationId = transferId.hashCode()
        builders.remove(transferId)
        notificationCenter.cancelNotification(notificationId)

        val contentText = if (fileCount > 1) {
            context.getString(R.string.notification_transfer_success_bulk, fileCount)
        } else {
            context.getString(R.string.notification_transfer_success)
        }

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_COMPLETE_CHANNEL,
            notificationId = notificationId + 1000
        ) {
            setContentTitle(context.getString(R.string.notification_file_transfer_complete))
            setContentText(contentText)
            setOngoing(false)
            setAutoCancel(true)
            setSilent(false)

            if (fileUri != null && mimeType != null) {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mimeType)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setContentIntent(pendingIntent)
            }
        }
    }

    fun showError(transferId: String, error: String) {
        val notificationId = transferId.hashCode()
        builders.remove(transferId)
        notificationCenter.cancelNotification(notificationId)

        notificationCenter.showNotification(
            channelId = AppNotifications.TRANSFER_ERROR_CHANNEL,
            notificationId = notificationId + 2000
        ) {
            setContentTitle(context.getString(R.string.notification_file_transfer_error))
            setContentText(error)
            setOngoing(false)
            setAutoCancel(true)
            setSilent(false)
        }
    }

    fun cancel(transferId: String) {
        val notificationId = transferId.hashCode()
        builders.remove(transferId)
        notificationCenter.cancelNotification(notificationId)
    }
}

