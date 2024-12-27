package sefirah.network.extensions

import sefirah.database.model.toEntity
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.PlaybackData
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.SocketMessage
import sefirah.network.NetworkService

suspend fun NetworkService.handleMessage(message: SocketMessage) {
    when (message) {
        is NotificationMessage -> notificationHandler.removeNotification(message.notificationKey)
        is NotificationAction -> notificationHandler.performNotificationAction(message)
        is ReplyAction -> notificationHandler.performReplyAction(message)
        is DeviceInfo -> handleDeviceInfo(message)
        is PlaybackData -> handleMediaInfo(message)
        is ClipboardMessage -> clipboardHandler.setClipboard(message)
        else -> {

        }
    }
}

suspend fun NetworkService.handleDeviceInfo(deviceInfo: DeviceInfo) {
    appRepository.addDevice(
        RemoteDevice(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            avatar = deviceInfo.userAvatar,
            hashedSecret = connectedDevice.hashedSecret,
            port = connectedDevice.port,
            lastConnected = System.currentTimeMillis(),
            ipAddress = connectedDevice.ipAddress,
            publicKey = connectedDevice.publicKey
        ).toEntity())
}

suspend fun NetworkService.handleMediaInfo(playbackData: PlaybackData) {
    mediaHandler.updateMediaSession(playbackData)
}