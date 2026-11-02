package sefirah.status

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import sefirah.common.util.nearbyDevicesPermissionGranted
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.AudioStreamState
import sefirah.domain.model.BatteryState
import sefirah.domain.model.BluetoothState
import sefirah.domain.model.DndState
import sefirah.domain.model.RingerModeState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-side device controls and status (ringer, DND, volume, battery).
 * Handles inbound remote commands and outbound status broadcasts.
 */
@Singleton
class DeviceControlHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
    private val preferences: sefirah.domain.interfaces.PreferencesRepository,
    private val devices: sefirah.domain.interfaces.DeviceManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    data class BluetoothPrompt(val deviceId: String, val enabled: Boolean, val created: Long = android.os.SystemClock.elapsedRealtime())
    private val _bluetoothPrompt = kotlinx.coroutines.flow.MutableStateFlow<BluetoothPrompt?>(null)
    val bluetoothPrompt = _bluetoothPrompt as kotlinx.coroutines.flow.StateFlow<BluetoothPrompt?>
    fun dismissBluetoothPrompt() { _bluetoothPrompt.value = null }
    suspend fun consumeBluetoothPrompt(prompt: BluetoothPrompt): Boolean {
        if (_bluetoothPrompt.value != prompt || android.os.SystemClock.elapsedRealtime() - prompt.created > 30_000) return false
        _bluetoothPrompt.value = null
        val peer = devices.getPairedDevice(prompt.deviceId) ?: return false
        return peer.connectionState.isConnected &&
            preferences.preferenceSettings(peer.deviceId).first().allowRemoteDeviceControls
    }

    private val _controlNotice = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val controlNotice = _controlNotice as kotlinx.coroutines.flow.StateFlow<Map<String, String>>
    private val pendingOutgoing = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun requestBluetooth(peer: sefirah.domain.model.PairedDevice) {
        val controls = peer.controls ?: return
        if (!peer.connectionState.isConnected || controls.version != 1 || pendingOutgoing.containsKey(peer.deviceId) ||
            controls.bluetoothEnabled == null || controls.bluetoothControl !in listOf("direct", "local")) return
        val id = java.util.UUID.randomUUID().toString()
        if (pendingOutgoing.putIfAbsent(peer.deviceId, id) != null) return
        _controlNotice.update { it + (peer.deviceId to "pending") }
        networkManager.sendMessage(peer.deviceId, sefirah.domain.model.DeviceControlRequest(id, "bluetooth",
            if (controls.bluetoothEnabled == true) 0 else 1))
        scope.launch {
            kotlinx.coroutines.delay(8000)
            if (pendingOutgoing.remove(peer.deviceId, id))
                _controlNotice.update { it + (peer.deviceId to "unconfirmed") }
        }
    }
    fun handleResult(peerId: String, result: sefirah.domain.model.DeviceControlResult) {
        if (!pendingOutgoing.remove(peerId, result.requestId)) return
        _controlNotice.update { notices ->
            if (result.status == "applied") notices - peerId else notices + (peerId to result.status)
        }
    }

    suspend fun capabilities(deviceId: String): sefirah.domain.model.DeviceControls {
        val allowed = preferences.preferenceSettings(deviceId).first().allowRemoteDeviceControls
        start()
        val state = getBluetoothState()
        return sefirah.domain.model.DeviceControls(
            ringerControl = allowed,
            bluetoothControl = when {
                bluetoothAdapter == null -> "unsupported"
                !allowed || state.isAvailable == false -> "denied"
                else -> "local"
            },
            bluetoothEnabled = if (state.isAvailable == false) null else state.isEnabled,
        )
    }

    private val requestGate = kotlinx.coroutines.sync.Mutex()
    private val recentRequests = linkedMapOf<Pair<String, String>, Pair<sefirah.domain.model.DeviceControlRequest, sefirah.domain.model.DeviceControlResult>>()

    suspend fun handleControl(deviceId: String, request: sefirah.domain.model.DeviceControlRequest) {
        val peer = devices.getPairedDevice(deviceId) ?: return
        if (!peer.connectionState.isConnected || peer.controls?.version != 1) return
        if (runCatching { java.util.UUID.fromString(request.requestId) }.isFailure) return
        requestGate.withLock {
            val currentPeer = devices.getPairedDevice(deviceId) ?: return@withLock
            if (!currentPeer.connectionState.isConnected || currentPeer.controls?.version != 1) return@withLock
            val key = deviceId to request.requestId
            val previous = recentRequests[key]
            if (previous != null) {
                networkManager.sendMessage(deviceId, if (previous.first == request) previous.second
                    else sefirah.domain.model.DeviceControlResult(request.requestId, "invalid"))
                return@withLock
            }
            val allowed = preferences.preferenceSettings(deviceId).first().allowRemoteDeviceControls
            val status = if (!allowed) "denied" else when (request.control) {
                "ringer" -> if (request.value !in 0..2) "invalid" else try {
                    audioManager.ringerMode = request.value
                    if (audioManager.ringerMode == request.value) "applied" else "unconfirmed"
                } catch (_: SecurityException) { "denied" } catch (_: IllegalArgumentException) { "invalid" }
                "bluetooth" -> when {
                    request.value !in 0..1 -> "invalid"
                    bluetoothAdapter == null -> "unsupported"
                    getBluetoothState().isAvailable == false -> "denied"
                    getBluetoothState().isEnabled == (request.value == 1) -> "applied"
                    else -> {
                        val prompt = BluetoothPrompt(deviceId, request.value == 1)
                        _bluetoothPrompt.value = prompt
                        scope.launch {
                            kotlinx.coroutines.delay(30_000)
                            _bluetoothPrompt.compareAndSet(prompt, null)
                        }
                        "local"
                    }
                }
                else -> "unsupported"
            }
            val result = sefirah.domain.model.DeviceControlResult(request.requestId, status)
            if (recentRequests.size >= 128) recentRequests.remove(recentRequests.keys.first())
            recentRequests[key] = request to result
            networkManager.sendMessage(deviceId, result)
            sendDeviceStatus(deviceId)
        }
    }

    private var lastBatteryStatus: BatteryState? = null
    private var receiversRegistered = false
    private var bluetoothReceiverRegistered = false

    fun start() {
        if (!receiversRegistered) {
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            context.registerReceiver(
                interruptionFilterReceiver,
                IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
            )
            context.registerReceiver(
                interruptionFilterReceiver,
                IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION),
            )
            context.registerReceiver(
                interruptionFilterReceiver,
                IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED),
            )
            receiversRegistered = true
        }

        if (!bluetoothReceiverRegistered && bluetoothAdapter != null && nearbyDevicesPermissionGranted(context)) {
            context.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            bluetoothReceiverRegistered = true
        }
    }

    fun stop() {
        if (receiversRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { context.unregisterReceiver(interruptionFilterReceiver) }
            receiversRegistered = false
        }
        if (bluetoothReceiverRegistered) {
            runCatching { context.unregisterReceiver(bluetoothReceiver) }
            bluetoothReceiverRegistered = false
        }
    }

    fun handleRingerMode(ringerMode: RingerModeState) {
        try {
            audioManager.ringerMode = ringerMode.mode
        } catch (e: Exception) {
            Log.e(TAG, "Error changing ringer mode", e)
        }
    }

    fun handleDndStatus(dndStatus: DndState) {
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

    fun setStreamVolume(deviceId: String, message: AudioStreamState) {
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
                networkManager.sendMessage(deviceId, actualMessage)
            }
        }
    }

    fun sendDeviceStatus(deviceId: String) {
        scope.launch {
            val peer = devices.getPairedDevice(deviceId) ?: return@launch
            if (peer.connectionState.isConnected && peer.controls?.version == 1) {
                networkManager.sendMessage(deviceId, sefirah.domain.model.DeviceInfo(
                    devices.localDevice.deviceName,
                    controls = capabilities(deviceId)))
            }
        }
        val batteryStatus = getBatteryStatus()
        val ringerMode = RingerModeState(audioManager.ringerMode)
        val dndStatus = getDndStatus()

        networkManager.sendMessage(deviceId, batteryStatus)
        networkManager.sendMessage(deviceId, ringerMode)
        networkManager.sendMessage(deviceId, dndStatus)

        if (bluetoothAdapter != null && nearbyDevicesPermissionGranted(context)) {
            networkManager.sendMessage(deviceId, getBluetoothState())
        }

        getAudioLevels().forEach { audioLevel ->
            networkManager.sendMessage(deviceId, audioLevel)
        }

        lastBatteryStatus = batteryStatus
    }

    private fun broadcastBatteryStatus() {
        scope.launch {
            val batteryStatus = getBatteryStatus()
            if (batteryStatus != lastBatteryStatus) {
                networkManager.broadcastMessage(batteryStatus)
                lastBatteryStatus = batteryStatus
            }
        }
    }

    private fun broadcastBluetoothState() {
        if (bluetoothAdapter == null || !nearbyDevicesPermissionGranted(context)) return
        networkManager.broadcastMessage(getBluetoothState())
    }

    private fun getBluetoothState(): BluetoothState {
        if (bluetoothAdapter == null || !nearbyDevicesPermissionGranted(context))
            return BluetoothState(false, isAvailable = false)
        return try {
            BluetoothState(bluetoothAdapter.isEnabled, isAvailable = true)
        } catch (_: SecurityException) {
            BluetoothState(false, isAvailable = false)
        }
    }

    private fun broadcastRingerMode() {
        networkManager.broadcastMessage(RingerModeState(audioManager.ringerMode))
    }

    private fun broadcastDndStatus() {
        networkManager.broadcastMessage(getDndStatus())
    }

    private fun getBatteryStatus(): BatteryState {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
        val isCharging =
            batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
        return BatteryState(batteryLevel, isCharging)
    }

    private fun getDndStatus(): DndState {
        val isDndEnabled =
            notificationManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        return DndState(isDndEnabled)
    }

    private fun getAudioLevels(): List<AudioStreamState> {
        val streamTypes = listOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
        )
        return streamTypes.map { streamType ->
            val level = 100 * audioManager.getStreamVolume(streamType) /
                audioManager.getStreamMaxVolume(streamType)
            AudioStreamState(streamType, level)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            broadcastBatteryStatus()
        }
    }

    private val interruptionFilterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED -> broadcastDndStatus()
                AudioManager.RINGER_MODE_CHANGED_ACTION -> broadcastRingerMode()
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (state == BluetoothAdapter.STATE_ON || state == BluetoothAdapter.STATE_OFF) {
                broadcastBluetoothState()
            }
        }
    }

    private companion object {
        const val TAG = "DeviceControlHandler"
    }
}
