package sefirah.domain.model

import android.util.Log
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sefirah.domain.util.MessageSerializer
import javax.net.ssl.SSLSocket

/**
 * Owns one peer's SSL session.
 *
 * Outbound traffic goes through a single writer coroutine fed by [outbound].
 * That keeps writes serialized and lets [close] stop the writer before
 * tearing down the socket.
 */
class DeviceConnection(
    val deviceId: String,
    var sslSocket: SSLSocket? = null,
    var readChannel: ByteReadChannel? = null,
    var writeChannel: ByteWriteChannel? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closeMutex = Mutex()
    private val outbound = Channel<SocketMessage>(Channel.UNLIMITED)
    private var listeningJob: Job? = null
    @Volatile private var closed = false

    private val writerJob = scope.launch {
        try {
            for (message in outbound) {
                val channel = writeChannel ?: continue
                val jsonMessage = MessageSerializer.serialize(message) ?: continue
                try {
                    channel.writeStringUtf8(jsonMessage)
                    channel.writeStringUtf8("\n")
                    channel.flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send message to $deviceId", e)
                    break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Writer failed for $deviceId", e)
        }
    }

    /** @return false if the connection is closed / outbound cancelled. */
    fun sendMessage(message: SocketMessage): Boolean =
        outbound.trySend(message).isSuccess

    /**
     * Starts listening for messages from the device.
     */
    fun startListening(
        getDevice: suspend (String) -> BaseRemoteDevice?,
        onMessage: (BaseRemoteDevice, SocketMessage) -> Unit,
        onClose: (DeviceConnection) -> Unit,
    ) {
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
                        Log.w(TAG, "Read error for $deviceId", e)
                        break
                    }
                }
            } catch (e: CancellationException) {
                // Expected on close
            } catch (e: Exception) {
                Log.e(TAG, "Session error for $deviceId", e)
            } finally {
                // Only notify for remote/unexpected end — local [close] owns cleanup
                if (!closed) {
                    onClose(this@DeviceConnection)
                }
            }
        }
    }

    /**
     * Aborts this connection: drop queued messages, close the socket to unblock
     * any in-flight SSL I/O, then join the writer/listener.
     */
    suspend fun close() {
        closeMutex.withLock {
            if (closed) return
            closed = true

            outbound.cancel()

            // Close the socket first so a blocked write/read fails immediately
            // instead of cancelAndJoin waiting on non-interruptible SSL I/O.
            try {
                sslSocket?.close()
                readChannel?.cancel(kotlinx.io.IOException())
                writeChannel?.cancel(kotlinx.io.IOException())
            } catch (_: Exception) {
            }
            sslSocket = null
            readChannel = null
            writeChannel = null

            writerJob.cancelAndJoin()
            listeningJob?.cancelAndJoin()
            listeningJob = null

            scope.cancel()
        }
    }

    private companion object {
        const val TAG = "DeviceConnection"
    }
}
