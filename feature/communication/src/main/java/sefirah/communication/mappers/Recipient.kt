package sefirah.communication.mappers

import android.content.Context
import android.database.Cursor
import androidx.core.net.toUri
import javax.inject.Inject

class Recipient @Inject constructor(
    private val context: Context
) {
    companion object {
        val URI = "content://mms-sms/canonical-addresses".toUri()

        const val COLUMN_ID = 0
        const val COLUMN_ADDRESS = 1
    }

    fun getAddress(cursor: Cursor) : String {
        return cursor.getString(COLUMN_ADDRESS)
    }

    fun getRecipientCursor() = context.contentResolver.query(URI, null, null, null, null)

    fun getRecipientCursor(id: Long) = context.contentResolver.query(URI, null, "_id = ?", arrayOf(id.toString()), null)
}