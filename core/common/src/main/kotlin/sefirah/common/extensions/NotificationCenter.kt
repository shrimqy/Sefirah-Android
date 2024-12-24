package sefirah.common.extensions

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import sefirah.common.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun showNotification(
        channelId: String,
        channelName: String,
        notificationId: Int,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        builder: NotificationCompat.Builder.() -> Unit = {}
    ) : NotificationCompat.Builder {
        return buildNotification(
            context = context,
            channelId = channelId,
            channelName = channelName,
            importance = importance,
            builder = builder
        ).apply {
            manager.notify(notificationId, build())
        }
    }
    /**
     * Dismiss the notification
     *
     * @param notificationId the id of the notification
     */
    fun cancelNotification(notificationId: Int) = manager.cancel(notificationId)


    private fun buildNotification(
        context: Context,
        channelId: String,
        channelName: String,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
        builder: NotificationCompat.Builder.() -> Unit,
    ): NotificationCompat.Builder {
        val channel = NotificationChannel(channelId, channelName, importance)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(context, channelId).apply {
            setSmallIcon(R.mipmap.ic_launcher_round)
            priority = NotificationCompat.PRIORITY_DEFAULT
            builder(this)
        }
    }

    @SuppressLint("MissingPermission")
    fun modifyNotification(
        builder: NotificationCompat.Builder,
        notificationId: Int,
        modifierBlock: NotificationCompat.Builder.() -> Unit
    ) {
        modifierBlock(builder)
        NotificationManagerCompat
            .from(context)
            .notify(notificationId, builder.build())
    }
}
