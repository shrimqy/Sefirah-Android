package sefirah.communication.call

import android.annotation.SuppressLint
import android.content.Context
import android.provider.CallLog
import sefirah.communication.utils.ContactsHelper
import sefirah.domain.model.CallLogInfo
import sefirah.domain.model.CallLogType

object CallLogHelper {
    private val contactHelper = ContactsHelper()
    private const val MAX_SYNC_CALL_LOGS = 200

    @SuppressLint("MissingPermission")
    fun getCallLogs(context: Context, sinceMillisExclusive: Long? = null): List<CallLogInfo> {
        val resolver = context.contentResolver
        val selection = sinceMillisExclusive?.let { "${CallLog.Calls.DATE} > ?" }
        val selectionArgs = sinceMillisExclusive?.let { arrayOf(it.toString()) }
        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC",
        ) ?: return emptyList()

        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberIdx = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateIdx = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = it.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val typeIdx = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val tmp = ArrayList<CallLogInfo>()
            while (it.moveToNext() && tmp.size < MAX_SYNC_CALL_LOGS) {
                val number = it.getString(numberIdx).orEmpty()
                tmp.add(
                    CallLogInfo(
                        callLogId = it.getLong(idIdx),
                        phoneNumber = number,
                        timestampMillis = it.getLong(dateIdx),
                        durationSeconds = it.getLong(durationIdx),
                        callType = mapCallType(it.getInt(typeIdx)),
                        contactInfo = if (sinceMillisExclusive != null) contactHelper.getContactInfo(context, number) else null,
                    ),
                )
            }
            tmp.reverse()
            return tmp
        }
    }

    private fun mapCallType(type: Int): CallLogType = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallLogType.Incoming
        CallLog.Calls.OUTGOING_TYPE -> CallLogType.Outgoing
        CallLog.Calls.MISSED_TYPE -> CallLogType.Missed
        CallLog.Calls.VOICEMAIL_TYPE -> CallLogType.Voicemail
        CallLog.Calls.REJECTED_TYPE -> CallLogType.Rejected
        CallLog.Calls.BLOCKED_TYPE -> CallLogType.Blocked
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallLogType.AnsweredExternally
        else -> CallLogType.Unknown
    }
}
