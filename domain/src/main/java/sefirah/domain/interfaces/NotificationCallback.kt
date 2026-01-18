package sefirah.domain.interfaces

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

interface NotificationCallback {
    fun onNotificationPosted(notification: StatusBarNotification)
    fun onNotificationRemoved(notification: StatusBarNotification)
    fun onListenerConnected(service: NotificationListenerService)
    fun onListenerDisconnected()
}