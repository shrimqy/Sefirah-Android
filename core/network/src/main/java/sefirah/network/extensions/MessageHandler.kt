package sefirah.network.extensions

import android.app.NotificationManager
import android.util.Log
import kotlinx.coroutines.flow.first
import sefirah.domain.model.ActionMessage
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.AudioStreamMessage
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.CommandMessage
import sefirah.domain.model.CommandType
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.DndStatus
import sefirah.domain.model.FileTransferMessage
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.NotificationType
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.PlaybackSession
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.RingerMode
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.TAG
import sefirah.network.util.getInstalledApps

suspend fun NetworkService.handleMessage(device: BaseRemoteDevice, message: SocketMessage) {
    try {
        if (device is DiscoveredDevice) {
            when (message) {
                is PairMessage -> handlePairMessage(device, message)
                else -> {}
            }
            return
        }

        if (device is PairedDevice) {
            when (message) {
                is DeviceInfo -> handleDeviceInfo(message, device)
                is CommandMessage -> handleMisc(message, device)
                is NotificationMessage -> handleNotificationMessage(message)
                is NotificationAction -> notificationHandler.performNotificationAction(message)
                is ReplyAction -> notificationHandler.performReplyAction(message)
                is PlaybackSession -> handleMediaInfo(device.deviceId, message)
                is ClipboardMessage -> clipboardHandler.setClipboard(message)
                is FileTransferMessage ->  fileTransferService.receiveFiles(device.deviceId, message)
                is RingerMode -> handleRingerMode(message)
                is DndStatus -> handleDndStatus(message)
                is ThreadRequest -> smsHandler.handleThreadRequest(message)
                is TextMessage -> smsHandler.sendTextMessage(message)
                is AudioDevice -> mediaHandler.handleAudioDevice(device.deviceId, message)
                is AudioStreamMessage -> setStreamVolume(device, message)
                is ActionMessage -> actionHandler.addAction(device.deviceId, message)
                else -> {}
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error handling message for device ${device.deviceId}", e)
    }
}

private suspend fun NetworkService.handlePairMessage(device: DiscoveredDevice, message: PairMessage) {
    if (message.pair) {
        // Check if this is a response to our pairing request
        if (device.isPairing) {
            val pairedDevice = PairedDevice(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                avatar = null,
                lastConnected = System.currentTimeMillis(),
                addresses = device.addresses.map { AddressEntry(it) },
                address = device.address,
                publicKey = device.publicKey,
                connectionState = ConnectionState.Connected,
                port = device.port,
            )

            deviceManager.removeDiscoveredDevice(device.deviceId)
            deviceManager.addOrUpdatePairedDevice(pairedDevice)
            Log.d(TAG, "Created PairedDevice ${device.deviceId} after pairing approval")

            finalizeConnection(pairedDevice, true)
        } else {
            // Remote device is requesting to pair with us
            val pendingApproval = PendingDeviceApproval(
                device.deviceId,
                device.deviceName,
                device.verificationCode
            )
            emitPendingApproval(pendingApproval)
            showPairingVerificationNotification(pendingApproval)
        }
    } else {
        // Pairing rejected
        if (device.isPairing) {
            // They rejected our pairing request - update isPairing to false
            val updatedDevice = device.copy(isPairing = false)
            deviceManager.addOrUpdateDiscoveredDevice(updatedDevice)
            Log.d(TAG, "Pairing rejected by ${device.deviceId}")
        }
    }
}

suspend fun NetworkService.handleDeviceInfo(deviceInfo: DeviceInfo, device: PairedDevice) {
    val updatedDevice = device.copy(
        deviceName = deviceInfo.deviceName,
        avatar = deviceInfo.avatar
    )
    deviceManager.addOrUpdatePairedDevice(updatedDevice)
    Log.d(TAG, "DeviceInfo updated for ${device.deviceId}")
}

suspend fun NetworkService.handleMediaInfo(deviceId: String, playbackSession: PlaybackSession) {
    if (preferencesRepository.readMediaSessionSettingsForDevice(deviceId).first()) {
        mediaHandler.handlePlaybackSessionUpdates(deviceId, playbackSession)
    }
}

suspend fun NetworkService.handleMisc(commandMessage: CommandMessage, device: PairedDevice) {
    when (commandMessage.commandType) {
        CommandType.Disconnect -> disconnectDevice(device, true)
        CommandType.ClearNotifications -> notificationHandler.removeAllNotification()
        CommandType.RequestAppList -> handleAppListRequest(device)
    }
}

private fun NetworkService.handleAppListRequest(device: PairedDevice) {
    val appList = getInstalledApps(packageManager)
    sendMessage(device.deviceId, appList)
}

fun NetworkService.handleRingerMode(ringerMode: RingerMode) {
    try {
        audioManager.ringerMode = ringerMode.mode
    } catch (e: Exception) {
        Log.e(TAG, "Error changing ringer mode", e)
    }
}

fun NetworkService.handleDndStatus(dndStatus: DndStatus) {
    try {
        if (dndStatus.isEnabled) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        } else {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error setting DND mode", e)
    }
}



private fun NetworkService.handleNotificationMessage(message: NotificationMessage) {
    when (message.notificationType) {
        NotificationType.Removed -> notificationHandler.removeNotification(message.notificationKey)
        NotificationType.Invoke -> notificationHandler.openNotification(message.notificationKey)
        else -> {}
    }
}

fun NetworkService.setStreamVolume(device: PairedDevice, message: AudioStreamMessage) {
    try {
        audioManager.setStreamVolume(message.streamType, message.level, 0)
    } catch (e: Exception) {
        Log.e(TAG, "Error setting audio level for stream ${message.streamType}", e)
    } finally {
        // Read back the actual volume
        // since setStreamVolume can silently fail due to permissions, thank you OnePlus!
        val actualVolume = audioManager.getStreamVolume(message.streamType)
        
        // If the volume doesn't match, send back the actual volume
        if (actualVolume != message.level) {
            val actualMessage = message.copy(level = actualVolume, maxLevel = audioManager.getStreamMaxVolume(message.streamType))
            sendMessage(device.deviceId, actualMessage)
        }
    }
}