package sefirah.communication.call

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import sefirah.communication.utils.ContactsHelper
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.interfaces.PreferencesRepository
import sefirah.domain.model.CallState
import sefirah.domain.model.CallInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallStateReceiver @Inject constructor(
    private val networkManager: NetworkManager,
    private val deviceManager: DeviceManager,
    private val preferencesRepository: PreferencesRepository
) : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRegistered = false
    private val contactHelper = ContactsHelper()

    /** Last state we sent per phone number, to avoid duplicate events. */
    private val lastStateByNumber = mutableMapOf<String, Int>()

    fun register(context: Context) {
        if (isRegistered) return
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isRegistered = true
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering call state receiver", e)
        }
        isRegistered = false
        lastStateByNumber.clear()
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }

        // We will get a second broadcast with the phone number https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
        if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return

        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!phoneNumber.isNullOrEmpty()) {
            callBroadcastReceived(context.applicationContext, state, phoneNumber)
        }
    }

    private fun callBroadcastReceived(context: Context, state: Int, phoneNumber: String) {
        val contactInfo = contactHelper.getContactInfo(context, phoneNumber)
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (state != lastStateByNumber[phoneNumber]) {
                    lastStateByNumber[phoneNumber] = state
                    sendToDevices(CallInfo(CallState.Ringing, phoneNumber, contactInfo))
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (lastStateByNumber.containsKey(phoneNumber) && state != lastStateByNumber[phoneNumber]) {
                    lastStateByNumber[phoneNumber] = state
                    sendToDevices(CallInfo(CallState.InProgress, phoneNumber, contactInfo))
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                val previousState = lastStateByNumber.remove(phoneNumber)
                if (previousState == TelephonyManager.CALL_STATE_RINGING) {
                    sendToDevices(CallInfo(CallState.MissedCall, phoneNumber, contactInfo))
                }
            }
        }
    }

    private fun sendToDevices(callInfo: CallInfo) {
        scope.launch {
            val connectedDevices = deviceManager.pairedDevices.value.filter { it.connectionState.isConnected }
            for (device in connectedDevices) {
                if (preferencesRepository.readCallStateSyncSettingsForDevice(device.deviceId).first()) {
                    networkManager.sendMessage(device.deviceId, callInfo)
                }
            }
            Log.d(TAG, "Call event: ${callInfo.callState} number=${callInfo.phoneNumber}, contact=${callInfo.contactInfo?.displayName}")
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
    }
}
