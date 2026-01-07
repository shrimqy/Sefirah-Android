package sefirah.notification

import sefirah.domain.model.NotificationAction
import sefirah.domain.model.ReplyAction

interface NotificationHandler {
    fun sendActiveNotifications(deviceId: String? = null)
    fun removeAllNotification()
    fun removeNotification(notificationId: String?)
    fun openNotification(notificationKey: String?)
    fun performNotificationAction(action: NotificationAction)
    fun performReplyAction(action: ReplyAction)
}