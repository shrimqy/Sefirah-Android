package sefirah.network.extensions

import android.bluetooth.BluetoothClass.Device
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.database.model.toEntity
import sefirah.domain.model.BulkFileTransfer
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceRingerMode
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.Misc
import sefirah.domain.model.MiscType
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.NotificationType
import sefirah.domain.model.PlaybackData
import sefirah.domain.model.RemoteDevice
import sefirah.domain.model.ReplyAction
import sefirah.domain.model.SocketMessage
import sefirah.domain.model.TextMessage
import sefirah.domain.model.ThreadRequest
import sefirah.network.FileTransferService
import sefirah.network.FileTransferService.Companion.ACTION_RECEIVE_FILE
import sefirah.network.FileTransferService.Companion.EXTRA_BULK_TRANSFER
import sefirah.network.FileTransferService.Companion.EXTRA_FILE_TRANSFER
import sefirah.network.NetworkService
import sefirah.network.NetworkService.Companion.TAG
import sefirah.network.util.ECDHHelper

fun NetworkService.handleMessage(message: SocketMessage) {
    when (message) {
        is Misc -> handleMisc(message)
        is NotificationMessage -> handleNotificationMessage(message)
        is NotificationAction -> notificationHandler.performNotificationAction(message)
        is ReplyAction -> notificationHandler.performReplyAction(message)
        is PlaybackData -> handleMediaInfo(message)
        is ClipboardMessage -> {
            Log.d("ClipboardMessage", "Received clipboard message: ${message.content}")
            clipboardHandler.setClipboard(message)
        }
        is FileTransfer -> handleFileTransfer(message)
        is BulkFileTransfer -> handleBulkFileTransfer(message)
        is DeviceRingerMode -> handleRingerMode(message)
        is ThreadRequest -> smsHandler.handleThreadRequest(message)
        is TextMessage -> smsHandler.sendTextMessage(message)
        else -> {}
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

suspend fun NetworkService.handleDeviceInfo(deviceInfo: DeviceInfo, remoteInfo: RemoteDevice, ipAddress: String) {

    // Verify authentication
    if (deviceInfo.nonce == null || deviceInfo.proof == null) {
        Log.e(TAG, "Missing authentication data")
        stop(false)
        return
    }

    val localDevice = appRepository.getLocalDevice()

    val sharedSecret = ECDHHelper.deriveSharedSecret(
        localDevice.privateKey,
        remoteInfo.publicKey
    )

    if (!ECDHHelper.verifyProof(sharedSecret, deviceInfo.nonce!!, deviceInfo.proof!!)) {
        Log.e(TAG, "Authentication failed")
        stop(false)
        return
    }

    appRepository.addDevice(
        RemoteDevice(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            avatar = deviceInfo.avatar,
            port = remoteInfo.port,
            lastConnected = System.currentTimeMillis(),
            ipAddresses = remoteInfo.ipAddresses,
            prefAddress = ipAddress,
            publicKey = remoteInfo.publicKey,
        ).toEntity()
    )
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

fun NetworkService.handleRingerMode(ringerMode: DeviceRingerMode) {
    when(ringerMode.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        }
        AudioManager.RINGER_MODE_VIBRATE -> {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        }
        AudioManager.RINGER_MODE_NORMAL -> {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
    }
}

private fun NetworkService.handleNotificationMessage(message: NotificationMessage) {
    when (message.notificationType) {
        NotificationType.Removed -> notificationHandler.removeNotification(message.notificationKey)
        else -> {}
    }
}