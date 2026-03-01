package sefirah.network.extensions

import android.app.NotificationManager
import android.util.Log
import kotlinx.coroutines.flow.first
import sefirah.domain.model.ActionInfo
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.ApplicationList
import sefirah.domain.model.AudioDeviceInfo
import sefirah.domain.model.AudioStreamState
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ClearNotifications
import sefirah.domain.model.Disconnect
import sefirah.domain.model.RequestApplicationList
import sefirah.domain.model.ConnectionAck
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.DndState
import sefirah.domain.model.FileTransferInfo
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationInfo
import sefirah.domain.model.NotificationInfoType
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PendingDeviceApproval
import sefirah.domain.model.MediaAction
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.NotificationReply
import sefirah.domain.model.RingerModeState
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
                is ClearNotifications -> notificationHandler.removeAllNotification()
                is RequestApplicationList -> handleAppListRequest(device)
                is Disconnect -> disconnectDevice(device, true)
                is NotificationInfo -> handleNotificationMessage(message)
                is NotificationAction -> notificationHandler.performNotificationAction(message)
                is NotificationReply -> notificationHandler.performReplyAction(message)
                is PlaybackInfo -> handleMediaInfo(device.deviceId, message)
                is MediaAction -> handleMediaAction(device.deviceId, message)
                is ClipboardInfo -> clipboardHandler.setClipboard(message)
                is FileTransferInfo ->  fileTransferService.receiveFiles(device.deviceId, message)
                is RingerModeState -> handleRingerMode(message)
                is DndState -> handleDndStatus(message)
                is ThreadRequest -> smsHandler.handleThreadRequest(message)
                is TextMessage -> smsHandler.sendTextMessage(message)
                is AudioDeviceInfo -> remotePlaybackHandler.handleAudioDevice(device.deviceId, message)
                is AudioStreamState -> setStreamVolume(device, message)
                is ActionInfo -> actionHandler.addAction(device.deviceId, message)
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
                connectionState = ConnectionState.Connected,
                port = device.port,
                certificate = device.certificate.encoded,
            )

            deviceManager.removeDiscoveredDevice(device.deviceId)
            deviceManager.addOrUpdatePairedDevice(pairedDevice)
            sendMessage(device.deviceId, ConnectionAck)
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

suspend fun NetworkService.handleMediaInfo(deviceId: String, playbackSession: PlaybackInfo) {
    if (preferencesRepository.readMediaSessionSettingsForDevice(deviceId).first()) {
        remotePlaybackHandler.handlePlaybackSessionUpdates(deviceId, playbackSession)
    }
}

suspend fun NetworkService.handleMediaAction(deviceId: String, action: MediaAction) {
    if (!preferencesRepository.readMediaPlayerControlSettingsForDevice(deviceId).first()) return

    if (playbackService.getActivePackageNames().contains(action.source)) {
        playbackService.handlePlaybackAction(action)
        Log.d(TAG, "Handled MediaAction for Android session: ${action.source}")
    } else {
        Log.d(TAG, "MediaAction source ${action.source} not found in Android sessions, ignoring")
    }
}

private fun NetworkService.handleAppListRequest(device: PairedDevice) {
    val appList = getInstalledApps(packageManager)
    sendMessage(device.deviceId, ApplicationList(appList))
}

fun NetworkService.handleRingerMode(ringerMode: RingerModeState) {
    try {
        audioManager.ringerMode = ringerMode.mode
    } catch (e: Exception) {
        Log.e(TAG, "Error changing ringer mode", e)
    }
}

fun NetworkService.handleDndStatus(dndStatus: DndState) {
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



private fun NetworkService.handleNotificationMessage(message: NotificationInfo) {
    when (message.infoType) {
        NotificationInfoType.Removed -> notificationHandler.removeNotification(message.notificationKey)
        NotificationInfoType.Invoke -> notificationHandler.openNotification(message.notificationKey)
        else -> {}
    }
}

fun NetworkService.setStreamVolume(device: PairedDevice, message: AudioStreamState) {
    val maxVolume = audioManager.getStreamMaxVolume(message.streamType)
    val normalizedVolume = (message.level * maxVolume / 100).coerceIn(0, maxVolume)
    Log.d(TAG, "incoming level: ${message.level}, max: $maxVolume, normalized: $normalizedVolume}")
    try {
        audioManager.setStreamVolume(message.streamType, normalizedVolume, 0)
    } catch (e: Exception) {
        Log.e(TAG, "Error setting audio level for stream ${message.streamType}", e)
    } finally {
        // Read back the actual volume
        // since setStreamVolume can silently fail due to permissions, thank you OnePlus!
        val actualVolume = audioManager.getStreamVolume(message.streamType)
        // If the volume doesn't match, send back the actual volume
        if (actualVolume != normalizedVolume) {
            val level = 100 * actualVolume / maxVolume
            val actualMessage = message.copy(level = level)
            sendMessage(device.deviceId, actualMessage)
        }
    }
}