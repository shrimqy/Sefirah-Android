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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var lastBatteryStatus: BatteryState? = null
    private var receiversRegistered = false
    private var bluetoothReceiverRegistered = false

    fun start() {
        if (receiversRegistered) return
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

        if (bluetoothAdapter != null && nearbyDevicesPermissionGranted(context)) {
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
        val isEnabled = try {
            bluetoothAdapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
        return BluetoothState(isEnabled)
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
