package sefirah.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.Serializable

enum class NotificationType {
    ACTIVE,
    NEW,
    REMOVED,
    ACTION
}
enum class MediaAction {
    RESUME,
    PAUSE,
    NEXT_QUEUE,
    PREV_QUEUE,
    SEEK,
    VOLUME
}

enum class CommandType {
    LOCK,
    SHUTDOWN,
    SLEEP,
    HIBERNATE,
    MIRROR,
    CLOSE_MIRROR,
    CLEAR_NOTIFICATIONS
}

enum class DataTransferType {
    METADATA, CHUNK
}

@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
@Serializable
sealed class SocketMessage

@Serializable
@SerialName("0")
data class Response(
    val resType: String,
    val content: String,
) : SocketMessage()

@Serializable
@SerialName("1")
data class ClipboardMessage(
    val content: String,
) : SocketMessage()

@Serializable
@SerialName("2")
data class NotificationMessage(
    val notificationKey: String,
    val notificationType: NotificationType,
    val timestamp: String? = null,
    val appPackage: String?,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val messages: List<Message>? = emptyList(),
    val groupKey: String? = null,
    val tag: String?,
    val appIcon: String? = null,
    val largeIcon: String? = null,
    val bigPicture: String? = null,
    val replyResultKey: String? = null,
    val actions: List<NotificationAction?> = emptyList(),
) : SocketMessage()

@Serializable
@Parcelize
@SerialName("13")
data class NotificationAction(
    val notificationKey: String,
    val label: String,
    val actionIndex: Int,
    val isReplyAction: Boolean
) : SocketMessage(), Parcelable

@Serializable
@Parcelize
@SerialName("14")
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

@Parcelize
@Serializable
@SerialName("3")
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val userAvatar: String? = null,
    val publicKey: String? = null,
    var hashedSecret: String? = null,
) : SocketMessage(), Parcelable

@Serializable
@SerialName("4")
data class DeviceStatus(
    val batteryStatus: Int?,
    val chargingStatus: Boolean?,
    val wifiStatus: Boolean?,
    val bluetoothStatus: Boolean?,
) : SocketMessage()

@Parcelize
@Serializable
@SerialName("5")
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
@SerialName("6")
data class Command(
    val commandType: CommandType
) : SocketMessage()

@Serializable
@SerialName("7")
data class FileTransfer(
    val dataTransferType: DataTransferType,
    val metadata: FileMetadata? = null,
    val chunkSize: Int? = null,
) : SocketMessage()

@Serializable
@SerialName("8")
data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
) : SocketMessage()

@Serializable
@SerialName("9")
data class DirectoryInfo(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: String?
) : SocketMessage()

@Serializable
@SerialName("10")
data class ScreenData(
    val timestamp: Long,
) : SocketMessage()

@Serializable
@SerialName("11")
data class InteractiveControlMessage(
    val control: InteractiveControl
) : SocketMessage()

@Serializable
@SerialName("12")
data class ApplicationInfo(
    val packageName: String,
    val appName: String,
    val appIcon: String?
) : SocketMessage()

@Serializable
sealed class InteractiveControl {
    @Serializable
    @SerialName("SINGLE")
    data class SingleTap(
        val x: Double,
        val y: Double,
        val frameWidth: Double,
        val frameHeight: Double
    ) : InteractiveControl()

    @Serializable
    @SerialName("HOLD")
    data class HoldTap(
        val x: Double,
        val y: Double,
        val frameWidth: Double,
        val frameHeight: Double
    ) : InteractiveControl()

    @Serializable
    @SerialName("KEYBOARD")
    data class KeyboardAction(
        val action: KeyboardActionType,
    ) : InteractiveControl()


    @Serializable
    @SerialName("KEY")
    data class KeyEvent(
        val key: String,
    ) : InteractiveControl()

    @Serializable
    @SerialName("SCROLL")
    data class ScrollEvent(
        val direction: ScrollDirection
    ) : InteractiveControl()

    @Serializable
    @SerialName("SWIPE")
    data class SwipeEvent(
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double,
        val willContinue: Boolean,
        val frameWidth: Double,
        val frameHeight: Double,
        val duration: Double,
    ) : InteractiveControl()
}

@Serializable
@SerialName("15")
data class SftpServerInfo (
    val username: String,
    val password: String,
    val ipAddress: String,
    val port: Int
) : SocketMessage()



enum class ScrollDirection {
    UP, DOWN
}

enum class KeyboardActionType {
    Tab, Backspace, Enter, Escape, CtrlC, CtrlV, CtrlX, CtrlA
}

@Parcelize
@Serializable
data class FileMetadata(
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val uri: String,
) : Parcelable
