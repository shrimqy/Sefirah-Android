package sefirah.network.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.SocketMessage
import javax.inject.Inject

class MessageSerializer @Inject constructor() {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(SocketMessage::class) {
                subclass(ClipboardMessage::class)
                subclass(NotificationMessage::class)
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