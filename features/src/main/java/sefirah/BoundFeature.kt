package sefirah

import kotlinx.coroutines.sync.withLock
import sefirah.domain.interfaces.DeviceManager

/**
 * Feature that resource bound by active devices
 */
abstract class BoundFeature(
    deviceManager: DeviceManager,
) : Feature(deviceManager) {
    protected abstract suspend fun onStart()

    protected abstract suspend fun onStop()

    final override suspend fun enable(deviceId: String) = lifecycleMutex.withLock {
        if (deviceId in enabledDevices) return@withLock
        if (enabledDevices.isEmpty()) {
            onStart()
        }
        enabledDevices.add(deviceId)
        onStart(deviceId)
    }

    final override suspend fun disable(deviceId: String) = lifecycleMutex.withLock {
        if (deviceId !in enabledDevices) return@withLock
        enabledDevices.remove(deviceId)
        onStop(deviceId)
        if (enabledDevices.isEmpty()) {
            onStop()
        }
    }
}
