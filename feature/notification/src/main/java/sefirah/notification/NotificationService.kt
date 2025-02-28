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
import androidx.core.os.bundleOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.Message
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.NotificationType
import sefirah.domain.model.ReplyAction
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
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
    private val networkManager: NetworkManager,
    private val preferencesRepository: PreferencesRepository
) : NotificationHandler, NotificationCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isConnected : Boolean = false

    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    
    private lateinit var listener: NotificationListenerService

    private var activeNotificationsSend : Boolean = false

    private var notificationSyncSettings = MutableStateFlow(true)
    init {
        scope.launch {
            networkManager.connectionState.collect { state ->
                connectionState.value = state
                if (state == ConnectionState.Disconnected()) activeNotificationsSend = false
            }
        }
        scope.launch {
            preferencesRepository.readNotificationSyncSettings().collectLatest {
                notificationSyncSettings.value = it
            }
        }
    }

    override fun sendActiveNotifications() {
        if (!isConnected && !notificationSyncSettings.value) {
            return
        } else {
            scope.launch {
                if (!::listener.isInitialized) {
                    Log.w(TAG, "listener not connected")
                    return@launch
                }
                
                activeNotificationsSend = true
                val activeNotifications = listener.activeNotifications
                if (activeNotifications.isNullOrEmpty()) {
                    Log.d(TAG, "No active notifications found.")
                } else {
                    Log.d(TAG, "Active notifications found: ${activeNotifications.size}")
                    activeNotifications.forEach { sbn ->
                        sendNotification(sbn, NotificationType.Active)
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
        sendNotification(notification, NotificationType.New)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        // to remove the notification on the desktop
        val removeNotificationMessage = NotificationMessage(
            appPackage = notification.packageName,
            notificationKey = notification.key,
            notificationType = NotificationType.Removed,
            tag = notification.tag,
        )
        scope.launch {
            networkManager.sendMessage(removeNotificationMessage)
        }
    }

    override fun onListenerConnected(service: NotificationListenerService) {
        isConnected = true
        listener = service
        if ((connectionState.value == ConnectionState.Connected) && !activeNotificationsSend) {
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
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Error performing click action", e)
        }
    }


    override fun performReplyAction(action: ReplyAction) {
        // Get notification and its first reply action (usually messaging apps only have one)
        val notification = listener.activeNotifications
            .find { it.key == action.notificationKey }
            ?.notification ?: return

        // Most messaging apps put the reply action as the first action with RemoteInput
        val replyAction = notification.actions?.firstOrNull { 
            it.remoteInputs?.isNotEmpty() == true 
        } ?: return

        try {
            Intent().apply {
                RemoteInput.addResultsToIntent(
                    replyAction.remoteInputs,
                    this,
                    bundleOf(action.replyResultKey to action.replyText)
                )
            }.also { replyAction.actionIntent.send(listener, 0, it) }

        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Reply failed: ${action.notificationKey}", e)
        }
    }

    override fun stopListener() {
        NotificationListener.stop(context)
    }


    private fun sendNotification(sbn: StatusBarNotification, notificationType: NotificationType) {
        if (!notificationSyncSettings.value) return
        val notification = sbn.notification
        val packageName = sbn.packageName

        // Get the app name using PackageManager
        val packageManager = listener.packageManager


        val appName = try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Couldn't resolve name $packageName", e)
            null
        }

        val extras = notification.extras
        // Check for progress-related extras
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val maxProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
        val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val hasProgress = (progress >= 1 || maxProgress >= 1) || isIndeterminate
        // Check if the notification is ongoing, media-style, or belongs to the 'progress' category
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
            || (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
            || notification.isMediaStyle()
            || hasProgress) {
            return
        }

        if ("com.facebook.orca" == packageName &&
            (sbn.id == 10012) &&
            "Messenger" == appName && notification.tickerText == null
        ) {
            //HACK: Hide weird Facebook empty "Messenger" notification that is actually not shown in the phone
            return
        }

        if ("com.android.systemui" == packageName &&
            "low_battery" == sbn.tag
        ) {
            //HACK: Android low battery notification are posted again every few seconds. Ignore them, as we already have a battery indicator.
            return
        }

        if ("com.castle.sefirah" == packageName) {
            // Don't send our own notifications
            return
        }

        scope.launch {
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
                    Log.e(TAG, "Error retrieving action: ${e.localizedMessage}")
                    null
                }
            } ?: emptyList()

            Log.d(TAG, "$appName $title $text messages: $messages actions: $actions")

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
                Log.d(TAG, "Duplicate notification, ignoring...")
                return@launch
            }

            try {
//                Log.d("NotificationService", "${notificationMessage.appName} ${notificationMessage.title} ${notificationMessage.text} ${notificationMessage.tag} ${notificationMessage.groupKey}")
                networkManager.sendMessage(notificationMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send notification message", e)
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
    companion object {
        const val TAG = "NotificationService"
    }
}