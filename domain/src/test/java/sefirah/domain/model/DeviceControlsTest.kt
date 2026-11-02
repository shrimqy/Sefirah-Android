package sefirah.domain.model

import org.junit.Assert.*
import org.junit.Test
import sefirah.domain.util.MessageSerializer

class DeviceControlsTest {
    @Test fun legacyPeerDoesNotAdvertiseNewMessages() {
        val info = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"old"}""") as DeviceInfo
        assertNull(info.controls)
    }
    @Test fun nullBluetoothIsUnknown() {
        val value = DeviceControls()
        assertNull(value.bluetoothEnabled)
        assertEquals("unsupported", value.bluetoothControl)
        assertFalse(value.ringerControl)
    }
    @Test fun sharedContractRoundTripsTargetState() {
        val request = DeviceControlRequest("4ed821d3-258c-4a0d-b0d5-b862c9e9a91b", "bluetooth", 0)
        assertEquals(request, MessageSerializer.deserialize(MessageSerializer.serialize(request)!!))
        val info = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer","controls":{"version":1,"ringerControl":true,"bluetoothControl":"local","bluetoothEnabled":false,"future":true}}""") as DeviceInfo
        assertEquals(false, info.controls!!.bluetoothEnabled)
        assertEquals("local", info.controls!!.bluetoothControl)
    }
    @Test fun newResultDoesNotClaimLocalConfirmationIsApplied() {
        val result = DeviceControlResult("id", "local")
        assertEquals(result, MessageSerializer.deserialize(MessageSerializer.serialize(result)!!))
        assertNotEquals("applied", result.status)
    }

    @Test fun partialInfoPreservesNumbersAndEmptyListClearsThem() {
        val partial = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer"}""") as DeviceInfo
        assertNull(partial.phoneNumbers)
        val cleared = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer","phoneNumbers":[]}""") as DeviceInfo
        assertEquals(emptyList<PhoneNumber>(), cleared.phoneNumbers)
        assertFalse(MessageSerializer.serialize(DeviceInfo("peer"))!!.contains("phoneNumbers"))
    }
}
