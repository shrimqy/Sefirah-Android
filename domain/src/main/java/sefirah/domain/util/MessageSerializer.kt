package sefirah.domain.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sefirah.domain.model.*

object MessageSerializer {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(SocketMessage::class) {
                subclass(ActionInfo::class)
                subclass(ActionList::class)
                subclass(ApplicationInfo::class)
                subclass(ApplicationList::class)
                subclass(Authentication::class)
                subclass(AudioAction::class)
                subclass(AudioDeviceInfo::class)
                subclass(AudioStreamState::class)
                subclass(BatteryState::class)
                subclass(BluetoothState::class)
                subclass(BluetoothPairingResult::class)
                subclass(BluetoothPairingRequest::class)
                subclass(CallInfo::class)
                subclass(CallLogInfo::class)
                subclass(ClearNotifications::class)
                subclass(ClipboardInfo::class)
                subclass(ClipboardTransfer::class)
                subclass(ContactInfo::class)
                subclass(ConversationInfo::class)
                subclass(ConversationsRequest::class)
                subclass(DeviceInfo::class)
                subclass(Disconnect::class)
                subclass(DndState::class)
                subclass(MediaAction::class)
                subclass(MessageIndex::class)
                subclass(MessageList::class)
                subclass(MessagesRequest::class)
                subclass(NotificationAction::class)
                subclass(NotificationInfo::class)
                subclass(NotificationReply::class)
                subclass(PairMessage::class)
                subclass(PlaybackInfo::class)
                subclass(PlaySound::class)
                subclass(RemoveConversation::class)
                subclass(RequestApplicationList::class)
                subclass(RequestWorkerLaunch::class)
                subclass(RingerModeState::class)
                subclass(SftpServerInfo::class)
                subclass(ShareTransfer::class)
                subclass(SmsAttachmentRequest::class)
                subclass(SmsAttachmentTransfer::class)
                subclass(TextMessage::class)
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
