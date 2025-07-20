package sefirah.communication.utils

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import androidx.core.net.toUri
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import sefirah.domain.model.ContactMessage
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.text.Charsets.UTF_8

class ContactsHelper {
        /**
     * Get contact information including name, phone number and photo (if available)
     * @param context Context in which the method is called
     * @param phoneNumber Phone number to look up contact information for
     * @return Contact object containing name, phone number and base64 encoded photo
     */
    fun getContactInfo(context: Context, phoneNumber: String): ContactMessage? {
        // Check if the number is valid
        val isValidNumber = try {
            PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber)
        } catch (e: Exception) {
            false
        }

        // If not a valid number, return a Contact with just the original input
        if (!isValidNumber) {
            return null
        }

        // Look up contact info for valid numbers
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))

        try {
            context.contentResolver.query(uri, PROJECTION, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val id = cursor.getString(COLUMN_ID)           
                    val lookupKey = cursor.getString(COLUMN_LOOKUP_KEY)    
                    val number = cursor.getString(COLUMN_NUMBER)    
                    val displayName = cursor.getString(COLUMN_DISPLAY_NAME)  
                    val photoBase64 = photoId64Encoded(context, cursor.getString(COLUMN_PHOTO_URI))
                    return ContactMessage(
                        id = id,
                        lookupKey = lookupKey,
                        number = number,
                        displayName = displayName,
                        photoBase64 = photoBase64
                    )
                }
            }
        } catch (ignored: Exception) {
            return null
        }

        return null
    }

    private fun photoId64Encoded(context: Context, photoId: String?): String? {
        if (photoId == null) {
            return ""
        }
        val photoUri = photoId.toUri()

        val encodedPhoto = ByteArrayOutputStream()
        try {
            context.contentResolver.openInputStream(photoUri).use { input ->
                Base64OutputStream(encodedPhoto, Base64.DEFAULT).use { output ->
                    IOUtils.copy(input, output, 1024)
                    return encodedPhoto.toString()
                }
            }
        } catch (ex: java.lang.Exception) {
            Log.e(TAG, ex.toString())
            return ""
        }
    }

    /**
     * Return all contacts with full information including photo, display name, and phone number
     *
     * @param context android.content.Context running the request
     * @return List of ContactMessage objects with complete contact information
     */
    fun getAllContacts(context: Context): List<ContactMessage> {
        val toReturn: ArrayList<ContactMessage> = ArrayList()

        // Query the Phone content URI to get all phone numbers with contact info
        val contactsUri = Phone.CONTENT_URI 
        try {
            context.contentResolver.query(contactsUri, PROJECTION, null, null, Phone.DISPLAY_NAME + " ASC")
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            val id = cursor.getString(COLUMN_ID)           
                            val lookupKey = cursor.getString(COLUMN_LOOKUP_KEY)    
                            val number = cursor.getString(COLUMN_NUMBER)    
                            val displayName = cursor.getString(COLUMN_DISPLAY_NAME)  
                            val photoUri = cursor.getString(COLUMN_PHOTO_URI)     

                            if (id == null || number == null) {
                                Log.w(TAG, "Skipping contact with missing essential information")
                                continue
                            }

                            val photoBase64 = photoId64Encoded(context, photoUri)

                            val contactMessage = ContactMessage(
                                id = id,
                                lookupKey = lookupKey,
                                number = number,
                                displayName = displayName,
                                photoBase64 = photoBase64
                            )

                            toReturn.add(contactMessage)

                        } while (cursor.moveToNext())
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
        }

        return toReturn
    }

    /**
     * Get VCards using serial database lookups. This is tragically slow, so call only when needed.
     *
     * There is a faster API specified using ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
     * but there does not seem to be a way to figure out which ID resulted in which VCard using that API
     *
     * @param context    android.content.Context running the request
     * @param IDs        collection of uIDs to look up
     * @return Mapping of uIDs to the corresponding VCard
     */
    private fun getVCardsSlow(context: Context, IDs: Collection<UID>): Map<UID, VCardBuilder> {
        val toReturn: MutableMap<UID, VCardBuilder> = java.util.HashMap<UID, VCardBuilder>()

        for (ID in IDs) {
            val lookupKey: String = ID.toString()
            val vcardURI =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

            try {
                context.contentResolver.openInputStream(vcardURI).use { input ->
                    if (input == null) {
                        throw NullPointerException("ContentResolver did not give us a stream for the VCard for uID $ID")
                    }
                    val lines: List<String?> = IOUtils.readLines(input, UTF_8)
                    toReturn.put(ID, VCardBuilder(StringUtils.join(lines, '\n')))
                }
            } catch (e: IOException) {
                // If you are experiencing this, please open a bug report indicating how you got here
                Log.e("Contacts", "Exception while fetching vcards", e)
            } catch (e: NullPointerException) {
                Log.e("Contacts", "Exception while fetching vcards", e)
            }
        }

        return toReturn
    }

    /**
     * Get the VCard for every specified raw contact ID
     *
     * @param context android.content.Context running the request
     * @param IDs     collection of raw contact IDs to look up
     * @return Mapping of raw contact IDs to the corresponding VCard
     */
    fun getVCardsForContactIDs(context: Context, IDs: Collection<UID>): Map<UID, VCardBuilder> {
        return getVCardsSlow(context, IDs)
    }

    /**
     * Get the last-modified timestamp for every contact in the database
     *
     * @param context android.content.Context running the request
     * @return Mapping of contact UID to last-modified timestamp
     */
    fun getAllContactTimestamps(context: Context): Map<UID, Long> {
        val projection = arrayOf(UID.COLUMN, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)

        val databaseValues: Map<UID, Map<String, String>> =
            accessContactsDatabase(
                context,
                projection,
                null,
                null,
                null
            )

        val timestamps: MutableMap<UID, Long> = java.util.HashMap<UID, Long>()
        for (contactID in databaseValues.keys) {
            val data = databaseValues[contactID]!!
            timestamps[contactID] =
                data[ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP]!!.toLong()
        }

        return timestamps
    }

    /**
     * Return a mapping of contact IDs to a map of the requested data from the Contacts database.
     *
     * @param context    android.content.Context running the request
     * @param projection List of column names to extract, defined in ContactsContract.Contacts. Must contain uID.COLUMN
     * @param selection  Parameterizable filter to use with the ContentResolver query. May be null.
     * @param selectionArgs Parameters for selection. May be null.
     * @param sortOrder  Sort order to request from the ContentResolver query. May be null.
     * @return mapping of contact uIDs to desired values, which are a mapping of column names to the data contained there
     */
    private fun accessContactsDatabase(
        context: Context,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Map<UID, Map<String, String>> {
        val contactsUri = ContactsContract.Contacts.CONTENT_URI

        val toReturn = java.util.HashMap<UID, Map<String, String>>()

        context.contentResolver.query(
            contactsUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        ).use { contactsCursor ->
            if (contactsCursor != null && contactsCursor.moveToFirst()) {
                do {
                    val requestedData: MutableMap<String, String> = java.util.HashMap()

                    val uIDIndex = contactsCursor.getColumnIndexOrThrow(UID.COLUMN)
                    val uID = UID(contactsCursor.getString(uIDIndex))

                    // For each column, collect the data from that column
                    for (column in projection) {
                        val index = contactsCursor.getColumnIndex(column)
                        if (index == -1) {
                            // This contact didn't have the requested column? Something is very wrong.
                            // If you are experiencing this, please open a bug report indicating how you got here
                            Log.e(TAG, "Got a contact which does not have a requested column")
                            continue
                        }
                        // Since we might be getting various kinds of data, Object is the best we can do
                        val data = contactsCursor.getString(index)

                        requestedData[column] = data
                    }

                    toReturn[uID] = requestedData
                } while (contactsCursor.moveToNext())
            }
        }
        return toReturn
    }

    /**
     * This is a cheap ripoff of com.android.vcard.VCardBuilder
     *
     *
     * Maybe in the future that library will be made public and we can switch to using that!
     *
     *
     * The main similarity is the usage of .toString() to produce the finalized VCard and the
     * usage of .appendLine(String, String) to add stuff to the vcard
     */
    class VCardBuilder internal constructor(vcard: String) {
        // Remove the end tag. We will add it back on in .toString()
        private val vcardBody = StringBuilder(vcard.substring(0, vcard.indexOf(VCARD_END)))

        /**
         * Appends one line with a given property name and value.
         */
        fun appendLine(propertyName: String?, rawValue: String?) {
            vcardBody.append(propertyName)
                .append(VCARD_DATA_SEPARATOR)
                .append(rawValue)
                .append("\n")
        }

        override fun toString(): String { return vcardBody.toString() + VCARD_END }

        companion object {
            const val VCARD_END: String = "END:VCARD" // Written to terminate the vcard
            const val VCARD_DATA_SEPARATOR: String = ":"
        }
    }

    /**
     * Essentially a typedef of the type used for a unique identifier
     */
    class UID(lookupKey: String?) {
        /**
         * We use the LOOKUP_KEY column of the Contacts table as a unique ID, since that's what it's
         * for
         */
        val contactLookupKey: String

        init {
            requireNotNull(lookupKey) { "lookUpKey should not be null" }

            contactLookupKey = lookupKey
        }

        override fun toString(): String {
            return this.contactLookupKey
        }

        override fun hashCode(): Int {
            return contactLookupKey.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (other is UID) {
                return contactLookupKey == other.contactLookupKey
            }
            return contactLookupKey == other
        }

        companion object {
            /**
             * Which Contacts column this uID is pulled from
             */
            const val COLUMN: String = ContactsContract.Contacts.LOOKUP_KEY
        }
    }

    /**
     * Exception to indicate that a specified contact was not found
     */
    class ContactNotFoundException : java.lang.Exception {
        constructor(contactID: UID) : super("Unable to find contact with ID $contactID")

        constructor(message: String?) : super(message)
    }

    companion object {
        const val TAG = "ContactsHelper"

        val PROJECTION = arrayOf(
            Phone._ID,
            Phone.LOOKUP_KEY,
            Phone.NUMBER,
            Phone.DISPLAY_NAME,
            Phone.PHOTO_URI,
        )

        const val COLUMN_ID = 0
        const val COLUMN_LOOKUP_KEY = 1
        const val COLUMN_NUMBER = 2
        const val COLUMN_DISPLAY_NAME = 3
        const val COLUMN_PHOTO_URI = 4
    }
}