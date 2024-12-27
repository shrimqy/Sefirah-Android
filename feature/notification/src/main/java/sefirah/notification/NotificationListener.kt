package sefirah.notification

import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject
    lateinit var notificationCallback: NotificationCallback

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            notificationCallback.onNotificationPosted(notification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            notificationCallback.onNotificationRemoved(notification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationService", "Listener connected")
        notificationCallback.onListenerConnected(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        notificationCallback.onListenerDisconnected()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, NotificationListener::class.java)
            context.startService(intent)
        }
        fun stop(context: Context) {
            Log.d("NotificationService", "Stop called")
            val intent = Intent(context, NotificationListener::class.java)
            context.stopService(intent)
        }
    }
}

interface NotificationCallback {
    fun onNotificationPosted(notification: StatusBarNotification)
    fun onNotificationRemoved(notification: StatusBarNotification)
    fun onListenerConnected(service: NotificationListenerService)
    fun onListenerDisconnected()
}