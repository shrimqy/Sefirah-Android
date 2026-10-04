/*
 * SPDX-FileCopyrightText: 2020 Aniket Kumar <anikketkumar786@gmail.com>
 * SPDX-FileCopyrightText: 2021 Simon Redman <simon@ergotech.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package sefirah.communication.sms

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.android.mms.dom.smil.parser.SmilXmlSerializer
import com.google.android.mms.ContentType
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.MMSPart
import com.google.android.mms.pdu_alt.CharacterSets
import com.google.android.mms.pdu_alt.EncodedStringValue
import com.google.android.mms.pdu_alt.MultimediaMessagePdu
import com.google.android.mms.pdu_alt.PduBody
import com.google.android.mms.pdu_alt.PduComposer
import com.google.android.mms.pdu_alt.PduHeaders
import com.google.android.mms.pdu_alt.PduPart
import com.google.android.mms.pdu_alt.RetrieveConf
import com.google.android.mms.pdu_alt.SendReq
import com.google.android.mms.smil.SmilHelper
import com.klinker.android.send_message.Message
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.commons.io.IOUtils
import sefirah.common.util.MimeType
import sefirah.communication.utils.TelephonyHelper
import sefirah.communication.utils.TelephonyHelper.LocalPhoneNumber
import sefirah.domain.model.SmsAttachment
import sefirah.domain.model.TextMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import androidx.core.graphics.scale
import kotlin.time.Duration.Companion.milliseconds

object SmsMmsUtils {
    const val EXTRA_TEMP_ID = "tempId"

    private const val SENDING_MESSAGE = "Sending message"
    private const val THUMBNAIL_MAX_EDGE = 512
    private const val TEMP_ID_AWAIT_TIMEOUT_MS = 5_000L
    private val sentIntentRequestCode = AtomicInteger()
    private val tempIdByUniqueId = ConcurrentHashMap<Long, Long>()
    @Volatile private var pendingTempIdMap: CompletableDeferred<Unit>? = null

    private fun getSendingPhoneNumber(context: Context, subscriptionID: Int): LocalPhoneNumber {
        val sendingPhoneNumber: LocalPhoneNumber
        val allPhoneNumbers = TelephonyHelper.getAllPhoneNumbers(context)

        val maybeSendingPhoneNumber = allPhoneNumbers.firstOrNull { localPhoneNumber -> localPhoneNumber.subscriptionID == subscriptionID }
        if (maybeSendingPhoneNumber != null) {
            sendingPhoneNumber = maybeSendingPhoneNumber
        }
        else {
            if (allPhoneNumbers.isEmpty()) {
                // We were not able to get any phone number for the user's device
                // Use a null "dummy" number instead. This should behave the same as not setting
                // the FromAddress (below) since the default value there is null.
                // The only more-correct thing we could do here is query the user (maybe in a
                // persistent configuration) for their phone number(s).
                sendingPhoneNumber = LocalPhoneNumber(null.toString(), subscriptionID)
                Log.w(
                    SENDING_MESSAGE, ("We do not know *any* phone numbers for this device. "
                        + "Attempting to send a message without knowing the local phone number is likely "
                        + "to result in strange behavior, such as the message being sent to yourself, "
                        + "or might entirely fail to send (or be received).")
                )
            } else {
                // Pick an arbitrary phone number
                sendingPhoneNumber = allPhoneNumbers[0]
            }
            Log.w(SENDING_MESSAGE, "Unable to determine correct outgoing address for sub ID $subscriptionID. Using $sendingPhoneNumber")
        }
        return sendingPhoneNumber
    }

    private fun getTransactionSettings(
        context: Context,
        subID: Int,
        longTextAsMms: Boolean,
        groupMessageAsMms: Boolean,
        convertToMmsAfter: Int,
    ): Settings {
        val settings = Settings()

        val apnSettings = TelephonyHelper.getPreferredApn(context, subID)
        if (apnSettings != null) {
            settings.mmsc = apnSettings.mmsc.toString()
            settings.proxy = apnSettings.mmsProxyAddressAsString
            settings.port = apnSettings.mmsProxyPort.toString()
        } else {
            settings.useSystemSending = true
        }

        settings.sendLongAsMms = longTextAsMms
        settings.sendLongAsMmsAfter = convertToMmsAfter
        settings.group = groupMessageAsMms

        if (subID > 0) {
            settings.subscriptionId = subID
        }

        return settings
    }

    /**
     * Sends SMS or MMS message.
     *
     * @param context       context in which the method is called.
     * @param textMessage   text body of the message to be sent.
     * @param addressList   List of addresses.
     * @param attachedFiles List of attachments. Pass empty list if none.
     * @param subID         Note that here subID is of type int and not long because klinker library requires it as int
     * I don't really know the exact reason why they implemented it as int instead of long
     */
    fun sendMessage(
        context: Context,
        textMessage: String?,
        attachedFiles: List<SMSHelper.Attachment>?,
        addressList: MutableList<SMSHelper.Address>,
        subID: Int,
        tempId: Long,
        longTextAsMms: Boolean = false,
        groupMessageAsMms: Boolean = true,
        convertToMmsAfter: Int = 3,
    ) {
        val sendingPhoneNumber: LocalPhoneNumber = getSendingPhoneNumber(context, subID)
        if (addressList.size > 1) {
            // Remove the user's phone number if present in the list of recipients
            addressList.removeIf { address -> sendingPhoneNumber.isMatchingPhoneNumber(address.getAddress()) }
        }

        try {
            val settings = getTransactionSettings(
                context,
                subID,
                longTextAsMms,
                groupMessageAsMms,
                convertToMmsAfter,
            )

            val transaction = Transaction(context, settings)

            val addresses: Array<String> = addressList.map(SMSHelper.Address::getAddress).toTypedArray()
            val message = Message(textMessage, addresses)

            // If there are any attachment files add those into the message
            if (attachedFiles != null) {
                for (attachedFile in attachedFiles) {
                    val file = Base64.decode(attachedFile.encodedFile, Base64.NO_WRAP)
                    val mimeType = attachedFile.mimeType
                    val fileName = attachedFile.uniqueIdentifier
                    message.addMedia(file, mimeType, fileName)
                }
            }

            message.fromAddress = sendingPhoneNumber.number
            message.save = true

            // Sending MMS on android requires the app to be set as the default SMS app,
            // but sending SMS doesn't needs the app to be set as the default app.
            // This is the reason why there are separate branch handling for SMS and MMS.
            if (transaction.checkMMS(message)) {
                Log.v(SENDING_MESSAGE, "Sending new MMS")
                sendMmsMessageNative(context, message, settings, tempId)
            } else {
                Log.v(SENDING_MESSAGE, "Sending new SMS")
                sendSmsWithSentIntent(context, message, settings, tempId)
            }
        } catch (e: Exception) {
            Log.e(SENDING_MESSAGE, "Exception", e)
        }
    }

    /**
     * Resolve a concrete subscription: background sends on multi-SIM devices without a default
     * SMS SIM fail instead of showing the disambiguation dialog.
     */
    private fun smsManagerFor(context: Context, subscriptionId: Int): SmsManager {
        val subId = subscriptionId.takeIf { it > 0 }
            ?: SubscriptionManager.getDefaultSmsSubscriptionId()
                .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId
            ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    private fun sendSmsWithSentIntent(
        context: Context,
        message: Message,
        settings: Settings,
        tempId: Long,
    ) {
        val smsManager = smsManagerFor(context, settings.subscriptionId)
        val parts = smsManager.divideMessage(message.text.orEmpty())
        val destination = message.addresses.firstOrNull()
        if (destination.isNullOrBlank()) {
            Log.e(SENDING_MESSAGE, "No destination address for SMS")
            return
        }
        beginTempIdMap()
        if (parts.size <= 1) {
            smsManager.sendTextMessage(
                destination, null, message.text,
                createSmsSentIntent(context, tempId), null,
            )
        } else {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            repeat(parts.size) { sentIntents.add(createSmsSentIntent(context, tempId)) }
            smsManager.sendMultipartTextMessage(destination, null, parts, sentIntents, null)
        }
    }

    private fun createSmsSentIntent(
        context: Context,
        tempId: Long,
    ): PendingIntent {
        val requestCode = sentIntentRequestCode.incrementAndGet()
        val intent = Intent(context, SmsSentReceiver::class.java)
            .setAction(SmsSentReceiver.ACTION)
            .putExtra(EXTRA_TEMP_ID, tempId)
        var flags = PendingIntent.FLAG_ONE_SHOT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /**
     * Send an MMS message using SmsManager.sendMultimediaMessage
     *
     * @param context
     * @param message
     * @param klinkerSettings
     */
    internal fun sendMmsMessageNative(
        context: Context,
        message: Message,
        klinkerSettings: Settings,
        tempId: Long,
    ) {
        val data = ArrayList<MMSPart>()

        for (p in message.parts) {
            val part = MMSPart()
            if (p.name != null) {
                part.Name = p.name
            } else {
                part.Name = p.contentType.split("/").dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            }
            part.MimeType = p.contentType
            part.Data = p.media
            data.add(part)
        }

        if (message.text != null && message.text != "") {
            // add text to the end of the part and send
            val part = MMSPart()
            part.Name = "text"
            part.MimeType = "text/plain"
            part.Data = message.text.toByteArray()
            data.add(part)
        }

        val sendReq = buildPdu(context, message.fromAddress, message.addresses, message.subject, data, klinkerSettings)

        val configOverrides = Bundle()
        configOverrides.putBoolean(SmsManager.MMS_CONFIG_GROUP_MMS_ENABLED, klinkerSettings.group)

        // Write the PDUs to disk so that we can pass them to the SmsManager
        val fileName = "send.${abs(Random().nextLong().toDouble())}.dat"
        val mSendFile = File(context.cacheDir, fileName)

        val contentUri = (Uri.Builder())
            .authority("${context.packageName}.MmsFileProvider")
            .path(fileName)
            .scheme(ContentResolver.SCHEME_CONTENT)
            .build()

        try {
            FileOutputStream(mSendFile).use { writer ->
                writer.write(PduComposer(context, sendReq).make())
            }
        } catch (e: IOException) {
            Log.e(SENDING_MESSAGE, "Error while writing temporary PDU file: ", e)
        }

        val mSmsManager = smsManagerFor(context, klinkerSettings.subscriptionId)

        beginTempIdMap()
        mSmsManager.sendMultimediaMessage(
            context, contentUri, null, configOverrides,
            createSmsSentIntent(context, tempId),
        )
    }

    const val DEFAULT_EXPIRY_TIME: Long = (7 * 24 * 60 * 60).toLong()
    const val DEFAULT_PRIORITY: Int = PduHeaders.PRIORITY_NORMAL

    /**
     * Copy of the same-name method from https://github.com/klinker41/android-smsmms
     */
    private fun buildPdu(context: Context, fromAddress: String, recipients: Array<String>, subject: String?, parts: List<MMSPart>, settings: Settings): SendReq {
        val req = SendReq()
        // From, per spec
        req.prepareFromAddress(context, fromAddress, settings.subscriptionId)
        // To
        for (recipient in recipients) {
            req.addTo(EncodedStringValue(recipient))
        }
        // Subject
        if (!TextUtils.isEmpty(subject)) {
            req.subject = EncodedStringValue(subject)
        }
        // Date
        req.date = System.currentTimeMillis() / 1000
        // Body
        val body = PduBody()
        // Add text part. Always add a smil part for compatibility, without it there
        // may be issues on some carriers/client apps
        var size = 0
        for (i in parts.indices) {
            val part = parts[i]
            size += addTextPart(body, part, i)
        }

        // add a SMIL document for compatibility
        val out = ByteArrayOutputStream()
        SmilXmlSerializer.serialize(SmilHelper.createSmilDocument(body), out)
        val smilPart = PduPart()
        smilPart.contentId = "smil".toByteArray()
        smilPart.contentLocation = "smil.xml".toByteArray()
        smilPart.contentType = ContentType.APP_SMIL.toByteArray()
        smilPart.data = out.toByteArray()
        body.addPart(0, smilPart)

        req.body = body
        // Message size
        req.messageSize = size.toLong()
        // Message class
        req.messageClass = PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray()
        // Expiry
        req.expiry = DEFAULT_EXPIRY_TIME
        try {
            // Priority
            req.priority = DEFAULT_PRIORITY
            // Delivery report
            req.deliveryReport = PduHeaders.VALUE_NO
            // Read report
            req.readReport = PduHeaders.VALUE_NO
        } catch (_: InvalidHeaderValueException) { }

        return req
    }

    /**
     * Copy of the same-name method from https://github.com/klinker41/android-smsmms
     */
    private fun addTextPart(pb: PduBody, p: MMSPart, id: Int): Int {
        val filename = p.Name
        val part = PduPart()
        // Set Charset if it's a text media.
        if (p.MimeType.startsWith("text")) {
            part.charset = CharacterSets.UTF_8
        }
        // Set Content-Type.
        part.contentType = p.MimeType.toByteArray()
        // Set Content-Location.
        part.contentLocation = filename.toByteArray()
        val index = filename.lastIndexOf(".")
        val contentId = if (index == -1) filename else filename.substring(0, index)
        part.contentId = contentId.toByteArray()
        part.data = p.Data
        pb.addPart(part)

        return part.data.size
    }

    /**
     * Returns the Address of the sender of the MMS message.
     * @return sender's Address
     */
    fun getMmsFrom(context: Context, msg: MultimediaMessagePdu?): SMSHelper.Address? {
        if (msg == null) {
            return null
        }
        return SMSHelper.Address(context, msg.from.string)
    }

    /**
     * returns a List of Addresses of all the recipients of a MMS message.
     * @return List of Addresses of all recipients of an MMS message
     */
    fun getMmsTo(context: Context, msg: MultimediaMessagePdu?): List<SMSHelper.Address>? {
        if (msg == null) {
            return null
        }

        val toBuilder = StringBuilder()

        val to = msg.to
        if (to != null) {
            toBuilder.append(EncodedStringValue.concat(to))
        }

        if (msg is RetrieveConf) {
            val cc = msg.cc
            if (cc != null && cc.isNotEmpty()) {
                toBuilder.append(";")
                toBuilder.append(EncodedStringValue.concat(cc))
            }
        }

        val built = toBuilder.toString().replace(";", ", ").removePrefix(", ")

        return stripDuplicatePhoneNumbers(context, built)
    }

    /**
     * Removes duplicate addresses from the string and returns List of Addresses
     */
    fun stripDuplicatePhoneNumbers(context: Context, phoneNumbers: String): List<SMSHelper.Address> {
        val numbers = phoneNumbers.split(", ").dropLastWhile { it.isEmpty() }.toTypedArray()

        val uniqueNumbers = mutableListOf<SMSHelper.Address>()

        for (number in numbers) {
            val duplicate = uniqueNumbers.any { uniqueNumber -> uniqueNumber.getAddress() == number.trim { it <= ' ' } }
            if (!duplicate) {
                uniqueNumbers.add(SMSHelper.Address(context, number.trim { it <= ' ' }))
            }
        }

        return uniqueNumbers
    }

    /**
     * Downscaled JPEG thumbnail, aspect ratio preserved.
     */
    fun encodeThumbnail(bitmap: Bitmap): String? {
        val longest = maxOf(bitmap.width, bitmap.height)
        val toEncode = if (longest > THUMBNAIL_MAX_EDGE) {
            val scale = THUMBNAIL_MAX_EDGE.toFloat() / longest
            bitmap.scale(
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
            )
        } else {
            bitmap
        }
        return try {
            val outputStream = ByteArrayOutputStream()
            if (!toEncode.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)) {
                return null
            }
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (toEncode !== bitmap) {
                toEncode.recycle()
            }
        }
    }

    /**
     * Reads the image files attached with an MMS from MMS database
     * @param context Context in which the method is called
     * @param id part ID of the image file attached with an MMS message
     * @return Returns the image as a bitmap
     */
    fun getMmsImage(context: Context, id: Long): Bitmap? {
        val partURI = ContentUris.withAppendedId(SMSHelper.mMSPartUri, id)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(partURI)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, bounds)
            }
        } catch (e: IOException) {
            Log.e("SmsMmsUtils", "Exception", e)
            return null
        }

        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        if (longest > THUMBNAIL_MAX_EDGE) {
            while (longest / (sample * 2) >= THUMBNAIL_MAX_EDGE) {
                sample *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        return try {
            context.contentResolver.openInputStream(partURI)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (e: IOException) {
            Log.e("SmsMmsUtils", "Exception", e)
            null
        }
    }

    fun getMmsPartInfo(context: Context, partId: Long): Pair<String, String>? {
        val columns = arrayOf(
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part.FILENAME,
            Telephony.Mms.Part.NAME,
            Telephony.Mms.Part.CONTENT_LOCATION,
            Telephony.Mms.Part._DATA,
        )
        try {
            context.contentResolver.query(
                SMSHelper.mMSPartUri,
                columns,
                "${Telephony.Mms.Part._ID} = ?",
                arrayOf(partId.toString()),
                null,
            ).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_TYPE))
                    ?: return null
                val fileName = resolveMmsPartFileName(
                    partId = partId,
                    fileName = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.FILENAME)),
                    name = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.NAME)),
                    contentLocation = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part.CONTENT_LOCATION)),
                    dataPath = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.Part._DATA)),
                )
                return mimeType to fileName
            }
        } catch (e: Exception) {
            Log.e("SmsMmsUtils", "Failed to read MMS part info", e)
            return null
        }
    }

    /**
     * Best-effort original filename from MMS part metadata.
     * Carriers often put the real name in [contentLocation]; PDU FILENAME/NAME may be empty.
     */
    fun resolveMmsPartFileName(
        partId: Long,
        fileName: String?,
        name: String?,
        contentLocation: String?,
        dataPath: String?,
    ): String {
        sequenceOf(
            contentLocation?.substringAfterLast('/')?.substringAfterLast('\\'),
            fileName,
            name,
            dataPath?.substringAfterLast('/')
                ?.takeUnless { it.startsWith("PART_", ignoreCase = true) },
        ).firstNotNullOfOrNull { raw ->
            raw?.trim()
                ?.removeSurrounding("\"")
                ?.takeIf { it.isNotEmpty() }
        }?.let { return it }

        return "attachment_$partId"
    }

    /**
     * This method loads the byteArray of attachment file stored in the MMS database
     * @param context Context in which the method is called
     * @param id part ID of the particular multimedia attachment file of MMS
     * @return returns the byteArray of the attachment
     */
    fun loadAttachment(context: Context, id: Long): ByteArray {
        val partURI = ContentUris.withAppendedId(SMSHelper.mMSPartUri, id)
        var byteArray = ByteArray(0)

        // Open inputStream from the specified URI
        try {
            context.contentResolver.openInputStream(partURI).use { inputStream ->
                // Try read from the InputStream
                if (inputStream != null) {
                    byteArray = IOUtils.toByteArray(inputStream)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return byteArray
    }

    /**
     * Marks a conversation as read in the database.
     *
     * @param context the context to get the content provider with.
     * @param recipients the phone numbers to find the conversation with.
     */
    fun markConversationRead(context: Context, recipients: HashSet<String?>) {
        thread {
            try {
                val threadId = Utils.getOrCreateThreadId(context, recipients)
                markAsRead(context, ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), threadId)
            } catch (e: Exception) {
                // the conversation doesn't exist
                e.printStackTrace()
            }
        }.start()
    }

    private fun markAsRead(context: Context?, uri: Uri?, threadId: Long) {
        Log.v("SmsFeature", "marking thread with threadId $threadId as read at Uri$uri")

        if (uri != null && context != null) {
            val values = ContentValues(2)
            values.put("read", 1)
            values.put("seen", 1)

            context.contentResolver.update(uri, values, "(read=0 OR seen=0)", null)
        }
    }


    fun SMSHelper.Message.toTextMessage(tempId: Long? = null): TextMessage {
        val addresses = this.addresses.map { it.getAddress() }

        return TextMessage(
            addresses = addresses,
            body = this.body,
            timestamp = this.date,
            messageType = this.type,
            read = this.read == 1,
            uniqueId = this.uID,
            subscriptionId = this.subscriptionID,
            attachments = this.attachments?.map {
                SmsAttachment(
                    fileName = it.uniqueIdentifier,
                    mimeType = it.mimeType,
                    thumbnail = it.encodedFile,
                    partId = it.partID,
                )
            },
            tempId = tempId,
        )
    }

    /** Base64 preview for an image or video MMS part; null when none can be generated. */
    fun createThumbnail(context: Context, partId: Long, contentType: String): String? = try {
        val bitmap = when {
            // Get the actual image from the mms database convert it into thumbnail and encode to Base64
            MimeType.isTypeImage(contentType) -> getMmsImage(context, partId)
            MimeType.isTypeVideo(contentType) -> {
                // Can't use try-with-resources since MediaMetadataRetriever's close method was only added in API 29
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, ContentUris.withAppendedId(SMSHelper.mMSPartUri, partId))
                    retriever.frameAtTime
                } finally { retriever.release() }
            }
            else -> null
        }
        bitmap?.let { try { encodeThumbnail(it) } finally { it.recycle() } }
    } catch (e: Exception) {
        Log.w("SmsFeature", "Could not generate preview for part $partId", e)
        null
    }

    fun beginTempIdMap() {
        pendingTempIdMap?.complete(Unit)
        pendingTempIdMap = CompletableDeferred()
    }

    fun completeTempIdMap(tempId: Long, uniqueId: Long) {
        tempIdByUniqueId[uniqueId] = tempId
        pendingTempIdMap?.complete(Unit)
    }

    fun failTempIdMap() {
        pendingTempIdMap?.complete(Unit)
    }

    fun takeTempId(uniqueId: Long): Long? = tempIdByUniqueId.remove(uniqueId)

    suspend fun awaitPendingTempIdMaps() {
        val deferred = pendingTempIdMap ?: return
        if (deferred.isCompleted) return
        withTimeoutOrNull(TEMP_ID_AWAIT_TIMEOUT_MS.milliseconds) { deferred.await() }
    }
}
