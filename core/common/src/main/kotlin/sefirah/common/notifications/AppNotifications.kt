package sefirah.common.notifications

import android.app.NotificationChannelGroup
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_LOW
import sefirah.common.R

/**
 * Centralized notification management for the entire application
 */
object AppNotifications {
    
    // File Transfer related constants
    private const val FILE_TRANSFER_GROUP = "group_file_transfer"
    const val TRANSFER_PROGRESS_CHANNEL = "file_transfer_progress_channel"
    const val TRANSFER_COMPLETE_CHANNEL = "file_transfer_complete_channel"
    const val TRANSFER_ERROR_CHANNEL = "file_transfer_error_channel"
    const val TRANSFER_PROGRESS_ID = 1001
    const val TRANSFER_COMPLETE_ID = 1002
    const val TRANSFER_ERROR_ID = 1003
    
    // Network related constants
    private const val NETWORK_GROUP = "group_network"
    const val DEVICE_CONNECTION_CHANNEL = "device_connection_channel"
    const val NETWORK_ERROR_CHANNEL = "network_error_channel"
    const val DEVICE_CONNECTION_ID = 2001
    const val NETWORK_ERROR_ID = 2002
    
    // Media related constants
    private const val MEDIA_GROUP = "group_media"
    const val MEDIA_PLAYBACK_CHANNEL = "media_playback_channel"
    const val MEDIA_PLAYBACK_ID = 3001
    const val MEDIA_CONTROL_ID = 3002

    /**
     * Creates the notification channels introduced in Android Oreo.
     * This won't do anything on Android versions that don't support notification channels.
     *
     * @param context The application context.
     */
    fun createChannels(context: Context) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Create notification groups
        notificationManager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(FILE_TRANSFER_GROUP, context.getString(R.string.group_file_transfer)),
                NotificationChannelGroup(NETWORK_GROUP, context.getString(R.string.group_network)),
                NotificationChannelGroup(MEDIA_GROUP, context.getString(R.string.group_media))
            )
        )

        // Create notification channels
        notificationManager.createNotificationChannelsCompat(
            listOf(
                // File Transfer Channels
                buildNotificationChannel(TRANSFER_PROGRESS_CHANNEL, IMPORTANCE_LOW) {
                    setName(context.getString(R.string.channel_transfer_progress))
                    setGroup(FILE_TRANSFER_GROUP)
                    setShowBadge(false)
                },
                buildNotificationChannel(TRANSFER_COMPLETE_CHANNEL, IMPORTANCE_DEFAULT) {
                    setName(context.getString(R.string.channel_transfer_complete))
                    setGroup(FILE_TRANSFER_GROUP)
                },
                
                // Network Channels
                buildNotificationChannel(DEVICE_CONNECTION_CHANNEL, IMPORTANCE_LOW) {
                    setName(context.getString(R.string.channel_device_connection))
                    setGroup(NETWORK_GROUP)
                    setShowBadge(false)
                },
                
                // Media Channels
                buildNotificationChannel(MEDIA_PLAYBACK_CHANNEL, IMPORTANCE_LOW) {
                    setName(context.getString(R.string.channel_media_playback))
                    setGroup(MEDIA_GROUP)
                    setShowBadge(false)
                }
            )
        )
    }
} 