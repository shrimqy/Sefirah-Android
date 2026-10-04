package sefirah.network.extensions

import android.util.Log
import sefirah.domain.model.ActionInfo
import sefirah.domain.model.ActionList
import sefirah.domain.model.AudioDeviceInfo
import sefirah.domain.model.AudioStreamState
import sefirah.domain.model.BaseRemoteDevice
import sefirah.domain.model.BatteryState
import sefirah.domain.model.BluetoothPairingRequest
import sefirah.domain.model.ClearNotifications
import sefirah.domain.model.ClipboardInfo
import sefirah.domain.model.ClipboardTransfer
import sefirah.domain.model.ConversationsRequest
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.Disconnect
import sefirah.domain.model.DiscoveredDevice
import sefirah.domain.model.DndState
import sefirah.domain.model.MediaAction
import sefirah.domain.model.MessagesRequest
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationInfo
import sefirah.domain.model.NotificationInfoType
import sefirah.domain.model.NotificationReply
import sefirah.domain.model.PairMessage
import sefirah.domain.model.PairedDevice
import sefirah.domain.model.PlaySound
import sefirah.domain.model.PlaybackInfo
import sefirah.domain.model.RequestApplicationList
import sefirah.domain.model.RingerModeState
import sefirah.domain.model.ShareTransfer
import sefirah.domain.model.SmsAttachmentRequest
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextMessage
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.TAG

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
                is ClearNotifications -> notificationFeature.removeAllNotification()
                is RequestApplicationList -> appListHandler.handleRequest(device.deviceId)
                is Disconnect -> disconnectDevice(device, true)
                is NotificationInfo -> when (message.infoType) {
                    NotificationInfoType.Removed -> notificationFeature.removeNotification(message.notificationKey)
                    NotificationInfoType.Invoke -> notificationFeature.openNotification(message.notificationKey)
                    else -> {}
                }
                is NotificationAction -> notificationFeature.performNotificationAction(message)
                is NotificationReply -> notificationFeature.performReplyAction(message)
                is PlaybackInfo -> remotePlaybackFeature.handlePlaybackSessionUpdates(device.deviceId, message)
                is MediaAction -> playbackFeature.handlePlaybackAction(device.deviceId, message)
                is ClipboardInfo -> clipboardFeature.setClipboard(message)
                is ShareTransfer -> fileTransferService.receiveShare(device.deviceId, message)
                is ClipboardTransfer -> fileTransferService.receiveClipboard(device.deviceId, message)
                is RingerModeState -> deviceControlHandler.handleRingerMode(message)
                is DndState -> deviceControlHandler.handleDndStatus(message)
                is MessagesRequest -> smsFeature.handleMessagesRequest(device.deviceId, message)
                is ConversationsRequest -> smsFeature.handleConversationsRequest(device.deviceId, message)
                is SmsAttachmentRequest -> smsFeature.handleAttachmentRequest(device.deviceId, message)
                is TextMessage -> smsFeature.sendTextMessage(device.deviceId, message)
                is AudioDeviceInfo -> remotePlaybackFeature.handleAudioDevice(device.deviceId, message)
                is AudioStreamState -> deviceControlHandler.setStreamVolume(device.deviceId, message)
                is ActionInfo -> actionFeature.addAction(device.deviceId, message)
                is ActionList -> actionFeature.setActions(device.deviceId, message.actions)
                is BatteryState -> remoteDeviceStatusFeature.updateBattery(device.deviceId, message)
                is BluetoothPairingRequest -> bluetoothPairingHandler.handleBluetoothRequest(device.deviceId)
                is PlaySound -> playSoundFeature.handle(device.deviceId, message)
                else -> {}
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error handling message for device ${device.deviceId}", e)
    }
}
