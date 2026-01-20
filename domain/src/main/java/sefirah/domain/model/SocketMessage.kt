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
@SerialName("0")
data class AuthenticationMessage(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val nonce: String,
    val proof: String,
    val model: String
) : SocketMessage()

@Serializable
@SerialName("1")
data class PairMessage(
    val pair: Boolean,
) : SocketMessage()

@Serializable
@SerialName("2")
data class UdpBroadcast(
    val deviceId: String,
    val deviceName: String,
    val port: Int,
    val publicKey: String,
) : SocketMessage()

@Serializable
@SerialName("3")
@Parcelize
data class DeviceInfo(
    val deviceName: String,
    val avatar: String? = null,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
) : SocketMessage(), Parcelable

@Serializable
@SerialName("4")
data class BatteryStatus(
    val batteryLevel: Int,
    val isCharging: Boolean
) : SocketMessage()

@Serializable
@SerialName("5")
data class RingerMode(
    val mode: Int
) : SocketMessage()

@Serializable
@SerialName("6")
data class DndStatus(
    val isEnabled: Boolean
) : SocketMessage()

@Serializable
@SerialName("7")
data class AudioStreamMessage(
    val streamType: Int,
    val level: Int,
    val maxLevel: Int
) : SocketMessage()

@Serializable
@SerialName("8")
@Parcelize
data class AudioDevice(
    val audioDeviceType: AudioMessageType,
    val deviceId: String,
    var isSelected: Boolean,
    val deviceName: String,
    var volume: Float,
    var isMuted: Boolean,
) : SocketMessage(), Parcelable

@Serializable
@SerialName("9")
data class CommandMessage(
    val commandType: CommandType
) : SocketMessage()

@Serializable
@SerialName("10")
data class TextMessage(
    val addresses: List<String>,
    val body: String,
    val timestamp: Long = 0,
    val messageType: Int = 0,
    val read: Boolean = false,
    val threadId: Long? = null,
    val uniqueId: Long = 0,
    val subscriptionId: Int = 0,
    val attachments: List<SmsAttachment>? = null,
    val isTextMessage: Boolean = false,
    val hasMultipleRecipients: Boolean = false
) : SocketMessage()

@Serializable
@SerialName("11")
data class TextConversation(
    val conversationType: ConversationType,
    val threadId: Long,
    val recipients: List<String> = emptyList(),
    val messages: List<TextMessage> = emptyList()
) : SocketMessage()

@Serializable
@SerialName("12")
data class ThreadRequest(
    val threadId: Long,
    val rangeStartTimestamp: Long = -1,
    val numberToRequest: Long = -1
) : SocketMessage()

@Serializable
@SerialName("13")
data class ContactMessage(
    val id: String,
    val lookupKey: String,
    val number: String,
    val displayName: String,
    val photoBase64: String?,
) : SocketMessage()

@Serializable
@SerialName("14")
data class NotificationMessage(
    val notificationKey: String,
    val notificationType: NotificationType,
    val timestamp: String? = null,
    val appPackage: String? = null,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val messages: List<Message> = emptyList(),
    val groupKey: String? = null,
    val tag: String? = null,
    val appIcon: String? = null,
    val largeIcon: String? = null,
    val bigPicture: String? = null,
    val replyResultKey: String? = null,
    val actions: List<NotificationAction> = emptyList(),
) : SocketMessage()

@Serializable
@SerialName("15")
@Parcelize
data class NotificationAction(
    val notificationKey: String,
    val label: String? = null,
    val actionIndex: Int,
    val isReplyAction: Boolean
) : SocketMessage(), Parcelable

@Serializable
@SerialName("16")
@Parcelize
data class ReplyAction(
    val notificationKey: String,
    val replyResultKey: String,
    val replyText: String
) : SocketMessage(), Parcelable

@Serializable
@Parcelize
@SerialName("17")
data class FileTransferMessage(
    val serverInfo: ServerInfo,
    val files: List<FileMetadata>,
    val isClipboard: Boolean = false
) : Parcelable, SocketMessage()

@Serializable
@SerialName("18")
data class SftpServerInfo (
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
) : SocketMessage()

@Serializable
@SerialName("19")
data class ClipboardMessage(
    val clipboardType: String,
    val content: String,
) : SocketMessage()

@Serializable
@SerialName("20")
@Parcelize
data class PlaybackSession(
    var sessionType: SessionType,
    val source: String? = null,
    val trackTitle: String? = null,
    val artist: String? = null,
    var isPlaying: Boolean = false,
    var playbackRate: Double? = 0.0,
    var isShuffle: Boolean = false,
    var position: Double = 0.0,
    val maxSeekTime: Double = 0.0,
    val minSeekTime: Double? = null,
    val thumbnail: String? = null,
    val appIcon: String? = null,
) : SocketMessage(), Parcelable

@Serializable
@SerialName("21")
data class PlaybackAction(
    val playbackActionType: PlaybackActionType,
    val source: String? = null,
    val value: Double? = null
) : SocketMessage()

@Serializable
@SerialName("22")
data class ApplicationList(
    val appList: List<ApplicationInfo>
) : SocketMessage()

@Serializable
@SerialName("23")
data class ActionMessage(
    val actionId: String,
    val actionName: String,
) : SocketMessage()

@Serializable
data class Message(
    val sender: String,
    val text: String
)

@Parcelize
@Serializable
data class ServerInfo(
    val port: Int,
    var password: String
) : Parcelable

@Serializable
@Parcelize
data class FileMetadata(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
) : Parcelable

@Serializable
data class ApplicationInfo(
    val packageName: String,
    val appName: String,
    val appIcon: String?
) : SocketMessage()

@Serializable
data class SmsAttachment(
    val mimeType: String,
    val base64EncodedFile: String? = null,
    val fileName: String
)

@Parcelize
@Serializable
data class PhoneNumber(
    val number: String,
    val subscriptionId: Int
) : Parcelable
