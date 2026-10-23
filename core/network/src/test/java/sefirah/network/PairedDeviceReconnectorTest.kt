package sefirah.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sefirah.domain.model.AddressEntry
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.PairedDevice

@OptIn(ExperimentalCoroutinesApi::class)
class PairedDeviceReconnectorTest {
    private fun device(id: String = "peer") = PairedDevice(
        deviceId = id,
        deviceName = id,
        certificate = byteArrayOf(1, 2, 3),
        addresses = listOf(AddressEntry("192.0.2.1")),
    )

    @Test
    fun `lost session retries with exponential backoff capped at one minute`() = runTest {
        val peer = device()
        val attempts = mutableListOf<Long>()
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts += currentTime }
        reconnector.schedule(peer.deviceId)
        runCurrent()

        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(attempts.isEmpty())
        advanceTimeBy(180_002)
        runCurrent()
        assertEquals(listOf(5_000L, 15_000L, 35_000L, 75_000L, 135_000L), attempts)
    }

    @Test
    fun `duplicate close events do not start parallel retry loops`() = runTest {
        val peer = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        repeat(3) { reconnector.schedule(peer.deviceId) }
        runCurrent()
        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(2, attempts)
    }

    @Test
    fun `a blocked peer does not delay another peers reconnection`() = runTest {
        val peers = listOf(device("first"), device("second")).associateBy { it.deviceId }
        val attempts = mutableListOf<String>()
        val reconnector = PairedDeviceReconnector(backgroundScope, { peers[it] }) {
            attempts += it.deviceId
            if (it.deviceId == "first") awaitCancellation()
        }
        peers.keys.forEach(reconnector::schedule)
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(listOf("first", "second"), attempts)
    }

    @Test
    fun `successful retry stops subsequent attempts`() = runTest {
        var peer = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) {
            attempts++
            peer = peer.copy(connectionState = ConnectionState.Connected)
        }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(1, attempts)
    }

    @Test
    fun `incoming connection cancels the need to dial`() = runTest {
        var peer = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        peer = peer.copy(connectionState = ConnectionState.Connected)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, attempts)
    }

    @Test
    fun `manual or remote forced disconnect prevents retries`() = runTest {
        var peer = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        peer = peer.copy(connectionState = ConnectionState.Disconnected(forcedDisconnect = true))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, attempts)
    }

    @Test
    fun `unpaired devices are not retried`() = runTest {
        var peer: PairedDevice? = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        reconnector.schedule("peer")
        runCurrent()
        peer = null
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, attempts)
    }

    @Test
    fun `retry uses the latest addresses and certificate`() = runTest {
        var peer = device()
        var attempted: PairedDevice? = null
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempted = it }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        peer = peer.copy(addresses = listOf(AddressEntry("100.100.100.100")), certificate = byteArrayOf(4))
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(peer, attempted)
    }

    @Test
    fun `another connection attempt is allowed to finish before retrying`() = runTest {
        var peer = device().copy(connectionState = ConnectionState.Connecting())
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(0, attempts)
        peer = peer.copy(connectionState = ConnectionState.Disconnected())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, attempts)
    }

    @Test
    fun `cancelling an in-flight retry does not remove a newly scheduled loop`() = runTest {
        val peer = device()
        var attempts = 0
        val cancelled = CompletableDeferred<Unit>()
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) {
            attempts++
            if (attempts == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        reconnector.cancel(peer.deviceId)
        reconnector.schedule(peer.deviceId)
        runCurrent()
        assertTrue(cancelled.isCompleted)
        reconnector.schedule(peer.deviceId)
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(2, attempts)
    }

    @Test
    fun `service shutdown cancels pending retries`() = runTest {
        val peer = device()
        var attempts = 0
        val reconnector = PairedDeviceReconnector(backgroundScope, { peer }) { attempts++ }
        reconnector.schedule(peer.deviceId)
        runCurrent()
        backgroundScope.cancel()
        reconnector.schedule(peer.deviceId)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, attempts)
        assertFalse(backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }
}
