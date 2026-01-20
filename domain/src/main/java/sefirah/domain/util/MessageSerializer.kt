package sefirah.domain.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sefirah.domain.model.ActionMessage
import sefirah.domain.model.ApplicationInfo
import sefirah.domain.model.ApplicationList
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.AudioStreamMessage
import sefirah.domain.model.AuthenticationMessage
import sefirah.domain.model.BatteryStatus
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.CommandMessage
import sefirah.domain.model.ContactMessage
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DndStatus
import sefirah.domain.model.FileTransferMessage
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PlaybackAction
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.RingerMode
import sefirah.domain.model.SftpServerInfo
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextConversation
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.domain.model.UdpBroadcast

object MessageSerializer {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(SocketMessage::class) {
                subclass(AuthenticationMessage::class)
                subclass(PairMessage::class)
                subclass(UdpBroadcast::class)
                subclass(DeviceInfo::class)
                subclass(BatteryStatus::class)
                subclass(RingerMode::class)
                subclass(DndStatus::class)
                subclass(AudioDevice::class)
                subclass(AudioStreamMessage::class)
                subclass(CommandMessage::class)
                subclass(TextMessage::class)
                subclass(TextConversation::class)
                subclass(ThreadRequest::class)
                subclass(ContactMessage::class)
                subclass(NotificationMessage::class)
                subclass(NotificationAction::class)
                subclass(ReplyAction::class)
                subclass(FileTransferMessage::class)
                subclass(SftpServerInfo::class)
                subclass(ClipboardMessage::class)
                subclass(PlaybackSession::class)
                subclass(PlaybackAction::class)
                subclass(ApplicationList::class)
                subclass(ApplicationInfo::class)
                subclass(ActionMessage::class)
            }
        }
        isLenient = true
        decodeEnumsCaseInsensitive = true
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun serialize(message: SocketMessage): String? {
        return runCatching {
             json.encodeToString(SocketMessage.serializer(), message)
        }.getOrNull()
    }

    fun deserialize(jsonString: String): SocketMessage? {
        return runCatching {
            json.decodeFromString<SocketMessage>(jsonString)
        }.getOrNull()
    }
}