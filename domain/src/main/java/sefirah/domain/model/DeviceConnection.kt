package sefirah.domain.model

import android.util.Log
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.domain.util.MessageSerializer
import javax.net.ssl.SSLSocket

class DeviceConnection(
    val deviceId: String,
    var socket: Socket? = null,
    var sslSocket: SSLSocket? = null,
    var readChannel: ByteReadChannel? = null,
    var writeChannel: ByteWriteChannel? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var listeningJob: Job? = null

    /**
     * Sends a message through the connection's write channel (fire-and-forget)
     */
    fun sendMessage(message: SocketMessage) {
        scope.launch {
            mutex.withLock {
                try {
                    writeChannel?.let { channel ->
                        val jsonMessage = MessageSerializer.serialize(message)
                        channel.writeStringUtf8("$jsonMessage\n")
                        channel.flush()
                    }
                } catch (ex: Exception) {
                    Log.e("DeviceConnection", "Failed to send message to $deviceId", ex)
                }
            }
        }
    }

    /**
     * Starts listening for messages from the device.
     * @param scope The coroutine scope to launch the listener in
     * @param getDevice Function to get the device by deviceId
     * @param onMessage Callback to handle received messages
     * @param onClose Callback when connection closes
     */
    fun startListening(
        getDevice: suspend (String) -> BaseRemoteDevice?,
        onMessage: (BaseRemoteDevice, SocketMessage) -> Unit,
        onClose: (String) -> Unit
    ) {
        // Stop existing listener if any
        listeningJob?.cancel()
        
        val channel = readChannel ?: return
        
        listeningJob = scope.launch {
            try {
                while (isActive && !channel.isClosedForRead) {
                    try {
                        channel.readUTF8Line()?.let { line ->
                            MessageSerializer.deserialize(line)?.let { socketMessage ->
                                val device = getDevice(deviceId) ?: return@let
                                onMessage(device, socketMessage)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("DeviceConnection", "Error while receiving data from device $deviceId", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("DeviceConnection", "Session error for device $deviceId", e)
            } finally {
                Log.e("DeviceConnection", "Session closed for device $deviceId")
                onClose(deviceId)
            }
        }
    }

    /**
     * Stops listening for messages.
     */
    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
    }

    /**
     * Closes all connection resources and stops listening.
     */
    fun close() {
        stopListening()
        scope.cancel()
        try {
            socket?.close()
            sslSocket?.close()
            readChannel?.cancel(kotlinx.io.IOException())
            writeChannel?.cancel(kotlinx.io.IOException())
        } catch (_: Exception) {
        }
        socket = null
        sslSocket = null
        readChannel = null
        writeChannel = null
    }
}
