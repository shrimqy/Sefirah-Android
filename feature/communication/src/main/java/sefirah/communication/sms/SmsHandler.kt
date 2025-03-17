package sefirah.communication.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sefirah.communication.sms.SMSHelper.MessageLooper.Companion.getLooper
import sefirah.communication.sms.SMSHelper.getConversations
import sefirah.communication.sms.SMSHelper.getMessagesInRange
import sefirah.communication.sms.SMSHelper.getNewestMessageTimestamp
import sefirah.communication.sms.SMSHelper.registerObserver
import sefirah.communication.sms.SmsMmsUtils.sendMessage
import sefirah.communication.sms.SmsMmsUtils.toHelperSmsAddress
import sefirah.communication.sms.SmsMmsUtils.toHelperSmsAttachment
import sefirah.communication.sms.SmsMmsUtils.toTextMessage
import sefirah.domain.model.ConversationType
import sefirah.domain.model.TextConversation
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.domain.repository.NetworkManager
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
) {
    private var smsReceiver: BroadcastReceiver? = null

    // Cache of existing thread IDs
    private var existingThreadIds: Set<Long> = emptySet()

    fun start() {
        val helperLooper: Looper? = getLooper()
        val messageObserver: ContentObserver = MessageContentObserver(Handler(helperLooper!!))
        registerObserver(messageObserver, context)

        mostRecentTimestampLock.lock()
        mostRecentTimestamp = getNewestMessageTimestamp(context)
        mostRecentTimestampLock.unlock()
        existingThreadIds = emptySet()

        CoroutineScope(Dispatchers.IO).launch {
            sendAllConversations()
        }
    }
    
    fun registerSmsReceiver() {
        smsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    messages.forEach { smsMessage ->
                        val sender = smsMessage.originatingAddress ?: ""
                        val messageBody = smsMessage.messageBody ?: ""
                    }
                }
            }
        }
        
        // Register the receiver
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        filter.priority = 500
        context.registerReceiver(smsReceiver, filter)
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

        if (!haveMessagesBeenRequested) {
            // Since the user has not requested a message, there is most likely nobody listening
            // for message updates, so just drop them rather than spending battery/time sending
            // updates that don't matter.
            mostRecentTimestampLock.unlock()
            return
        }
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
        val threadId = messages[0].threadID.threadID

        val textMessages = messages.map {
            val text = it.toTextMessage()
            text
        }

        val isNew = !existingThreadIds.contains(threadId)
        Log.d(TAG, "isNew: $isNew, threadId: $threadId")
        Log.d(TAG, "existingThreadIds: $existingThreadIds")

        val conversation = TextConversation(
            conversationType = if (isNew) ConversationType.New else ConversationType.ActiveUpdated,
            threadId = threadId,
            messages = textMessages
        )
        existingThreadIds = existingThreadIds.plus(threadId)
        
        // Send the conversation packet
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "conversation: $conversation")
            networkManager.sendMessage(conversation)
        }
    }

    /**
     * Send all conversations (one message per thread)
     */
    private fun sendAllConversations() {
        haveMessagesBeenRequested = true
        val conversations = getConversations(this.context)
        
        // Build a set of thread IDs while we process the conversations
        val currentThreadIds = mutableSetOf<Long>()
        
        // For each conversation (already one message per thread from getConversations)
        conversations.forEach { message ->
            val threadId = message.threadID.threadID
            currentThreadIds.add(threadId)
            
            val textMessage = message.toTextMessage()

            // Create a TextConversation with just this one message
            val conversation = TextConversation(
                conversationType = ConversationType.Active,
                threadId = threadId,
                messages = listOf(textMessage)
            )
            Log.d(TAG, "conversation: $conversation")
            // Send as a conversation container
            CoroutineScope(Dispatchers.IO).launch {
                networkManager.sendMessage(conversation)
            }
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
        CoroutineScope(Dispatchers.IO).launch {
            networkManager.sendMessage(conversation)
        }
    }

    fun unregisterSmsReceiver() {
        smsReceiver?.let {
            context.unregisterReceiver(it)
            smsReceiver = null
        }
    }
    
    fun sendTextMessage(message: TextMessage) {
        Log.d(TAG, "Sending message: ${message.body}")
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
        getConversations(context).forEach { message ->
            currentThreadIds.add(message.threadID.threadID)
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

        CoroutineScope(Dispatchers.IO).launch {
            networkManager.sendMessage(deleteNotification)
        }
    }

    companion object {
        const val TAG = "SmsHandler"
    }
}
