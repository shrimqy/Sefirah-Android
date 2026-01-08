package sefirah.communication.sms

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.common.util.smsPermissionGranted
import sefirah.communication.sms.SMSHelper.MessageLooper.Companion.getLooper
import sefirah.communication.sms.SMSHelper.getConversations
import sefirah.communication.sms.SMSHelper.getMessagesInRange
import sefirah.communication.sms.SMSHelper.getNewestMessageTimestamp
import sefirah.communication.sms.SMSHelper.mConversationUri
import sefirah.communication.sms.SmsMmsUtils.sendMessage
import sefirah.communication.sms.SmsMmsUtils.toHelperSmsAddress
import sefirah.communication.sms.SmsMmsUtils.toHelperSmsAttachment
import sefirah.communication.sms.SmsMmsUtils.toTextMessage
import sefirah.domain.model.ConversationType
import sefirah.domain.model.TextConversation
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.NetworkManager
import sefirah.domain.repository.PreferencesRepository
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val deviceManager: DeviceManager,
    private val preferencesRepository: PreferencesRepository
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var messageObserver: ContentObserver = MessageContentObserver(Handler(getLooper()!!))

    // Cache of existing thread IDs
    private var existingThreadIds: Set<Long> = emptySet()

    private val deviceIds = MutableStateFlow<Set<String>>(emptySet())
    private var isObserverRegistered = false

    init {
        scope.launch {
            deviceManager.pairedDevices.collect { pairedDevices ->
                val connectedDeviceIds = pairedDevices
                    .filter {
                        it.connectionState.isConnected &&
                                preferencesRepository.readMessageSyncSettingsForDevice(it.deviceId).first()
                    }
                    .map { it.deviceId }
                    .toSet()

                deviceIds.value = connectedDeviceIds
                
                // Start content observer when deviceIds becomes non-empty
                if (connectedDeviceIds.isNotEmpty() && !isObserverRegistered) {
                    startContentObserver()
                } else if (connectedDeviceIds.isEmpty() && isObserverRegistered) {
                    stopContentObserver()
                }
            }
        }
    }

    private fun startContentObserver() {
        if(!smsPermissionGranted(context)) return
        Log.d(TAG, "Message content observer started")
        context.contentResolver.registerContentObserver(mConversationUri, true, messageObserver)
        isObserverRegistered = true

        mostRecentTimestampLock.lock()
        mostRecentTimestamp = getNewestMessageTimestamp(context)
        mostRecentTimestampLock.unlock()
        existingThreadIds = emptySet()
    }

    private fun stopContentObserver() {
        context.contentResolver.unregisterContentObserver(messageObserver)
        isObserverRegistered = false
        // Reset state
        mostRecentTimestampLock.lock()
        mostRecentTimestamp = 0
        mostRecentTimestampLock.unlock()
        existingThreadIds = emptySet()
        haveMessagesBeenRequested = false
    }

    fun stop() {
        stopContentObserver()
    }


    /**
     * Keep track of the most-recently-seen message so that we can query for later ones as they arrive
     */
    private var mostRecentTimestamp: Long = 0

    // Since the mostRecentTimestamp is accessed both from the plugin's thread and the ContentObserver
    // thread, make sure that access is coherent
    private val mostRecentTimestampLock: Lock = ReentrantLock()

    /**
     * Keep track of whether we have received any packet which requested messages.
     *
     * If not, we will not send updates, since probably the user doesn't care.
     */
    private var haveMessagesBeenRequested: Boolean = false

    private inner class MessageContentObserver
    /**
     * Create a ContentObserver to watch the Messages database. onChange is called for
     * every subscribed change
     *
     * @param handler Handler object used to make the callback
     */
        (handler: Handler?) : ContentObserver(handler) {
        /**
         * The onChange method is called whenever the subscribed-to database changes
         *
         * In this case, this onChange expects to be called whenever *anything* in the Messages
         * database changes and simply reports those updated messages to anyone who might be listening
         */
        override fun onChange(selfChange: Boolean) {
            sendLatestMessage()
            checkForDeletedThreads()
        }
    }

    private fun sendLatestMessage() {
        // Lock so no one uses the mostRecentTimestamp between the moment we read it and the
        // moment we update it. This is because reading the Messages DB can take long.
        mostRecentTimestampLock.lock()

        val messages: List<SMSHelper.Message> = getMessagesInRange(context, null, mostRecentTimestamp, null, false)
        var newMostRecentTimestamp: Long = mostRecentTimestamp
        for (message: SMSHelper.Message? in messages) {
            if (message == null || message.date >= newMostRecentTimestamp) {
                newMostRecentTimestamp = message!!.date
            }
        }

        // Update the most recent counter
        mostRecentTimestamp = newMostRecentTimestamp
        mostRecentTimestampLock.unlock()

        if (messages.isEmpty()) {
            return
        }

        // Group messages by thread ID since they can belong to different threads
        val messagesByThread = messages.groupBy { it.threadID.threadID }
        
        // Send a separate conversation for each thread
        messagesByThread.forEach { (threadId, messagesInThread) ->
            val textMessages = messagesInThread.map {
                it.toTextMessage()
            }

            val isNew = !existingThreadIds.contains(threadId)

            val conversation = TextConversation(
                conversationType = if (isNew) ConversationType.New else ConversationType.ActiveUpdated,
                threadId = threadId,
                messages = textMessages
            )
            existingThreadIds = existingThreadIds.plus(threadId)
            sendToDesktop(conversation, deviceIds.value)
        }
    }

    /**
     * Send all conversations (one message per thread)
     */
    fun sendAllConversations(deviceId: String?) {
        val targetDeviceIds = deviceId?.let { setOf(it) } ?: deviceIds.value
        if (targetDeviceIds.isEmpty()) return
        
        val conversations = getConversations(this.context)
        // Build a set of thread IDs while we process the conversations
        val currentThreadIds = mutableSetOf<Long>()
        
        // For each conversation (already one message per thread from getConversations)
        conversations.forEach { conversationInfo ->
            val threadId = conversationInfo.message.threadID.threadID
            currentThreadIds.add(threadId)
            
            val textMessage = conversationInfo.message.toTextMessage()

            // Create a TextConversation with just this one message
            val conversation = TextConversation(
                conversationType = ConversationType.Active,
                threadId = threadId,
                recipients = conversationInfo.recipients,
                messages = listOf(textMessage)
            )
            sendToDesktop(conversation, targetDeviceIds)
        }
        
        // Store the current thread IDs for later comparison
        existingThreadIds = currentThreadIds
    }

    /**
     * Handle a request for all messages in a specific thread
     */
    fun handleThreadRequest(request: ThreadRequest) {
        haveMessagesBeenRequested = true

        val threadID = SMSHelper.ThreadID(request.threadId)

        // Get all messages in the thread
        val messages = if (request.rangeStartTimestamp < 0) {
            SMSHelper.getMessagesInThread(context, threadID,
                if (request.numberToRequest < 0) null else request.numberToRequest)
        } else {
            getMessagesInRange(context, threadID, request.rangeStartTimestamp,
                if (request.numberToRequest < 0) null else request.numberToRequest, true)
        }

        // Convert all messages to TextMessage objects
        val textMessages = messages.map { it.toTextMessage() }

        // Create a TextConversation containing all messages from this thread
        val conversation = TextConversation(
            conversationType = ConversationType.Active,
            threadId = request.threadId,
            messages = textMessages
        )
        
        // Send the conversation packet
        sendToDesktop(conversation, deviceIds.value)
    }

    private fun sendToDesktop(conversation: TextConversation, targetDeviceIds: Set<String> = deviceIds.value) {
        targetDeviceIds.forEach { deviceId ->
            networkManager.sendMessage(deviceId, conversation)
        }
    }

    fun sendTextMessage(message: TextMessage) {
        val addresses = message.addresses.toHelperSmsAddress(context)
        val attachments = message.attachments?.toHelperSmsAttachment()
        sendMessage(
            context,
            message.body,
            attachments,
            addresses,
            subID = message.subscriptionId,
        )
    }

    /**
     * Check if any threads were deleted
     */
    private fun checkForDeletedThreads() {
        // Get current threads
        val currentThreadIds = mutableSetOf<Long>()
        getConversations(context).forEach { conversationInfo ->
            currentThreadIds.add(conversationInfo.message.threadID.threadID)
        }

        // Find deleted threads
        val deletedThreadIds = existingThreadIds.minus(currentThreadIds)
        
        // Notify about deleted threads
        if (deletedThreadIds.isNotEmpty()) {
            for (threadId in deletedThreadIds) {
                Log.d(TAG, "Thread deleted: $threadId")
                notifyThreadDeleted(threadId)
            }
        }
        
        // Update our cache for next time
        existingThreadIds = currentThreadIds
    }
    
    /**
     * Notify that a thread was deleted
     */
    private fun notifyThreadDeleted(threadId: Long) {
        val deleteNotification = TextConversation(
            conversationType = ConversationType.Removed,
            threadId = threadId,
        )
        sendToDesktop(deleteNotification, deviceIds.value)
    }

    companion object {
        const val TAG = "SmsHandler"
    }
}
