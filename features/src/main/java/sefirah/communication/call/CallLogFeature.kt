package sefirah.communication.call

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import sefirah.Feature
import sefirah.common.util.isCallLogsPermissionGranted
import sefirah.domain.interfaces.DeviceManager
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.DevicePreferences
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogFeature @Inject constructor(
    private val context: Context,
    deviceManager: DeviceManager,
    private val networkManager: NetworkManager,
) : Feature(deviceManager) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncJobs = ConcurrentHashMap<String, Job>()

    override fun isPrefEnabled(prefs: DevicePreferences) = prefs.callLogSync

    override fun hasPermissions(): Boolean = isCallLogsPermissionGranted(context)

    override suspend fun onStart(deviceId: String) {
        syncJobs[deviceId]?.cancelAndJoin()
        syncJobs[deviceId] = scope.launch {
            for (callLog in CallLogHelper.getCallLogs(context)) {
                ensureActive()
                if (!networkManager.sendMessage(deviceId, callLog)) {
                    Log.d(TAG, "Stopping call log sync for $deviceId; connection gone")
                    return@launch
                }
            }
        }
    }

    override suspend fun onStop(deviceId: String) {
        syncJobs.remove(deviceId)?.cancel()
    }

    private companion object {
        const val TAG = "CallLogFeature"
    }
}
