package sefirah.network.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sefirah.domain.model.ApplicationInfo
import sefirah.domain.model.BulkFileTransfer
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceStatus
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.CommandMessage
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.SftpServerInfo
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextConversation
import sefirah.domain.model.UdpBroadcast
import sefirah.domain.model.TextMessage
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.PlaybackSession
import javax.inject.Inject

class MessageSerializer @Inject constructor() {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(SocketMessage::class) {
                subclass(ClipboardMessage::class)
                subclass(NotificationMessage::class)
                subclass(FileTransfer::class)
                subclass(BulkFileTransfer::class)
                subclass(DeviceInfo::class)
                subclass(DeviceStatus::class)
                subclass(CommandMessage::class)
                subclass(NotificationAction::class)
                subclass(ReplyAction::class)
                subclass(PlaybackSession::class)
                subclass(ApplicationInfo::class)
                subclass(SftpServerInfo::class)
                subclass(UdpBroadcast::class)
                subclass(TextMessage::class)
                subclass(TextConversation::class)
                subclass(AudioDevice::class)
            }
        }
        isLenient = true
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