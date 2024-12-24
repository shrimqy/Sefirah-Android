package sefirah.network.extensions

import sefirah.database.model.toEntity
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.SocketMessage
import sefirah.network.NetworkService

suspend fun NetworkService.handleMessage(message: SocketMessage) {
    when (message) {
        is DeviceInfo -> handleDeviceInfo(message)
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