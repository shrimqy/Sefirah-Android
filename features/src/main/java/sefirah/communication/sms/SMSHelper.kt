/*
 * SPDX-FileCopyrightText: 2021 Simon Redman <simon@ergotech.com>
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package sefirah.communication.sms

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Telephony
import android.provider.Telephony.Threads
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toUri
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduPersister
import com.google.android.mms.util_alt.PduCache
import com.google.android.mms.util_alt.PduCacheEntry
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.math.NumberUtils
import sefirah.common.util.MimeType
import sefirah.communication.utils.TelephonyHelper
import sefirah.communication.utils.TelephonyHelper.LocalPhoneNumber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.text.Charsets.UTF_8

@SuppressLint("InlinedApi")
object SMSHelper {
    // The constant Telephony.Mms.Part.CONTENT_URI was added in API 29
    val mMSPartUri : Uri = "content://mms/part/".toUri()

    val mConversationUri : Uri = "content://mms-sms/conversations?simple=true".toUri()

    // This glorious - but completely undocumented - content URI gives us all messages, both MMS and SMS,
    // in all conversations
    // See https://stackoverflow.com/a/36439630/3723163
    private val completeConversationsUri : Uri =  "content://mms-sms/complete-conversations".toUri()
    /**
     * Column used to discriminate between SMS and MMS messages
     * Unfortunately, this column is not defined for Telephony.MmsSms.CONTENT_CONVERSATIONS_URI
     * (aka. content://mms-sms/conversations)
     * which gives us the first message in every conversation, but it *is* defined for
     * content://mms-sms/conversations/<threadID> which gives us the complete conversation matching
     * that threadID, so at least it's partially useful to us.
     */
    private const val TRANSPORT_TYPE_DISCRIMINATOR_COLUMN = Telephony.MmsSms.TYPE_DISCRIMINATOR_COLUMN

    /**
     * Get the timestamp of the newest known message. Will return Integer.MIN_VALUE if there are no messages.
     *
     * @param context  android.content.Context running the request
     * @return Timestamp of the oldest known message.
     */
    fun getNewestMessageTimestamp(context: Context): Long {
        val sms = newestDate(context, Telephony.Sms.CONTENT_URI)
        val mms = newestDate(context, Telephony.Mms.CONTENT_URI)?.times(1000)
        return maxOf(sms ?: Long.MIN_VALUE, mms ?: Long.MIN_VALUE)
    }

    private fun newestDate(context: Context, uri: Uri): Long? {
        context.contentResolver.query(
            uri, arrayOf(Message.DATE), null, null, "${Message.DATE} DESC",
        ).use { cursor ->
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return null
    }

    /**
     * Get some or all the messages in a requested thread, starting with the most-recent message
     *
     * @param context  android.content.Context running the request
     * @param threadID Thread to look up
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @return List of all messages in the thread
     */
    fun getMessagesInThread(context: Context, threadID: ThreadID, numberToGet: Long?): List<Message> {
        return getMessagesInRange(context, threadID, Long.MAX_VALUE, numberToGet, true)
    }

    /** Every SMS/MMS id in [threadId], newest first. */
    fun getMessageIds(context: Context, threadId: Long): List<Long> {
        val selection = "${Message.THREAD_ID} = ?"
        val args = arrayOf(threadId.toString())
        return (queryRefs(context, isMms = false, selection, args) + queryRefs(context, isMms = true, selection, args))
            .sortedWith(compareByDescending<MessageRef> { it.date }.thenByDescending { it.uniqueId })
            .map { it.uniqueId }
            .distinct()
    }

    /** Full messages in [threadId] with the given ids, oldest first. */
    fun getMessagesByIds(context: Context, threadId: Long, ids: Collection<Long>): List<Message> {
        if (ids.isEmpty()) return emptyList()
        val selection = "${Message.THREAD_ID} = ? AND ${Message.U_ID} IN (${ids.joinToString(",")})"
        val args = arrayOf(threadId.toString())
        val messages = ArrayList<Message>(ids.size)
        messages.addAll(getMessages(Telephony.Sms.CONTENT_URI, context, smsColumns(context), selection, args, null, null))
        messages.addAll(getMessages(Telephony.Mms.CONTENT_URI, context, mmsColumns(context), selection, args, null, null))
        messages.sortBy { it.date }
        return messages
    }

    data class MessageRef(val threadId: Long, val uniqueId: Long, val date: Long, val isMms: Boolean)

    /** New SMS/MMS rows after [afterTimestampMs] — id, thread, date only. */
    fun getMessageRefsNewerThan(context: Context, afterTimestampMs: Long): List<MessageRef> =
        queryRefs(context, isMms = false, "${Message.DATE} > ?", arrayOf(afterTimestampMs.toString())) +
            queryRefs(context, isMms = true, "${Message.DATE} > ?", arrayOf((afterTimestampMs / 1000).toString()))

    /** Newest SMS/MMS row in a thread — id, thread, date only. */
    fun getNewestMessageRef(context: Context, threadId: Long): MessageRef? {
        val selection = "${Message.THREAD_ID} = ?"
        val args = arrayOf(threadId.toString())
        val sms = queryRefs(context, isMms = false, selection, args, newestFirst = true, limit = 1)
        val mms = queryRefs(context, isMms = true, selection, args, newestFirst = true, limit = 1)
        return (sms + mms).maxByOrNull { it.date }
    }

    private fun queryRefs(
        context: Context,
        isMms: Boolean,
        selection: String?,
        selectionArgs: Array<String>?,
        newestFirst: Boolean = false,
        limit: Long? = null,
    ): List<MessageRef> {
        val uri = if (isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
        val refs = ArrayList<MessageRef>()
        context.contentResolver.query(
            uri,
            arrayOf(Message.U_ID, Message.THREAD_ID, Message.DATE),
            selection,
            selectionArgs,
            "${Message.DATE} ${if (newestFirst) "DESC" else "ASC"}",
        ).use { cursor ->
            checkNotNull(cursor) { "Message provider returned no cursor for $uri" }
            val idCol = cursor.getColumnIndexOrThrow(Message.U_ID)
            val threadCol = cursor.getColumnIndexOrThrow(Message.THREAD_ID)
            val dateCol = cursor.getColumnIndexOrThrow(Message.DATE)
            while (cursor.moveToNext()) {
                val date = cursor.getLong(dateCol)
                refs.add(
                    MessageRef(
                        threadId = cursor.getLong(threadCol),
                        uniqueId = cursor.getLong(idCol),
                        // MMS dates are epoch seconds, SMS dates are epoch milliseconds
                        date = if (isMms) date * 1000 else date,
                        isMms = isMms,
                    ),
                )
                if (limit != null && refs.size >= limit) break
            }
        }
        return refs
    }

    /** Full messages for the given refs, oldest first. */
    fun getMessagesByRefs(context: Context, refs: Collection<MessageRef>): List<Message> {
        if (refs.isEmpty()) return emptyList()
        val (mmsRefs, smsRefs) = refs.partition { it.isMms }
        val messages = ArrayList<Message>(refs.size)
        if (smsRefs.isNotEmpty()) {
            messages.addAll(
                getMessages(
                    Telephony.Sms.CONTENT_URI, context, smsColumns(context),
                    "${Message.U_ID} IN (${smsRefs.joinToString(",") { it.uniqueId.toString() }})",
                    null, null, null,
                ),
            )
        }
        if (mmsRefs.isNotEmpty()) {
            messages.addAll(
                getMessages(
                    Telephony.Mms.CONTENT_URI, context, mmsColumns(context),
                    "${Message.U_ID} IN (${mmsRefs.joinToString(",") { it.uniqueId.toString() }})",
                    null, null, null,
                ),
            )
        }
        messages.sortBy { it.date }
        return messages
    }

    private fun smsColumns(context: Context): List<String> =
        if (getSubscriptionIdSupport(Telephony.Sms.CONTENT_URI, context)) (Message.smsColumns + Message.multiSIMColumns).toList()
        else Message.smsColumns.toList()

    private fun mmsColumns(context: Context): List<String> =
        if (getSubscriptionIdSupport(Telephony.Mms.CONTENT_URI, context)) (Message.mmsColumns + Message.multiSIMColumns).toList()
        else Message.mmsColumns.toList()

    /**
     * Get some messages in the given thread based on a start timestamp and an optional count
     *
     * @param context  android.content.Context running the request
     * @param threadID Optional ThreadID to look up. If not included, this method will return the latest messages from all threads.
     * @param startTimestamp Beginning of the range to return
     * @param numberToGet Number of messages to return. Pass null for "all"
     * @param getMessagesOlderStartTime If true, get messages with timestamps before the startTimestamp. If false, get newer messages
     * @return Some messages in the requested conversation
     */
    @SuppressLint("NewApi")
    fun getMessagesInRange(
        context: Context,
        threadID: ThreadID?,
        startTimestamp: Long,
        numberToGet: Long?,
        getMessagesOlderStartTime: Boolean,
    ): List<Message> {
        // The stickiness with this is that Android's MMS database has its timestamp in epoch *seconds*
        // while the SMS database uses epoch *milliseconds*.
        // I can think of no way around this other than manually querying each one with a different
        // "WHERE" statement.
        var selection: String = if (getMessagesOlderStartTime) {
            Message.DATE + " <= ?"
        } else {
            Message.DATE + " >= ?"
        }
        val smsSelectionArgs: MutableList<String> = ArrayList(2)
        smsSelectionArgs.add(startTimestamp.toString())
        val mmsSelectionArgs: MutableList<String> = ArrayList(2)
        mmsSelectionArgs.add((startTimestamp / 1000).toString())
        if (threadID != null) {
            selection += " AND " + Message.THREAD_ID + " = ?"
            smsSelectionArgs.add(threadID.toString())
            mmsSelectionArgs.add(threadID.toString())
        }

        // Light index from each table first. Suppose we were requested to return N values and
        // suppose a user sends only one MMS per week and N SMS per day. We still need the same N
        // candidates from each table, then keep only the newest N after merge — otherwise we would
        // return some very old MMS messages which would be very confusing. Full bodies are loaded
        // only for those winners.
        val smsRefs = queryRefs(
            context, isMms = false, selection, smsSelectionArgs.toTypedArray(),
            newestFirst = true, limit = numberToGet,
        )
        val mmsRefs = queryRefs(
            context, isMms = true, selection, mmsSelectionArgs.toTypedArray(),
            newestFirst = true, limit = numberToGet,
        )
        val winners = (smsRefs + mmsRefs)
            .sortedByDescending { it.date }
            .let { refs -> if (numberToGet != null) refs.take(numberToGet.toInt()) else refs }
        return getMessagesByRefs(context, winners).sortedByDescending { it.date }
    }

    /**
     * Checks if device supports `Telephony.Sms.SUBSCRIPTION_ID` column in database with URI `uri`
     *
     * @param uri Uri indicating the messages database to check
     * @param context android.content.Context running the request.
     */
    private val subscriptionIdSupport = ConcurrentHashMap<Uri, Boolean>()

    private fun getSubscriptionIdSupport(uri: Uri, context: Context): Boolean =
        subscriptionIdSupport.getOrPut(uri) { probeSubscriptionIdSupport(uri, context) }

    private fun probeSubscriptionIdSupport(uri: Uri, context: Context): Boolean {
        // Some (Xiaomi) devices running >= Android Lollipop (SDK 22+) don't support
        // `Telephony.Sms.SUBSCRIPTION_ID`, so additional check is needed.
        // It may be possible to use "sim_id" instead of "sub_id" on these devices
        // https://stackoverflow.com/a/38152331/6509200
        try {
            context.contentResolver.query(
                uri, arrayOf(Telephony.Sms.SUBSCRIPTION_ID),
                null,
                null,
                null
            ).use { availableColumnsCursor ->
                return availableColumnsCursor != null // if we got the cursor, the query shouldn't fail
            }
        } catch (e: SQLiteException) {
            // With uri content://mms-sms/conversations this query throws an exception if sub_id is not supported
            return !e.message.orEmpty().contains(Telephony.Sms.SUBSCRIPTION_ID)
        } catch (e: IllegalArgumentException) {
            return !e.message.orEmpty().contains(Telephony.Sms.SUBSCRIPTION_ID)
        }
    }

    /**
     * Gets messages which match the selection
     *
     * @param uri Uri indicating the messages database to read
     * @param context android.content.Context running the request.
     * @param fetchColumns List of columns to fetch
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder Sort ordering passed to Android's content resolver. May be null for unspecified
     * @param numberToGet Number of things to get from the result. Pass null to get all
     * @return Returns List<Message> of all messages in the return set, either in the order of sortOrder or in an unspecified order
    </Message> */
    private fun getMessages(
        uri: Uri,
        context: Context,
        fetchColumns: Collection<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        numberToGet: Long?,
    ): MutableList<Message> {
        val toReturn: MutableList<Message> = ArrayList()

        // Get all the active phone numbers so we can filter the user out of the list of targets
        // of any MMSes
        val userPhoneNumbers by lazy { TelephonyHelper.getAllPhoneNumbers(context) }
        try {
            context.contentResolver.query(
                uri,
                fetchColumns.toTypedArray(),
                selection,
                selectionArgs,
                sortOrder
            ).use { myCursor ->
                if (myCursor == null)
                    throw IllegalStateException("Message provider returned no cursor for $uri")
                if (myCursor.moveToFirst()) {
                    do {
                        val transportTypeColumn = myCursor.getColumnIndex(TRANSPORT_TYPE_DISCRIMINATOR_COLUMN)
                        val transportType = if (transportTypeColumn < 0) {
                            // The column didn't actually exist. See https://issuetracker.google.com/issues/134592631
                            // Try to determine using other information
                            val messageBoxColumn = myCursor.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                            // MessageBoxColumn is defined for MMS only
                            val messageBoxExists = messageBoxColumn >= 0 && !myCursor.isNull(messageBoxColumn)
                            if (messageBoxExists) {
                                TransportType.MMS
                            } else {
                                // There is room here for me to have made an assumption and we'll guess wrong
                                // The penalty is the user will potentially get some garbled data, so that's not too bad.
                                TransportType.SMS
                            }
                        } else {
                            val transportTypeString = myCursor.getString(transportTypeColumn)
                            if ("mms" == transportTypeString) {
                                TransportType.MMS
                            } else if ("sms" == transportTypeString) {
                                TransportType.SMS
                            } else {
                                Log.w("SMSHelper", "Skipping message with unknown TransportType: $transportTypeString")
                                continue
                            }
                        }
                        val messageInfo = HashMap<String, String?>()
                        for (columnIdx in 0 until myCursor.columnCount) {
                            val colName = myCursor.getColumnName(columnIdx)
                            val body = myCursor.getString(columnIdx)
                            messageInfo[colName] = body
                        }
                        try {
                            when (transportType) {
                                TransportType.SMS -> toReturn.add(parseSMS(context, messageInfo))
                                TransportType.MMS -> toReturn.add(parseMMS(context, messageInfo, userPhoneNumbers))
                            }
                        } catch (e: Exception) {
                            // Swallow exceptions in case we get an error reading one message so that we
                            // might be able to read some of them
                            Log.e("SMSHelper", "Got an error reading a message of type $transportType", e)
                        }
                    } while ((numberToGet == null || toReturn.size < numberToGet) && myCursor.moveToNext())
                }
            }
        } catch (e: SQLiteException) {
            var unfilteredColumns = arrayOf<String?>()
            context.contentResolver.query(uri, null, null, null, null)
                .use { unfilteredColumnsCursor ->
                    if (unfilteredColumnsCursor != null) {
                        unfilteredColumns = unfilteredColumnsCursor.columnNames
                    }
                }
            if (unfilteredColumns.isEmpty()) {
                throw MessageAccessException(uri, e)
            } else {
                throw MessageAccessException(unfilteredColumns, uri, e)
            }
        } catch (e: IllegalArgumentException) {
            var unfilteredColumns = arrayOf<String?>()
            context.contentResolver.query(uri, null, null, null, null)
                .use { unfilteredColumnsCursor ->
                    if (unfilteredColumnsCursor != null) {
                        unfilteredColumns = unfilteredColumnsCursor.columnNames
                    }
                }
            if (unfilteredColumns.isEmpty()) {
                throw MessageAccessException(uri, e)
            } else {
                throw MessageAccessException(unfilteredColumns, uri, e)
            }
        }
        return toReturn
    }

    /**
     * Gets messages which match the selection
     *
     * @param uri Uri indicating the messages database to read
     * @param context android.content.Context running the request.
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder Sort ordering passed to Android's content resolver. May be null for unspecified
     * @param numberToGet Number of things to get from the result. Pass null to get all
     * @return Returns List<Message> of all messages in the return set, either in the order of sortOrder or in an unspecified order
    </Message> */
    @SuppressLint("NewApi")
    private fun getMessages(
        uri: Uri,
        context: Context,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        numberToGet: Long?,
    ): List<Message> {
        val allColumns: MutableSet<String> = HashSet()
        allColumns.addAll(Message.smsColumns)
        allColumns.addAll(Message.mmsColumns)
        if (getSubscriptionIdSupport(uri, context)) {
            allColumns.addAll(Message.multiSIMColumns)
        }
        if (uri != mConversationUri) {
            // See https://issuetracker.google.com/issues/134592631
            allColumns.add(TRANSPORT_TYPE_DISCRIMINATOR_COLUMN)
        }
        return getMessages(
            uri,
            context,
            allColumns,
            selection,
            selectionArgs,
            sortOrder,
            numberToGet
        )
    }

    /**
     * Get all messages matching the passed filter. See documentation for Android's ContentResolver
     *
     * @param context android.content.Context running the request
     * @param selection Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param numberToGet Number of things to return. Pass null to get all
     * @return List of messages matching the filter, from newest to oldest
     */
    private fun getMessagesWithFilter(
        context: Context,
        selection: String?,
        selectionArgs: Array<String>?,
        numberToGet: Long?,
    ): List<Message> {
        val sortOrder = Message.DATE + " DESC"
        return getMessages(
            completeConversationsUri,
            context,
            selection,
            selectionArgs,
            sortOrder,
            numberToGet
        )
    }

    /**
     * Get the last message from each conversation. Can use the thread_ids in those messages to look
     * up more messages in those conversations
     *
     * Returns values ordered from oldest to most-recently-touched conversation, if possible.
     * Otherwise ordering is undefined.
     *
     * @param context android.content.Context running the request
     * @param threadIds Only return these threads. Pass null for all
     * @return Non-blocking iterable of conversation info (message with recipients)
     */
    fun getConversations(context: Context, threadIds: Set<Long>? = null, checksums: Map<Long, Long>? = null): Sequence<ConversationInfo> {
        if (threadIds?.isEmpty() == true) return emptySequence()
        // Used to avoid spewing logs in case there is an overall problem with fetching thread IDs
        var warnedForNullThreadIDs = false

        // Used to avoid spewing logs in case the date column doesn't return anything.
        var warnedForUnorderedOutputs = false

        // Step 1: Populate the list of all known threadIDs
        // This is basically instantaneous even with lots of conversations because we only make one
        // query. If someone wanted to squeeze better UI performance out of this method, they could
        // iterate over the threadIdCursor instead of getting all the threads before beginning to
        // return conversations, but I doubt anyone will ever find it necessary.
        var threadInfoList: List<ThreadInfo>
        context.contentResolver.query(
            mConversationUri,
            null,
            threadIds?.let { "${Threads._ID} IN (${it.joinToString(",")})" },
            null,
            null
        ).use { threadIdCursor ->
            val threadInfos: MutableList<ThreadInfo> = ArrayList()
            while (threadIdCursor != null && threadIdCursor.moveToNext()) {
                // The "_id" column returned from the `content://sms-mms/conversations?simple=true` URI
                // is actually what the rest of the world calls a thread_id.
                // In my limited experimentation, the other columns are not populated, so don't bother
                // looking at them here.
                val idColumn = threadIdCursor.getColumnIndex(Threads._ID)
                val dateColumn = threadIdCursor.getColumnIndex(Threads.DATE)
                var threadID: ThreadID? = null
                var messageDate: Long = -1
                
                if (!threadIdCursor.isNull(idColumn)) {
                    threadID = ThreadID(threadIdCursor.getLong(idColumn))
                }
                // Skip before the per-recipient lookups below, which are the expensive part
                if (threadIds != null && threadID?.threadID !in threadIds) {
                    continue
                }
                if (!threadIdCursor.isNull(dateColumn)) {
                    // I think the presence of the "date" column depends on the specifics of the
                    // device. If it's there, we'll use it to return threads in a sorted order.
                    // If it's not there, we'll return them unsorted (maybe you get lucky and the
                    // conversations URI returns sorted anyway).
                    messageDate = threadIdCursor.getLong(dateColumn)
                }
                val recipients = resolveThreadRecipients(context, threadIdCursor)
                
                if (messageDate <= 0) {
                    if (!warnedForUnorderedOutputs) {
                        Log.w(
                            "SMSHelper",
                            "Got no value for date of thread. Return order of results is undefined."
                        )
                        warnedForUnorderedOutputs = true
                    }
                }
                if (threadID == null) {
                    if (!warnedForNullThreadIDs) {
                        Log.w(
                            "SMSHelper",
                            "Got null for some thread IDs. If these were valid threads, they will not be returned."
                        )
                        warnedForNullThreadIDs = true
                    }
                    continue
                }
                
                threadInfos.add(ThreadInfo(threadID, messageDate, recipients, checksums?.get(threadID.threadID) ?: threadChecksum(threadIdCursor)))
            }
            threadInfoList = threadInfos
                // Sort oldest-to-newest (smallest to largest)
                .sortedBy { it.messageDate }
        }

        // Step 2: Get the actual message object from each thread ID with recipients
        // Do this in a sequence, so that the caller can choose to interrupt us as frequently as desired
        return sequence {
            for (threadInfo in threadInfoList) {
                val firstMessage = getMessagesInThread(context, threadInfo.threadId, 1L)
                if (firstMessage.size > 1) {
                    Log.w("SMSHelper", "getConversations got two messages for the same ThreadID: ${threadInfo.threadId}")
                }
                if (firstMessage.isEmpty()) {
                    Log.e("SMSHelper", "ThreadID: ${threadInfo.threadId} did not return any messages")
                    // This is a strange issue, but I don't know how to say what is wrong, so just continue along
                    continue
                }
                yield(ConversationInfo(firstMessage[0], threadInfo.recipients, threadInfo.checksum))
            }
        }
    }

    /** Conversation-list change detection uses one query of the threads table. */
    data class ThreadState(val checksum: Long, val messageCount: Long)

    fun getThreadStates(context: Context): Map<Long, ThreadState> {
        val states = mutableMapOf<Long, ThreadState>()
        context.contentResolver.query(mConversationUri, null, null, null, null).use { cursor ->
            if (cursor == null) throw IllegalStateException("Thread provider returned no cursor")
            val idColumn = cursor.getColumnIndexOrThrow(Threads._ID)
            val countColumn = cursor.getColumnIndex(Threads.MESSAGE_COUNT)
            while (cursor.moveToNext()) {
                if (cursor.isNull(idColumn)) continue
                val count = if (countColumn >= 0 && !cursor.isNull(countColumn)) cursor.getLong(countColumn) else 0L
                states[cursor.getLong(idColumn)] = ThreadState(threadChecksum(cursor), count)
            }
        }
        return states
    }

    fun getThreadChecksums(context: Context): Map<Long, Long> =
        getThreadStates(context).mapValues { it.value.checksum }

    private fun threadChecksum(cursor: Cursor): Long {
        var checksum = 17L
        for (column in arrayOf(Threads.DATE, Threads.MESSAGE_COUNT, Threads.READ)) {
            val index = cursor.getColumnIndex(column)
            val value = if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            checksum = checksum * 31 + value
        }
        return checksum
    }

    /** Thread roster only — no tip message. */
    fun getThreadRecipients(context: Context, threadId: Long): List<String> {
        context.contentResolver.query(
            mConversationUri,
            null,
            "${Threads._ID}=?",
            arrayOf(threadId.toString()),
            null
        ).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                return resolveThreadRecipients(context, cursor)
            }
        }
        return emptyList()
    }

    private fun resolveThreadRecipients(context: Context, cursor: Cursor): List<String> {
        val recipientsColumn = cursor.getColumnIndex(Threads.RECIPIENT_IDS)
        if (recipientsColumn < 0 || cursor.isNull(recipientsColumn)) return emptyList()

        val recipients = mutableListOf<String>()
        for (recipient in cursor.getString(recipientsColumn).split(" ")) {
            val id = recipient.trim()
            if (id.isEmpty()) continue
            context.contentResolver.query(
                "content://mms-sms/canonical-addresses".toUri(),
                null,
                "_id = ?",
                arrayOf(id),
                null
            ).use { addressCursor ->
                if (addressCursor != null && addressCursor.moveToFirst()) {
                    // The canonical-addresses table typically has columns: _id, address
                    addressCursor.getString(1)?.let { recipients.add(it) }
                }
            }
        }
        return recipients
    }

    private fun addEventFlag(oldEvent: Int, eventFlag: Int): Int {
        return oldEvent or eventFlag
    }

    /**
     * Parse all parts of an SMS into a Message
     */
    private fun parseSMS(context: Context, messageInfo: Map<String, String?>): Message {
        return Message(
            addresses = listOf(Address(context, messageInfo[Telephony.Sms.ADDRESS]!!)),
            body = messageInfo[Message.BODY] ?: "",
            date = messageInfo[Message.DATE]?.toLongOrNull() ?: 0L,
            type = messageInfo[Message.TYPE]?.toIntOrNull() ?: 0,
            read = messageInfo[Message.READ]?.toIntOrNull() ?: 0,
            threadID = ThreadID(messageInfo[Message.THREAD_ID]?.toLongOrNull() ?: ThreadID.invalidThreadId.threadID),
            uID = messageInfo[Message.U_ID]?.toLongOrNull() ?: 0L,
            subscriptionID = messageInfo[Message.SUBSCRIPTION_ID]?.toIntOrNull() ?: 0,
            attachments = null,
        )
    }

    /**
     * Parse all parts of the MMS message into a message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private fun parseMMS(context: Context, messageInfo: Map<String, String?>, userPhoneNumbers: List<LocalPhoneNumber>): Message {
        var event = Message.EVENT_UNKNOWN
        var body = ""
        val read = NumberUtils.toInt(messageInfo[Message.READ])
        val threadID = ThreadID(
            NumberUtils.toLong(
                messageInfo.getOrDefault(Message.THREAD_ID, null),
                ThreadID.invalidThreadId.threadID
            )
        )
        val uID = NumberUtils.toLong(messageInfo[Message.U_ID])
        val subscriptionID = NumberUtils.toInt(messageInfo[Message.SUBSCRIPTION_ID])
        val attachments: MutableList<Attachment> = ArrayList()
        val columns = arrayOf(
            Telephony.Mms.Part._ID,  // The content ID of this part
            Telephony.Mms.Part._DATA,  // The location in the filesystem of the data
            Telephony.Mms.Part.CONTENT_TYPE,  // The mime type of the data
            Telephony.Mms.Part.TEXT,  // The plain text body of this MMS
            Telephony.Mms.Part.CHARSET,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.CONTENT_LOCATION,
        )
        val mmsID = messageInfo[Message.U_ID]
        val selection = Telephony.Mms.Part.MSG_ID + " = ?"
        val selectionArgs = arrayOf(mmsID)

        // Get text body and attachments of the message
        try {
            context.contentResolver.query(
                mMSPartUri,
                columns,
                selection,
                selectionArgs,
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val partIDColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._ID)
                    val contentTypeColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE)
                    val dataColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA)
                    val textColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.TEXT)
                    val fileNameColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.FILENAME)
                    val nameColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.NAME)
                    val contentLocationColumn = cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_LOCATION)
                    // TODO: Parse charset (As usual, it is skimpily documented) (Possibly refer to MMS spec)
                    do {
                        val partID = cursor.getLong(partIDColumn)
                        val contentType = cursor.getString(contentTypeColumn)
                        val data = cursor.getString(dataColumn)
                        val fileName = SmsMmsUtils.resolveMmsPartFileName(
                            partId = partID,
                            fileName = cursor.getString(fileNameColumn),
                            name = cursor.getString(nameColumn),
                            contentLocation = cursor.getString(contentLocationColumn),
                            dataPath = data,
                        )
                        if (MimeType.isTypeText(contentType)) {
                            body = if (data != null) {
                                // data != null means the data is on disk. Go get it.
                                getMmsText(context, partID)
                            } else {
                                cursor.getString(textColumn)
                            }
                            event = addEventFlag(event, Message.EVENT_TEXT_MESSAGE)
                        } else if (
                            // SMIL is the MMS layout document, not a user-facing file
                            contentType.equals("application/smil", ignoreCase = true)
                            // RCS / RBM protocol parts (bot JSON, FT HTTP XML, location, etc.).
                            // Carriers persist these in mms/part next to the real media; they are
                            // not downloadable attachments (ghosts like …_attachment on desktop).
                            || contentType.startsWith("application/vnd.gsma.", ignoreCase = true)
                        ) {
                            // skip
                        } else {
                            val thumbnail = SmsMmsUtils.createThumbnail(context, partID, contentType)
                            attachments.add(Attachment(partID, contentType, thumbnail, fileName))
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("SMSHelper", "Could not read parts of MMS $mmsID", e)
        }

        // Determine whether the message was in- our out- bound
        val messageBox = messageInfo[Telephony.Mms.MESSAGE_BOX]?.toIntOrNull() ?: 0
        val type = when (messageBox) {
            Telephony.Mms.MESSAGE_BOX_INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
            Telephony.Mms.MESSAGE_BOX_SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
            else -> {
                // As an undocumented feature, it looks like the values of Mms.MESSAGE_BOX_*
                // are the same as Sms.MESSAGE_TYPE_* of the same type. So by default let's just use
                // the value we've got.
                // This includes things like drafts, which are a far-distant plan to support
                messageBox
            }
        }
        // Get address(es) of the message
        val msg = getMessagePdu(context, uID)
        val from = SmsMmsUtils.getMmsFrom(context, msg)
        val to = SmsMmsUtils.getMmsTo(context, msg)
        val addresses: MutableList<Address> = ArrayList()
        if (from != null) {
            val isLocalPhoneNumber = userPhoneNumbers
                .any { localPhoneNumber: LocalPhoneNumber ->
                    localPhoneNumber.isMatchingPhoneNumber(from.getAddress())
                }
            if (!isLocalPhoneNumber && from.toString() != "insert-address-token") {
                addresses.add(from)
            }
        }
        if (to != null) {
            for (toAddress in to) {
                val isLocalPhoneNumber = userPhoneNumbers
                    .any { localPhoneNumber: LocalPhoneNumber ->
                        localPhoneNumber.isMatchingPhoneNumber(toAddress.getAddress())
                    }
                if (!isLocalPhoneNumber && toAddress.toString() != "insert-address-token") {
                    addresses.add(toAddress)
                }
            }
        }

        // It looks like addresses[0] is always the sender of the message and
        // following addresses are recipient(s)
        // This usually means the addresses list is at least 2 long, but there are cases (special
        // telco service messages) where it is not (only 1 long in that case, just the "sender")
        if (addresses.size >= 2) {
            event = addEventFlag(event, Message.EVENT_MULTI_TARGET)
        }

        // Canonicalize the date field
        // SMS uses epoch milliseconds, MMS uses epoch seconds. Standardize on milliseconds.
        val rawDate = messageInfo[Message.DATE]?.toLongOrNull() ?: 0L
        val date = rawDate * 1000
        return Message(
            addresses,
            body,
            date,
            type,
            read,
            threadID,
            uID,
            subscriptionID,
            attachments
        )
    }

    private fun getMessagePdu(context: Context, uID: Long): MultimediaMessagePdu? {
        val uri = ContentUris.appendId(Telephony.Mms.CONTENT_URI.buildUpon(), uID).build()
        return try {
            // Work around https://bugs.kde.org/show_bug.cgi?id=434348 by querying the PduCache directly
            // Most likely, this is how we should do business anyway and we will probably see a
            // decent speedup...
            val pduCache = PduCache.getInstance()
            val maybePduValue: PduCacheEntry? = synchronized(pduCache) { pduCache[uri] }
            if (maybePduValue != null) {
                maybePduValue.pdu as MultimediaMessagePdu
            } else {
                PduPersister.getPduPersister(context).load(uri) as MultimediaMessagePdu
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get a text part of an MMS message
     * Original implementation from https://stackoverflow.com/a/6446831/3723163
     */
    private fun getMmsText(context: Context, id: Long): String {
        val partURI = ContentUris.withAppendedId(mMSPartUri, id)
        var body = ""
        try {
            context.contentResolver.openInputStream(partURI).use { stream ->
                if (stream != null) {
                    // The stream is buffered internally, so buffering it separately is unnecessary.
                    body = IOUtils.toString(stream, UTF_8)
                }
            }
        } catch (e: IOException) {
            throw MessageAccessException(partURI, e)
        }
        return body
    }

    /**
     * Represents thread information with recipients
     */
    data class ThreadInfo(
        val threadId: ThreadID,
        val messageDate: Long,
        val recipients: List<String>,
        val checksum: Long
    )

    /**
     * Represents a conversation with its latest message and recipients
     */
    data class ConversationInfo(
        val message: Message,
        val recipients: List<String>,
        val checksum: Long
    )

    /**
     * Represent an ID used to uniquely identify a message thread
     */
    class ThreadID(val threadID: Long) {
        override fun toString(): String = threadID.toString()

        override fun hashCode(): Int = java.lang.Long.hashCode(threadID)

        override fun equals(other: Any?): Boolean {
            return other!!.javaClass.isAssignableFrom(ThreadID::class.java) && (other as ThreadID?)!!.threadID == threadID
        }

        companion object {

            /**
             * Define a value against which we can compare others, which should never be returned from
             * a valid thread.
             */
            val invalidThreadId = ThreadID(-1)
        }
    }

    class Attachment(
        val partID: Long,
        val mimeType: String,
        val encodedFile: String?,
        val uniqueIdentifier: String,
    ) {

        companion object {
            /**
             * Attachment object field names
             */
            const val PART_ID = "part_id"
            const val MIME_TYPE = "mime_type"
            const val ENCODED_THUMBNAIL = "encoded_thumbnail"
            const val UNIQUE_IDENTIFIER = "unique_identifier"
        }
    }

    class Address(context: Context, private val address: String) {
        fun getAddress() = address

        override fun toString() = address

        private val networkCountryIso = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso

        override fun equals(other: Any?) = when (other) {
            null -> false
            is Address -> isSameNumber(other.address)
            is String -> isSameNumber(other)
            else -> false
        }

        private fun isSameNumber(other: String): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PhoneNumberUtils.areSamePhoneNumber(address, other, networkCountryIso)
            } else {
                @Suppress("DEPRECATION")
                PhoneNumberUtils.compare(address, other)
            }

        override fun hashCode(): Int {
            return address.hashCode()
        }

        companion object {
            /**
             * Address object field names
             */
            const val ADDRESS = "address"
        }
    }

    /**
     * Indicate that some error has occurred while reading a message.
     * More useful for logging than catching and handling
     */
    class MessageAccessException : RuntimeException {
        internal constructor(uri: Uri, cause: Throwable?) : super("Error getting messages from $uri", cause)

        internal constructor(availableColumns: Array<String?>, uri: Uri, cause: Throwable?) :
            super("Error getting messages from $uri. Available columns were: ${availableColumns.contentToString()}", cause)
    }

    /**
     * Represent all known transport types
     */
    enum class TransportType {
        SMS,
        MMS
        // Maybe in the future there will be more TransportType, but for now these are all I know about
    }

    /**
     * Represent a message and all of its interesting data columns
     */
    class Message internal constructor(
        val addresses: List<Address>,
        val body: String,
        val date: Long,
        val type: Int,
        val read: Int,
        val threadID: ThreadID,
        val uID: Long,
        val subscriptionID: Int,
        val attachments: List<Attachment>?,
    ) {
        override fun toString() = body

        companion object {
            /**
             * Named constants which are used to construct a Message
             * See: https://developer.android.com/reference/android/provider/Telephony.TextBasedSmsColumns.html for full documentation
             */
            const val ADDRESSES = "addresses" // Contact information (phone number or otherwise) of the remote
            const val BODY = Telephony.Sms.BODY // Body of the message
            const val DATE = Telephony.Sms.DATE // Date (Unix epoch millis) associated with the message
            const val TYPE = Telephony.Sms.TYPE // Compare with Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*
            const val READ = Telephony.Sms.READ // Whether we have received a read report for this message (int)
            const val THREAD_ID = Telephony.Sms.THREAD_ID // Magic number which binds (message) threads
            const val U_ID = Telephony.Sms._ID // Something which uniquely identifies this message
            const val EVENT = "event"
            const val SUBSCRIPTION_ID = Telephony.Sms.SUBSCRIPTION_ID // An ID which appears to identify a SIM card
            const val ATTACHMENTS = "attachments" // List of files attached in an MMS

            /**
             * Event flags
             * A message should have a bitwise-or of event flags before delivering the packet
             * Any events not supported by the receiving device should be ignored
             */
            const val EVENT_UNKNOWN = 0x0 // The message was of some type we did not understand
            const val EVENT_TEXT_MESSAGE = 0x1 // This message has a "body" field which contains

            // pure, human-readable text
            const val EVENT_MULTI_TARGET = 0x2 // Indicates that this message has multiple recipients

            /**
             * Define the columns which are to be extracted from the Android SMS database
             */
            val smsColumns = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID,
                U_ID
            )
            val mmsColumns = arrayOf(
                U_ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.TEXT_ONLY,
                Telephony.Mms.MESSAGE_BOX
            )

            /**
             * These columns are for determining what SIM card the message belongs to
             */
            val multiSIMColumns = arrayOf(Telephony.Sms.SUBSCRIPTION_ID)
        }
    }

    /**
     * If anyone wants to subscribe to changes in the messages database, they will need a thread
     * to handle callbacks on
     * This singleton conveniently provides such a thread, accessed and used via its Looper object
     */
    class MessageLooper private constructor() : Thread() {
        init {
            name = "MessageHelperLooper"
        }

        override fun run() {
            looperReadyLock.lock()
            try {
                Looper.prepare()
                looper = Looper.myLooper()
                looperReady.signalAll()
            } finally {
                looperReadyLock.unlock()
            }
            Looper.loop()
        }

        companion object {
            private var singleton: MessageLooper? = null
            private var looper: Looper? = null
            private val looperReadyLock: Lock = ReentrantLock()
            private val looperReady = looperReadyLock.newCondition()

            /**
             * Get the Looper object associated with this thread
             *
             * If the Looper has not been prepared, it is prepared as part of this method call.
             * Since this means a thread has to be spawned, this method might block until that thread is
             * ready to serve requests
             */
            @JvmStatic // required for ConnectivityReportPlugin.java
            fun getLooper(): Looper? {
                if (singleton == null) {
                    looperReadyLock.lock()
                    try {
                        singleton = MessageLooper().apply { start() }
                        while (looper == null) {
                            // Block until the looper is ready
                            looperReady.await()
                        }
                    } catch (e: InterruptedException) {
                        // I don't know when this would happen
                        Log.e("SMSHelper", "Interrupted while waiting for Looper", e)
                        return null
                    } finally {
                        looperReadyLock.unlock()
                    }
                }
                return looper
            }
        }
    }
}
