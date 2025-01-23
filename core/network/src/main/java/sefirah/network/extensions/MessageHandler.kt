package sefirah.network.extensions

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.database.model.toEntity
import sefirah.domain.model.BulkFileTransfer
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.Misc
import sefirah.domain.model.MiscType
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.PlaybackData
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.SocketMessage
import sefirah.network.FileTransferService
import sefirah.network.FileTransferService.Companion.ACTION_RECEIVE_FILE
import sefirah.network.FileTransferService.Companion.EXTRA_BULK_TRANSFER
import sefirah.network.FileTransferService.Companion.EXTRA_FILE_TRANSFER
import sefirah.network.NetworkService

suspend fun NetworkService.handleMessage(message: SocketMessage) {
    when (message) {
        is Misc -> handleMisc(message)
        is NotificationMessage -> notificationHandler.removeNotification(message.notificationKey)
        is NotificationAction -> notificationHandler.performNotificationAction(message)
        is ReplyAction -> notificationHandler.performReplyAction(message)
        is DeviceInfo -> handleDeviceInfo(message)
        is PlaybackData -> handleMediaInfo(message)
        is ClipboardMessage -> {
            Log.d("ClipboardMessage", "Received clipboard message: ${message.content}")
            clipboardHandler.setClipboard(message)
        }
        is FileTransfer -> handleFileTransfer(message)
        is BulkFileTransfer -> handleBulkFileTransfer(message)
        else -> {

        }
    }
}

fun NetworkService.handleFileTransfer(message: FileTransfer) {
    val intent = Intent(this, FileTransferService::class.java).apply {
        action = ACTION_RECEIVE_FILE
        putExtra(EXTRA_FILE_TRANSFER, message)
    }
    startService(intent)
}

fun NetworkService.handleBulkFileTransfer(message: BulkFileTransfer) {
    val intent = Intent(this, FileTransferService::class.java).apply {
        action = ACTION_RECEIVE_FILE
        putExtra(EXTRA_BULK_TRANSFER, message)
    }
    startService(intent)
}

suspend fun NetworkService.handleDeviceInfo(deviceInfo: DeviceInfo) {
    appRepository.addDevice(
        RemoteDevice(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            avatar = deviceInfo.avatar,
            hashedSecret = connectedDevice.hashedSecret,
            port = connectedDevice.port,
            lastConnected = System.currentTimeMillis(),
            ipAddress = connectedDevice.ipAddress,
            publicKey = connectedDevice.publicKey,
            certificate = connectedDevice.certificate
        ).toEntity())
}

fun NetworkService.handleMediaInfo(playbackData: PlaybackData) {
    CoroutineScope(Dispatchers.IO).launch {
        if (preferencesRepository.readMediaSessionSettings().first()) {
            mediaHandler.updateMediaSession(playbackData)
        }
        playbackRepository.updatePlaybackData(playbackData)
    }

}

fun NetworkService.handleMisc(misc: Misc) {
    when(misc.miscType) {
        MiscType.Disconnect -> stop(true)
        MiscType.ClearNotifications -> notificationHandler.removeAllNotification()
        else -> {}
    }
}