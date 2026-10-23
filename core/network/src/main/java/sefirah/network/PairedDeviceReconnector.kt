package sefirah.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sefirah.domain.model.PairedDevice

/** Retries lost paired sessions without depending on LAN discovery. */
internal class PairedDeviceReconnector(
    private val scope: CoroutineScope,
    private val getDevice: suspend (String) -> PairedDevice?,
    private val connect: suspend (PairedDevice) -> Unit,
) {
    private val jobs = mutableMapOf<String, Job>()

    fun schedule(deviceId: String) = synchronized(jobs) {
        if (!scope.isActive) return@synchronized
        if (jobs[deviceId]?.isActive == true) return@synchronized

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                var retryDelay = INITIAL_DELAY_MS
                while (isActive) {
                    delay(retryDelay)
                    val device = getDevice(deviceId) ?: break
                    if (device.connectionState.isConnected || device.connectionState.isForcedDisconnect) break

                    // Discovery or a manual request may already be dialing this peer.
                    if (!device.connectionState.isConnecting) {
                        connect(device)
                        val updated = getDevice(deviceId) ?: break
                        if (updated.connectionState.isConnected || updated.connectionState.isForcedDisconnect) break
                        retryDelay = (retryDelay * 2).coerceAtMost(MAX_DELAY_MS)
                    }
                }
            } finally {
                val job = currentCoroutineContext()[Job]
                synchronized(jobs) {
                    if (jobs[deviceId] === job) jobs.remove(deviceId)
                }
            }
        }
        jobs[deviceId] = job
        job.start()
        Unit
    }

    fun cancel(deviceId: String) {
        synchronized(jobs) { jobs.remove(deviceId) }?.cancel()
    }

    private companion object {
        const val INITIAL_DELAY_MS = 5_000L
        const val MAX_DELAY_MS = 60_000L
    }
}
