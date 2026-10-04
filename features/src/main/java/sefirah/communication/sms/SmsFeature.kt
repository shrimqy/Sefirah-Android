package sefirah.communication.sms

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.BoundFeature
import sefirah.common.util.smsPermissionGranted
import sefirah.communication.sms.SMSHelper.MessageLooper.Companion.getLooper
import sefirah.communication.sms.SMSHelper.getConversations
import sefirah.communication.sms.SMSHelper.getMessageIds
import sefirah.communication.sms.SMSHelper.getMessageRefsNewerThan
import sefirah.communication.sms.SMSHelper.getMessagesByIds
import sefirah.communication.sms.SMSHelper.getMessagesByRefs
import sefirah.communication.sms.SMSHelper.getMessagesInRange
import sefirah.communication.sms.SMSHelper.getNewestMessageRef
import sefirah.communication.sms.SMSHelper.getNewestMessageTimestamp
import sefirah.communication.sms.SMSHelper.ThreadState
import sefirah.communication.sms.SMSHelper.getThreadChecksums
import sefirah.communication.sms.SMSHelper.getThreadStates
import sefirah.communication.sms.SMSHelper.getThreadRecipients
import sefirah.communication.sms.SMSHelper.mConversationUri
import sefirah.communication.sms.SmsMmsUtils.sendMessage
import sefirah.communication.sms.SmsMmsUtils.toTextMessage
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.ConversationInfo
import sefirah.domain.model.ConversationsRequest
import sefirah.domain.model.DevicePreferences
import sefirah.domain.model.FileMetadata
import sefirah.domain.model.MessageIndex
import sefirah.domain.model.MessageList
import sefirah.domain.model.MessageListKind
import sefirah.domain.model.MessagesRequest
import sefirah.domain.model.RemoveConversation
import sefirah.domain.model.SmsAttachmentRequest
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextMessage
import sefirah.transfer.FileTransferService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.map

@Singleton
class SmsFeature @Inject constructor(
    private val context: Context,
    deviceManager: DeviceManager,
    private val networkManager: NetworkManager,
    private val fileTransferService: FileTransferService,
    private val preferencesRepository: PreferencesRepository,
) : BoundFeature(deviceManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private val syncJobs = ConcurrentHashMap<String, Job>()
    private val threadJobs = ConcurrentHashMap<Pair<String, Long>, Job>()
    private val pendingSyncRequests = ConcurrentHashMap<String, ConversationsRequest>()
    private val messageObserver: ContentObserver = MessageContentObserver(Handler(getLooper()!!))

    /** Thread checksum + message count cache for change and delete detection. */
    private var cachedThreadStates: Map<Long, ThreadState> = emptyMap()

    /** New-SMS watermark — messages with date > these are treated as additions. */
    private var mostRecentTimestamp: Long = Long.MIN_VALUE

    override fun isPrefEnabled(prefs: DevicePreferences) = prefs.messageSync

    override fun hasPermissions(): Boolean = smsPermissionGranted(context)

    override suspend fun onStart() {
        startContentObserver()
    }

    override suspend fun onStop() {
        stopContentObserver()
    }

    override suspend fun onStart(deviceId: String) {
        pendingSyncRequests.remove(deviceId)?.let { startSync(deviceId, it) }
    }

    override suspend fun onStop(deviceId: String) {
        pendingSyncRequests.remove(deviceId)
        syncJobs.remove(deviceId)?.cancel()
        threadJobs.keys.filter { it.first == deviceId }.forEach { threadJobs.remove(it)?.cancel() }
    }

    fun stop() {
        syncJobs.values.forEach { it.cancel() }
        syncJobs.clear()
        threadJobs.values.forEach { it.cancel() }
        threadJobs.clear()
        stopContentObserver()
    }

    private var isObserverRegistered = false

    private fun startContentObserver() {
        if (!smsPermissionGranted(context)) return
        Log.d(TAG, "Message content observer started")
        context.contentResolver.registerContentObserver(mConversationUri, true, messageObserver)
        isObserverRegistered = true

        cachedThreadStates = getThreadStates(context)
        mostRecentTimestamp = getNewestMessageTimestamp(context)
    }

    private fun stopContentObserver() {
        if (!isObserverRegistered) return
        context.contentResolver.unregisterContentObserver(messageObserver)
        isObserverRegistered = false
        cachedThreadStates = emptyMap()
        mostRecentTimestamp = Long.MIN_VALUE
    }

    private inner class MessageContentObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            syncThreadChanges()
        }
    }

    private fun syncThreadChanges() {
        scope.launch {
            lifecycleMutex.withLock {
                if (enabledDevices.isEmpty()) return@withLock
                try {
                    // Wait for in-flight desktop sends so tips can stamp tempId.
                    SmsMmsUtils.awaitPendingTempIdMaps()

                    // Light index first; load full bodies only for new rows we will broadcast.
                    val newRefs = getMessageRefsNewerThan(context, mostRecentTimestamp)
                    val newRefsByThread = newRefs.groupBy { it.threadId }

                    val current = getThreadStates(context)
                    val previous = cachedThreadStates

                    for (threadId in previous.keys - current.keys) {
                        broadcast(RemoveConversation(threadId))
                    }

                    val changed = current.filter { (id, state) -> previous[id]?.checksum != state.checksum }.keys + newRefsByThread.keys
                    for (threadId in changed) {
                        currentCoroutineContext().ensureActive()
                        val state = current[threadId] ?: continue
                        val checksum = state.checksum
                        val added = newRefsByThread[threadId]
                        when {
                            // Brand-new thread — establish list row with recipients + tip.
                            threadId !in previous -> {
                                val tipRef = added?.maxByOrNull { it.date }
                                    ?: getNewestMessageRef(context, threadId)
                                    ?: continue
                                val tip = getMessagesByRefs(context, listOf(tipRef)).firstOrNull() ?: continue
                                broadcast(
                                    ConversationInfo(
                                        threadId = threadId,
                                        recipients = getThreadRecipients(context, threadId),
                                        checksum = checksum,
                                        message = tip.toTextMessage(SmsMmsUtils.takeTempId(tip.uID)),
                                    ),
                                )
                            }
                            else -> {
                                // New SMS, or the tip after a read / edit / delete.
                                val messages = if (added != null) {
                                    getMessagesByRefs(context, added)
                                } else {
                                    getNewestMessageRef(context, threadId)
                                        ?.let { getMessagesByRefs(context, listOf(it)) }
                                        .orEmpty()
                                }
                                if (messages.isNotEmpty()) {
                                    broadcast(
                                        MessageList(
                                            threadId = threadId,
                                            kind = MessageListKind.New,
                                            messages = messages.map {
                                                it.toTextMessage(SmsMmsUtils.takeTempId(it.uID))
                                            },
                                            checksum = checksum,
                                        ),
                                    )
                                }

                                // Membership only changes on delete; reads / edits keep the count.
                                val expectedCount = (previous[threadId]?.messageCount ?: 0L) + (added?.size ?: 0)
                                if (messages.isEmpty() || state.messageCount < expectedCount) {
                                    broadcast(MessageIndex(threadId, getMessageIds(context, threadId), checksum))
                                }
                            }
                        }
                    }

                    newRefs.maxOfOrNull { it.date }?.let { mostRecentTimestamp = it }
                    cachedThreadStates = current
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Could not refresh conversations", e)
                }
            }
        }
    }

    private fun broadcast(message: SocketMessage) {
        for (deviceId in enabledDevices)
            networkManager.sendMessage(deviceId, message)
    }

    suspend fun handleConversationsRequest(deviceId: String, request: ConversationsRequest) = lifecycleMutex.withLock {
        if (deviceId !in enabledDevices) {
            pendingSyncRequests[deviceId] = request
            return@withLock
        }
        startSync(deviceId, request)
    }

    private suspend fun startSync(deviceId: String, request: ConversationsRequest) {
        syncJobs[deviceId]?.cancelAndJoin()
        syncJobs[deviceId] = scope.launch {
            try {
                lifecycleMutex.withLock {
                    if (deviceId in enabledDevices) sendConversations(deviceId, request)
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Could not synchronize conversations", e)
            }
        }
    }

    private suspend fun sendConversations(deviceId: String, request: ConversationsRequest) {
        val currentChecksums = getThreadChecksums(context)
        val knownChecksums = request.knownThreads.associate { it.threadId to it.checksum }

        for (threadId in knownChecksums.keys) {
            if (threadId in currentChecksums) continue
            if (!networkManager.sendMessage(deviceId, RemoveConversation(threadId))) {
                Log.d(TAG, "Stopping conversation sync; connection gone")
                return
            }
        }

        val changedThreadIds = currentChecksums
            .filter { (threadId, checksum) -> knownChecksums[threadId] != checksum }
            .keys
        if (changedThreadIds.isEmpty()) return

        for ((message, recipients, checksum) in getConversations(context, changedThreadIds, currentChecksums)) {
            currentCoroutineContext().ensureActive()
            val info = ConversationInfo(
                threadId = message.threadID.threadID,
                recipients = recipients,
                checksum = checksum,
                message = message.toTextMessage(),
            )
            if (!networkManager.sendMessage(deviceId, info)) {
                Log.d(TAG, "Stopping conversation sync; connection gone")
                return
            }
        }
    }

    fun handleAttachmentRequest(deviceId: String, request: SmsAttachmentRequest) {
        val partInfo = SmsMmsUtils.getMmsPartInfo(context, request.partId)
        if (partInfo == null) {
            Log.e(TAG, "Failed to read MMS part info. partId=${request.partId}")
            return
        }
        val (mimeType, fileName) = partInfo
        val partUri = ContentUris.withAppendedId(SMSHelper.mMSPartUri, request.partId)
        val pfd = try {
            context.contentResolver.openFileDescriptor(partUri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open MMS part. partId=${request.partId}", e)
            null
        }
        if (pfd == null) {
            Log.e(TAG, "Loaded attachment is empty. partId=${request.partId}")
            return
        }

        val size = pfd.statSize
        fileTransferService.sendSmsAttachment(
            deviceId = deviceId,
            pfd = pfd,
            metadata = FileMetadata(
                fileName = fileName,
                mimeType = mimeType,
                fileSize = if (size > 0) size else 0L,
            ),
            partId = request.partId,
        )
    }

    fun handleMessagesRequest(deviceId: String, request: MessagesRequest) {
        if (deviceId !in enabledDevices) return

        val ids = request.messageIds
        if (ids != null) {
            scope.launch {
                try {
                    lifecycleMutex.withLock {
                        if (deviceId !in enabledDevices) return@withLock
                        sendHistory(deviceId, request.threadId, ids)
                    }
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.e(TAG, "Could not fetch messages for thread ${request.threadId}", e)
                }
            }
            return
        }

        // A re-open supersedes an in-flight open for the same thread.
        val key = deviceId to request.threadId
        threadJobs[key]?.cancel()
        threadJobs[key] = scope.launch {
            try {
                lifecycleMutex.withLock {
                    if (deviceId !in enabledDevices) return@withLock
                    sendOpen(deviceId, request.threadId)
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.e(TAG, "Could not fetch thread ${request.threadId}", e)
            } finally {
                threadJobs.remove(key, currentCoroutineContext()[Job])
            }
        }
    }

    private fun sendOpen(deviceId: String, threadId: Long) {
        val bodies = getMessagesInRange(
            context,
            SMSHelper.ThreadID(threadId),
            Long.MAX_VALUE,
            PAGE_SIZE,
            getMessagesOlderStartTime = true,
        ).map { it.toTextMessage() }

        networkManager.sendMessage(
            deviceId,
            MessageList(
                threadId = threadId,
                kind = MessageListKind.Open,
                messages = bodies,
                messageIds = getMessageIds(context, threadId),
            ),
        )
    }

    private fun sendHistory(deviceId: String, threadId: Long, ids: List<Long>) {
        networkManager.sendMessage(
            deviceId,
            MessageList(
                threadId = threadId,
                kind = MessageListKind.History,
                messages = getMessagesByIds(context, threadId, ids).map { it.toTextMessage() },
            ),
        )
    }

    fun sendTextMessage(deviceId: String, message: TextMessage) {
        scope.launch {
            sendMutex.withLock {
                val prefs = preferencesRepository.preferenceSettings(deviceId).first()
                sendMessage(
                    context,
                    message.body,
                    message.attachments?.map {
                        SMSHelper.Attachment(
                            encodedFile = it.base64EncodedFile,
                            mimeType = it.mimeType,
                            uniqueIdentifier = it.fileName,
                            partID = -1
                        )
                    },
                    message.addresses.map {
                        SMSHelper.Address(
                            context,
                            address = it
                        )
                    }.toMutableList(),
                    subID = message.subscriptionId,
                    tempId = message.uniqueId,
                    longTextAsMms = prefs.longTextAsMms,
                    groupMessageAsMms = prefs.groupMessageAsMms,
                    convertToMmsAfter = prefs.convertToMmsAfter,
                )
            }
        }
    }

    companion object {
        const val TAG = "SmsFeature"
        private const val PAGE_SIZE = 10L
    }
}
