package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class SocketMessage

@Serializable
@SerialName("Disconnect")
object Disconnect : SocketMessage()

@Serializable
@SerialName("ClearNotifications")
object ClearNotifications : SocketMessage()

@Serializable
@SerialName("RequestApplicationList")
object RequestApplicationList : SocketMessage()

@Serializable
@SerialName("RequestWorkerLaunch")
data class RequestWorkerLaunch(
    val command: String,
) : SocketMessage()

@Serializable
@SerialName("BluetoothPairingRequest")
object BluetoothPairingRequest : SocketMessage()

@Serializable
@SerialName("BluetoothPairingResult")
data class BluetoothPairingResult(
    val granted: Boolean,
    val deviceName: String? = null,
) : SocketMessage()

@Serializable
@SerialName("Authentication")
data class Authentication(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val model: String
) : SocketMessage()

@Serializable
@SerialName("PairMessage")
data class PairMessage(
    val pair: Boolean,
) : SocketMessage()

@Serializable
@SerialName("UdpBroadcast")
data class UdpBroadcast(
    val port: Int,
    val deviceId: String,
    val deviceName: String,
) : SocketMessage()

@Serializable
@SerialName("DeviceInfo")
@Parcelize
data class DeviceInfo(
    val deviceName: String,
    val avatar: String? = null,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
) : SocketMessage(), Parcelable

@Serializable
@SerialName("BatteryState")
data class BatteryState(
    val batteryLevel: Int,
    val isCharging: Boolean
) : SocketMessage()

@Serializable
@SerialName("BluetoothState")
data class BluetoothState(
    val isEnabled: Boolean
) : SocketMessage()

@Serializable
@SerialName("RingerModeState")
data class RingerModeState(
    val mode: Int
) : SocketMessage()

@Serializable
@SerialName("CallInfo")
data class CallInfo(
    val callState: CallState,
    val phoneNumber: String,
    val contactInfo: ContactInfo? = null
) : SocketMessage()

@Serializable
@SerialName("CallLogInfo")
data class CallLogInfo(
    val callLogId: Long,
    val phoneNumber: String,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val callType: CallLogType,
    val contactInfo: ContactInfo? = null,
) : SocketMessage()

@Serializable
@SerialName("DndState")
data class DndState(
    val isEnabled: Boolean
) : SocketMessage()

@Serializable
@SerialName("PlaySound")
data class PlaySound(
    val isPlaying: Boolean
) : SocketMessage()

@Serializable
@SerialName("AudioStreamState")
data class AudioStreamState(
    val streamType: Int,
    val level: Int,
) : SocketMessage()

@Serializable
@SerialName("AudioDeviceInfo")
@Parcelize
data class AudioDeviceInfo(
    val infoType: AudioInfoType,
    val deviceId: String,
    val deviceName: String,
    var volume: Float,
    var isMuted: Boolean,
    var isSelected: Boolean,
) : SocketMessage(), Parcelable

@Serializable
@SerialName("ConversationInfo")
data class ConversationInfo(
    val threadId: Long,
    val recipients: List<String> = emptyList(),
    val checksum: Long = 0,
    val message: TextMessage,
) : SocketMessage()

@Serializable
@SerialName("MessageList")
data class MessageList(
    val threadId: Long,
    val kind: MessageListKind,
    val messages: List<TextMessage> = emptyList(),
    /** Full thread inventory on Open only, newest first. */
    val messageIds: List<Long>? = null,
    /** Thread checksum after this change; New only. */
    val checksum: Long? = null,
) : SocketMessage()

@Serializable
@SerialName("MessageIndex")
data class MessageIndex(
    val threadId: Long,
    val messageIds: List<Long> = emptyList(),
    val checksum: Long = 0,
) : SocketMessage()

@Serializable
@SerialName("RemoveConversation")
data class RemoveConversation(
    val threadId: Long,
) : SocketMessage()

@Serializable
@SerialName("MessagesRequest")
data class MessagesRequest(
    val threadId: Long,
    /** Null = Open (tip page + index); set = History bodies for these ids. */
    val messageIds: List<Long>? = null,
) : SocketMessage()

@Serializable
@SerialName("TextMessage")
data class TextMessage(
    val uniqueId: Long = 0,
    val addresses: List<String>,
    val body: String,
    val timestamp: Long = 0,
    val messageType: Int = 0,
    val read: Boolean = false,
    val subscriptionId: Int = 0,
    val attachments: List<SmsAttachment>? = null,
    /** Desktop outbound uniqueId; set on tips after sent-intent maps Telephony _id. */
    val tempId: Long? = null,
) : SocketMessage()

@Serializable
data class SmsAttachment(
    val fileName: String,
    val mimeType: String,
    val thumbnail: String? = null,
    val partId: Long = -1,
    val base64EncodedFile: String? = null
)

@Serializable
@SerialName("SmsAttachmentRequest")
data class SmsAttachmentRequest(
    val partId: Long,
) : SocketMessage()

@Serializable
@SerialName("ConversationsRequest")
data class ConversationsRequest(
    val knownThreads: List<ThreadInfo> = emptyList(),
) : SocketMessage()

@Serializable
data class ThreadInfo(
    val threadId: Long,
    val checksum: Long = 0,
)

@Serializable
@SerialName("ContactInfo")
data class ContactInfo(
    val id: String,
    val lookupKey: String,
    val displayName: String,
    val number: String,
    val photoBase64: String? = null,
) : SocketMessage()

@Serializable
@SerialName("NotificationInfo")
data class NotificationInfo(
    val notificationKey: String,
    val infoType: NotificationInfoType,
    val timestampMillis: Long = 0,
    val appPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val messages: List<NotificationTextMessage> = emptyList(),
    val groupKey: String? = null,
    val tag: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    val replyResultKey: String? = null,
    val appIcon: String? = null,
    val largeIcon: String? = null,
) : SocketMessage()

@Serializable
data class NotificationTextMessage(
    val sender: String,
    val text: String
)

@Serializable
@SerialName("NotificationAction")
@Parcelize
data class NotificationAction(
    val notificationKey: String? = null,
    val label: String? = null,
    val actionIndex: Int
) : SocketMessage(), Parcelable

@Serializable
@SerialName("NotificationReply")
@Parcelize
data class NotificationReply(
    val notificationKey: String,
    val replyResultKey: String,
    val replyText: String
) : SocketMessage(), Parcelable

@Serializable
@Parcelize
data class FileTransferSession(
    val files: List<FileMetadata>,
    val serverInfo: ServerInfo,
) : Parcelable

@Serializable
@Parcelize
@SerialName("ShareTransfer")
data class ShareTransfer(
    val transfer: FileTransferSession,
) : Parcelable, SocketMessage()

@Serializable
@Parcelize
@SerialName("ClipboardTransfer")
data class ClipboardTransfer(
    val transfer: FileTransferSession,
) : Parcelable, SocketMessage()

@Serializable
@Parcelize
@SerialName("SmsAttachmentTransfer")
data class SmsAttachmentTransfer(
    val transfer: FileTransferSession,
    val partId: Long,
) : Parcelable, SocketMessage()

@Serializable
@SerialName("SftpServerInfo")
data class SftpServerInfo(
    val username: String,
    val password: String,
    val port: Int,
    val paths: List<String> = emptyList(),
    val pathNames: List<String> = emptyList()
) : SocketMessage()

@Serializable
@SerialName("ClipboardInfo")
data class ClipboardInfo(
    val clipboardType: String,
    val content: String,
) : SocketMessage()

@Serializable
@SerialName("PlaybackInfo")
@Parcelize
data class PlaybackInfo(
    var infoType: PlaybackInfoType,
    val source: String,
    val trackTitle: String? = null,
    val artist: String? = null,
    var isPlaying: Boolean = false,
    val isShuffleActive: Boolean? = null,
    val repeatMode: Int? = null,
    var playbackRate: Double? = 0.0,
    var position: Double = 0.0,
    val maxSeekTime: Double = 0.0,
    val minSeekTime: Double? = null,
    val thumbnail: String? = null,
    val appName: String? = null,
    val volume: Int = 0,
    val canPlay: Boolean? = null,
    val canPause: Boolean? = null,
    val canGoNext: Boolean? = null,
    val canGoPrevious: Boolean? = null,
    val canSeek: Boolean? = null,
) : SocketMessage(), Parcelable

@Serializable
@SerialName("MediaAction")
data class MediaAction(
    val actionType: MediaActionType,
    val source: String,
    val value: Double? = null
) : SocketMessage()

@Serializable
@SerialName("AudioAction")
data class AudioAction(
    val actionType: AudioActionType,
    val source: String,
    val value: Double? = null
) : SocketMessage()

@Serializable
@SerialName("ApplicationList")
data class ApplicationList(
    val appList: List<ApplicationInfo>
) : SocketMessage()

@Serializable
@SerialName("ApplicationInfo")
data class ApplicationInfo(
    val packageName: String,
    val appName: String,
    val appIcon: String? = null
) : SocketMessage()

@Serializable
@SerialName("ActionInfo")
data class ActionInfo(
    val actionId: String,
    val actionName: String,
    val icon: String? = null,
    val askForConfirmation: Boolean = false,
) : SocketMessage()

@Serializable
@SerialName("ActionList")
data class ActionList(
    val actions: List<ActionInfo> = emptyList(),
) : SocketMessage()

@Parcelize
@Serializable
data class ServerInfo(
    val port: Int
) : Parcelable

@Serializable
@Parcelize
data class FileMetadata(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
) : Parcelable {
    companion object {
        const val CLIPBOARD_FILE_NAME = "clipboard"
    }
}

@Parcelize
@Serializable
data class PhoneNumber(
    val number: String,
    val subscriptionId: Int,
    val index: Int = -1,
) : Parcelable
