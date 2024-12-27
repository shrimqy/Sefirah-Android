package sefirah.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.SpannableString
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.Message
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.NotificationType
import sefirah.domain.model.ReplyAction
import sefirah.domain.repository.NetworkManager
import sefirah.presentation.util.bitmapToBase64
import sefirah.presentation.util.drawableToBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale.getDefault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager
) : NotificationHandler, NotificationCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isConnected : Boolean = false

    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    
    private lateinit var listener: NotificationListenerService

    init {
        scope.launch {
            networkManager.connectionState.collect { state ->
                connectionState.value = state
            }
        }
    }

    override fun sendActiveNotifications() {
        if (!isConnected) {
            NotificationListener.start(context)
        } else {
            scope.launch {
                val activeNotifications = listener.activeNotifications
                if (activeNotifications.isNullOrEmpty()) {
                    Log.d("activeNotification", "No active notifications found.")
                } else {
                    Log.d("activeNotification", "Active notifications found: ${activeNotifications.size}")
                    activeNotifications.forEach { sbn ->
                        sendNotification(sbn, NotificationType.ACTIVE)
                        delay(50)
                    }
                }

            }
        }
    }

    override fun removeAllNotification() {
        listener.cancelAllNotifications()
    }

    override fun removeNotification(notificationId: String?) {
        listener.cancelNotification(notificationId)
    }


    override fun onNotificationPosted(notification: StatusBarNotification) {
        sendNotification(notification, NotificationType.NEW)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {

    }

    override fun onListenerConnected(service: NotificationListenerService) {
        isConnected = true
        listener = service
        if (connectionState.value == ConnectionState.Connected) {
            sendActiveNotifications()
        }
    }

    override fun onListenerDisconnected() {
        isConnected = false

    }

    override fun performNotificationAction(action: NotificationAction) {
        val activeNotification = listener.activeNotifications.find {
            it.key == action.notificationKey
        } ?: return

        val actions = activeNotification.notification.actions ?: return
        val clickAction = actions.getOrNull(action.actionIndex) ?: return

        try {
            clickAction.actionIntent.send()
            Log.d("NotificationService", "Click action performed successfully")
        } catch (e: PendingIntent.CanceledException) {
            Log.e("NotificationService", "Error performing click action", e)
        }
    }


    override fun performReplyAction(action: ReplyAction) {
        val activeNotification = listener.activeNotifications.find {
            it.key == action.notificationKey
        } ?: return

        val actions = activeNotification.notification.actions ?: return
        val replyAction = actions.find { it.remoteInputs?.isNotEmpty() == true } ?: return

        val resultIntent = Intent()
        val remoteInputs = replyAction.remoteInputs

        val bundle = Bundle()
        remoteInputs?.forEach { remoteInput ->
            bundle.putString(remoteInput.resultKey, action.replyText)
        }
        RemoteInput.addResultsToIntent(replyAction.remoteInputs, resultIntent, bundle)

        try {
            replyAction.actionIntent.send(listener  , 0, resultIntent)
            Log.d("NotificationService", "Reply action performed successfully")
        } catch (e: PendingIntent.CanceledException) {
            Log.e("NotificationService", "Error performing reply action", e)
        }
    }

    override fun stopListener() {
        NotificationListener.stop(context)
    }


    private fun sendNotification(sbn: StatusBarNotification, notificationType: NotificationType) {
        val notification = sbn.notification
        val extras = notification.extras
        // Check for progress-related extras
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val maxProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
        val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val hasProgress = (progress >= 1 || maxProgress >= 1) || isIndeterminate
        // Check if the notification is ongoing, media-style, or belongs to the 'progress' category
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT != 0 && notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            || notification.isMediaStyle()
            || hasProgress) {
            return
        }

        scope.launch {
            val packageName = sbn.packageName

            // Get the app name using PackageManager
            val packageManager = listener.packageManager

            val appName = try {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown App"
            }

            // Get app icon
            val appIcon = try {
                val appIconDrawable = packageManager.getApplicationIcon(packageName)
                if (appIconDrawable is BitmapDrawable) {
                    val appIconBitmap = appIconDrawable.bitmap
                    bitmapToBase64(appIconBitmap)
                } else {
                    // Convert to Bitmap if it's not already a BitmapDrawable
                    val appIconBitmap = drawableToBitmap(appIconDrawable)
                    bitmapToBase64(appIconBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            val notificationKey = sbn.key

            // Get the notification large icon
            val largeIcon = notification.getLargeIcon()?.let { icon ->
                val largeIconBitmap = icon.loadDrawable(context)?.let { (it as BitmapDrawable).bitmap }
                largeIconBitmap?.let { bitmapToBase64(it) }
            }

            // Get picture (if available)
            val picture = notification.extras.get(Notification.EXTRA_PICTURE)?.let { pictureBitmap ->
                bitmapToBase64(pictureBitmap as Bitmap)
            }

            // Use the utility function to get text from SpannableString
            val title = getSpannableText(notification.extras.getCharSequence(Notification.EXTRA_TITLE))
                ?: getSpannableText(notification.extras.getCharSequence(Notification.EXTRA_TITLE_BIG))

            val text = getSpannableText(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
                ?: getSpannableText(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
                ?: getSpannableText(notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))

            val messages = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull {
                val bundle = it as? Bundle
                val sender = bundle?.getCharSequence("sender")?.toString() // Get the sender's name
                val messageText = bundle?.getCharSequence("text")?.toString()

                if (sender != null && messageText != null) {
                    Message(sender = sender, text = messageText)
                } else {
                    null
                }
            } ?: emptyList()

            // Get the timestamp of the notification
            val timestamp = notification.`when`

            // Convert timestamp to a human-readable format if needed
            val formattedTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", getDefault()).format(
                Date(timestamp)
            )

            val actions = notification.actions?.mapIndexedNotNull { index, action ->
                try {
                    val actionLabel = action.title.toString()

                    // Store replyResultKey for the first remote input, if available
                    val isReplyAction = action.remoteInputs?.firstOrNull()?.resultKey

                    NotificationAction(
                        notificationKey = sbn.key,
                        label = actionLabel,
                        actionIndex = index,
                        isReplyAction = !isReplyAction.isNullOrEmpty()
                    )
                } catch (e: Exception) {
                    Log.e("NotificationService", "Error retrieving action: ${e.localizedMessage}")
                    null
                }
            } ?: emptyList()

            Log.d("NotificationService", "$appName $title $text messages: $messages actions: $actions")

            val replyResultKey = notification.actions
                ?.firstNotNullOfOrNull { action ->
                    action.remoteInputs?.firstOrNull()?.resultKey
                }

            val notificationMessage = NotificationMessage(
                notificationKey = notificationKey,
                appPackage = sbn.packageName,
                appName = appName,
                title = title,
                text = text,
                messages = messages,
                actions = actions,
                replyResultKey = replyResultKey,
                timestamp = formattedTimestamp,
                appIcon = appIcon,
                largeIcon = largeIcon,
                bigPicture = picture,
                tag = sbn.key,
                groupKey = sbn.groupKey,
                notificationType = notificationType
            )

            if (notificationMessage.appName == "WhatsApp" && notificationMessage.messages?.isEmpty() == true
                || notificationMessage.appName == "Spotify" && notificationMessage.timestamp == "1970-01-01 05:30:00") {
                Log.d("NotificationService", "Duplicate notification, ignoring...")
                return@launch
            }

            try {
//                Log.d("NotificationService", "${notificationMessage.appName} ${notificationMessage.title} ${notificationMessage.text} ${notificationMessage.tag} ${notificationMessage.groupKey}")
                networkManager.sendMessage(notificationMessage)
            } catch (e: Exception) {
                Log.e("NotificationService", "Failed to send notification message", e)
            }
        }
    }

    private fun getSpannableText(charSequence: CharSequence?): String? {
        return when (charSequence) {
            is SpannableString -> charSequence.toString()
            else -> charSequence?.toString()
        }
    }
    private fun Notification.isMediaStyle(): Boolean {
        val mediaStyleClassName = "android.app.Notification\$MediaStyle"
        return mediaStyleClassName == this.extras.getString(Notification.EXTRA_TEMPLATE)
    }

}