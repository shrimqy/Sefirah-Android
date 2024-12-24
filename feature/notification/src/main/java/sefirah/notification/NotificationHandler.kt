package sefirah.notification

import sefirah.domain.model.NotificationAction
import sefirah.domain.model.ReplyAction

interface NotificationHandler {
    fun sendActiveNotifications()
    fun removeAllNotification()
    fun removeNotification(notificationId: String?)
    fun performNotificationAction(action: NotificationAction)
    fun performReplyAction(action: ReplyAction)
}