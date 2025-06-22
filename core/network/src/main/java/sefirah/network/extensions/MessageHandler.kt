package sefirah.network.extensions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.database.model.toEntity
import sefirah.domain.model.ApplicationList
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.BulkFileTransfer
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.model.DeviceInfo
import sefirah.domain.model.DeviceRingerMode
import sefirah.domain.model.FileTransfer
import sefirah.domain.model.CommandMessage
import sefirah.domain.model.CommandType
import sefirah.domain.model.NotificationAction
import sefirah.domain.model.NotificationMessage
import sefirah.domain.model.NotificationType
import sefirah.domain.model.PlaybackSession
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
import sefirah.network.util.getInstalledApps

fun NetworkService.handleMessage(message: SocketMessage) {
    when (message) {
        is CommandMessage -> handleMisc(message)
        is NotificationMessage -> handleNotificationMessage(message)
        is NotificationAction -> notificationHandler.performNotificationAction(message)
        is ReplyAction -> notificationHandler.performReplyAction(message)
        is PlaybackSession -> handleMediaInfo(message)
        is ClipboardMessage -> {
            Log.d("ClipboardMessage", "Received clipboard message: ${message.content}")
            clipboardHandler.setClipboard(message)
        }
        is FileTransfer -> handleFileTransfer(message)
        is BulkFileTransfer -> handleBulkFileTransfer(message)
        is DeviceRingerMode -> handleRingerMode(message)
        is ThreadRequest -> smsHandler.handleThreadRequest(message)
        is TextMessage -> smsHandler.sendTextMessage(message)
        is AudioDevice -> mediaHandler.addAudioDevice(message)
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


fun NetworkService.handleMediaInfo(session: PlaybackSession) {
    CoroutineScope(Dispatchers.IO).launch {
        if (preferencesRepository.readMediaSessionSettings().first()) {
            mediaHandler.handlePlaybackSessionUpdates(session)
        }
    }
}

fun NetworkService.handleMisc(commandMessage: CommandMessage) {
    when(commandMessage.commandType) {
        CommandType.Disconnect -> stop(true)
        CommandType.ClearNotifications -> notificationHandler.removeAllNotification()
        CommandType.RequestAppList -> handleAppListRequest()
        else -> {}
    }
}

private fun NetworkService.handleAppListRequest() {
    val appList = getInstalledApps(packageManager)
    val appListMessage = ApplicationList(appList)
    CoroutineScope(Dispatchers.IO).launch {
        sendMessage(appListMessage)
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
        NotificationType.Invoke -> notificationHandler.openNotification(message.notificationKey)
        else -> {}
    }
}