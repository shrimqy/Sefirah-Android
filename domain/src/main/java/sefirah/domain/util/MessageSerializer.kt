package sefirah.domain.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sefirah.domain.model.ActionInfo
import sefirah.domain.model.ApplicationInfo
import sefirah.domain.model.ApplicationList
import sefirah.domain.model.Authentication
import sefirah.domain.model.AudioDeviceInfo
import sefirah.domain.model.AudioStreamState
import sefirah.domain.model.BatteryState
import sefirah.domain.model.ClearNotifications
import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ConnectionAck
import sefirah.domain.model.ContactInfo
import sefirah.domain.model.ConversationInfo
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.Disconnect
import sefirah.domain.model.DndState
import sefirah.domain.model.FileTransferInfo
import sefirah.domain.model.MediaAction
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationInfo
import sefirah.domain.model.NotificationReply
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.RequestApplicationList
import sefirah.domain.model.RingerModeState
import sefirah.domain.model.SftpServerInfo
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.domain.model.UdpBroadcast

object MessageSerializer {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(SocketMessage::class) {
                // Same order as Desktop JsonDerivedType
                subclass(ActionInfo::class)
                subclass(ApplicationInfo::class)
                subclass(ApplicationList::class)
                subclass(Authentication::class)
                subclass(AudioDeviceInfo::class)
                subclass(AudioStreamState::class)
                subclass(BatteryState::class)
                subclass(ClearNotifications::class)
                subclass(ClipboardInfo::class)
                subclass(ConnectionAck::class)
                subclass(ContactInfo::class)
                subclass(ConversationInfo::class)
                subclass(DeviceInfo::class)
                subclass(Disconnect::class)
                subclass(DndState::class)
                subclass(FileTransferInfo::class)
                subclass(MediaAction::class)
                subclass(NotificationAction::class)
                subclass(NotificationInfo::class)
                subclass(NotificationReply::class)
                subclass(PairMessage::class)
                subclass(PlaybackInfo::class)
                subclass(RequestApplicationList::class)
                subclass(RingerModeState::class)
                subclass(SftpServerInfo::class)
                subclass(TextMessage::class)
                subclass(ThreadRequest::class)
                subclass(UdpBroadcast::class)
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
