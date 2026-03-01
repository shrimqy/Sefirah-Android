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
@SerialName("ConnectionAck")
object ConnectionAck : SocketMessage()

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
@SerialName("DndState")
data class DndState(
    val isEnabled: Boolean
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
    val infoType: ConversationInfoType,
    val threadId: Long,
    val recipients: List<String> = emptyList(),
    val messages: List<TextMessage> = emptyList()
) : SocketMessage()

@Serializable
@SerialName("TextMessage")
data class TextMessage(
    val uniqueId: Long = 0,
    val addresses: List<String>,
    val threadId: Long,
    val body: String,
    val timestamp: Long = 0,
    val messageType: Int = 0,
    val read: Boolean = false,
    val subscriptionId: Int = 0,
    val attachments: List<SmsAttachment>? = null,
    val isTextMessage: Boolean = false,
    val hasMultipleRecipients: Boolean = false
) : SocketMessage()

@Serializable
data class SmsAttachment(
    val id: String,
    val mimeType: String,
    val base64EncodedFile: String? = null
)

@Serializable
@SerialName("ThreadRequest")
data class ThreadRequest(
    val threadId: Long,
    val rangeStartTimestamp: Long = -1,
    val numberToRequest: Long = -1
) : SocketMessage()

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
    val timestamp: String? = null,
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
@SerialName("FileTransferInfo")
data class FileTransferInfo(
    val files: List<FileMetadata>,
    val serverInfo: ServerInfo,
    val isClipboard: Boolean = false
) : Parcelable, SocketMessage()

@Serializable
@SerialName("SftpServerInfo")
data class SftpServerInfo(
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
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
) : Parcelable

@Parcelize
@Serializable
data class PhoneNumber(
    val number: String,
    val subscriptionId: Int
) : Parcelable
