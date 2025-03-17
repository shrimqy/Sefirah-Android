package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.Serializable

enum class ClipboardType {
    Image,
    Text
}

enum class NotificationType {
    Active,
    New,
    Removed,
    Action,
    Invoke
}

enum class ConversationType {
    Active,
    ActiveUpdated,
    New,
    Removed,
}

enum class MediaAction {
    Resume,
    Pause,
    NextQueue,
    PrevQueue,
    Seek,
    Volume
}

enum class MiscType {
    Disconnect,
    Lock,
    Shutdown,
    Sleep,
    Hibernate,
    ClearNotifications,
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class SocketMessage

@Serializable
@SerialName("0")
data class Misc(
    val miscType: MiscType
) : SocketMessage()

@Serializable
@SerialName("1")
@Parcelize
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val avatar: String? = null,
    val publicKey: String? = null,
    val nonce: String? = null,
    val proof: String? = null,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
) : SocketMessage(), Parcelable

@Serializable
@SerialName("2")
data class DeviceStatus(
    val batteryStatus: Int? = null,
    val chargingStatus: Boolean? = null,
    val wifiStatus: Boolean? = null,
    val bluetoothStatus: Boolean? = null,
    val isDndEnabled: Boolean? = null,
    val ringerMode: Int? = null
) : SocketMessage()

@Serializable
@SerialName("3")
data class ClipboardMessage(
    val clipboardType: String,
    val content: String,
) : SocketMessage()

@Serializable
@SerialName("4")
data class NotificationMessage(
    val notificationKey: String,
    val notificationType: NotificationType,
    val timestamp: String? = null,
    val appPackage: String?,
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
@SerialName("5")
@Parcelize
data class NotificationAction(
    val notificationKey: String,
    val label: String,
    val actionIndex: Int,
    val isReplyAction: Boolean
) : SocketMessage(), Parcelable

@Serializable
@SerialName("6")
@Parcelize
data class ReplyAction(
    val notificationKey: String,
    val replyResultKey: String,
    val replyText: String
) : SocketMessage(), Parcelable

@Serializable
data class Message(
    val sender: String,
    val text: String
)

@Serializable
@SerialName("7")
@Parcelize
data class PlaybackData(
    val appName: String? = null,
    val trackTitle: String? = null,
    val artist: String? = null,
    var volume: Float? = null,
    var isPlaying: Boolean? = null,
    val position: Long? = null,
    val maxSeekTime: Long? = null,
    val minSeekTime: Long? = null,
    var mediaAction: MediaAction? = null,
    val thumbnail: String? = null,
    val appIcon: String? = null,
) : SocketMessage(), Parcelable

@Serializable
@Parcelize
@SerialName("8")
data class FileTransfer(
    val serverInfo: ServerInfo,
    val fileMetadata: FileMetadata
) : Parcelable, SocketMessage()

@Serializable
@Parcelize
@SerialName("9")
data class BulkFileTransfer(
    val serverInfo: ServerInfo,
    val files: List<FileMetadata>
) : Parcelable, SocketMessage()

@Parcelize
@Serializable
data class ServerInfo(
    val ipAddress: String,
    val port: Int,
    var password: String
) : Parcelable

@Serializable
@Parcelize
data class FileMetadata(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val uri: String? = null,
) : Parcelable

//@Serializable
//@SerialName("9")
//data class InteractiveControlMessage(
//    val control: InteractiveControl
//) : SocketMessage()

@Serializable
@SerialName("10")
data class ApplicationInfo(
    val packageName: String,
    val appName: String,
    val appIcon: String?
) : SocketMessage()

@Serializable
@SerialName("11")
data class SftpServerInfo (
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
) : SocketMessage()

@Serializable
@SerialName("12")
data class UdpBroadcast(
    val deviceId: String,
    val deviceName: String,
    val ipAddresses: List<String> = emptyList(),
    val port: Int? = null,
    val publicKey: String,
    var timestamp: Long?
) : SocketMessage()

@Serializable
@SerialName("13")
data class DeviceRingerMode(
    val ringerMode: Int
) : SocketMessage()

@Serializable
@SerialName("14")
data class TextMessage(
    val addresses: List<SmsAddress>,
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
@SerialName("15")
data class TextConversation(
    val conversationType: ConversationType,
    val threadId: Long,
    val messages: List<TextMessage> = emptyList()
) : SocketMessage()

@Serializable
@SerialName("16")
data class ThreadRequest(
    val threadId: Long,
    val rangeStartTimestamp: Long = -1,
    val numberToRequest: Long = -1
) : SocketMessage()

@Serializable
data class SmsAddress(
    val address: String
)

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
