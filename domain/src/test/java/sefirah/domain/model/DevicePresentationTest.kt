package sefirah.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import sefirah.domain.util.MessageSerializer

class DevicePresentationTest {
    @Test fun allPresetsNormalizeDirection() {
        DevicePresentation.kinds.forEach { kind ->
            DevicePresentation.ratios.forEach { ratio ->
                DevicePresentation.orientations.forEach { direction ->
                    val value = DevicePresentation(kind = kind, aspectRatio = ratio, orientation = direction)
                    assertTrue(value.isValid())
                    assertTrue(if (direction == "portrait") value.displayRatio() <= 1 else value.displayRatio() >= 1)
                }
            }
        }
    }
    @Test fun invalidValuesAndFutureVersionsAreNotUsed() {
        assertFalse(DevicePresentation(version = 2).isValid())
        assertFalse(DevicePresentation(aspectRatio = "0:0").isValid())
        assertFalse(DevicePresentation(kind = "Xiaomi Pad").isValid())
        assertFalse(DevicePresentation(wallpaperSource = "wallpaper").isValid())
    }
    @Test fun knownMessageRemainsCompatibleAndClearIsExplicit() {
        val old = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"old","avatar":"legacy"}""") as DeviceInfo
        assertNull(old.presentation)
        val cleared = DeviceInfo("peer", presentation = DevicePresentation(wallpaperSource = "none"))
        val value = MessageSerializer.deserialize(MessageSerializer.serialize(cleared)!!) as DeviceInfo
        assertEquals("none", value.presentation!!.wallpaperSource)
        val future = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer","future":42,"presentation":{"kind":"tablet","aspectRatio":"4:3","orientation":"landscape","future":true}}""") as DeviceInfo
        assertTrue(future.presentation!!.isValid())
    }
    @Test fun metadataDoesNotCarryImage() {
        assertNull(DevicePresentation(wallpaper = "private").metadata().wallpaper)
    }

    @Test fun persistedAndNetworkImagesUseTheSameBoundsAndContentChecks() {
        val bytes = ByteArray(32)
        byteArrayOf(137.toByte(),80,78,71,13,10,26,10).copyInto(bytes)
        java.nio.ByteBuffer.wrap(bytes,16,4).putInt(384)
        java.nio.ByteBuffer.wrap(bytes,20,4).putInt(200)
        val encoded = java.util.Base64.getEncoder().encodeToString(bytes)
        assertEquals(encoded, sefirah.domain.util.PresentationImage.validate(encoded, sefirah.domain.util.PresentationImage.id(bytes)))
        assertNull(sefirah.domain.util.PresentationImage.validate(encoded, "0".repeat(64)))
        java.nio.ByteBuffer.wrap(bytes,16,4).putInt(10000)
        assertFalse(sefirah.domain.util.PresentationImage.validBounds(bytes))
        assertNull(sefirah.domain.util.PresentationImage.validate("A".repeat(90000), null))
    }

    @Test fun partialInfoPreservesNumbersAndEmptyListClearsThem() {
        val partial = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer"}""") as DeviceInfo
        assertNull(partial.phoneNumbers)
        val cleared = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer","phoneNumbers":[]}""") as DeviceInfo
        assertEquals(emptyList<PhoneNumber>(), cleared.phoneNumbers)
        assertFalse(MessageSerializer.serialize(DeviceInfo("peer"))!!.contains("phoneNumbers"))
    }
    @Test fun omittedDefaultsMatchDesktopAndPhoneOverridesStayOnTheWire() {
        val empty = MessageSerializer.deserialize("""{"type":"DeviceInfo","deviceName":"peer","presentation":{}}""") as DeviceInfo
        assertEquals("landscape", empty.presentation!!.orientation)
        assertEquals("16:9", empty.presentation!!.aspectRatio)
        val wire = MessageSerializer.serialize(DeviceInfo("phone", presentation = DevicePresentation(
            kind = "phone", orientation = "portrait", aspectRatio = "9:20")))!!
        assertTrue(wire.contains("\"orientation\":\"portrait\""))
        assertTrue(wire.contains("\"aspectRatio\":\"9:20\""))
    }
}
