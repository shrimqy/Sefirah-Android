package sefirah.domain.util

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64

/** Small header/content verifier shared by network receipt and persisted preview loading. */
object PresentationImage {
    const val MAX_BYTES = 65536
    const val MAX_EDGE = 384
    fun id(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun validate(encoded: String?, expectedId: String?): String? = try {
        if (encoded == null || encoded.length > 87384) null else {
            val bytes = Base64.getDecoder().decode(encoded)
            if (validBounds(bytes) && id(bytes) == expectedId?.lowercase()) encoded else null
        }
    } catch (_: IllegalArgumentException) { null }

    fun validBounds(bytes: ByteArray): Boolean {
        if (bytes.size !in 24..MAX_BYTES) return false
        var width = 0
        var height = 0
        if (bytes.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))) {
            width = ByteBuffer.wrap(bytes,16,4).int
            height = ByteBuffer.wrap(bytes,20,4).int
        } else if (bytes[0] == 255.toByte() && bytes[1] == 216.toByte()) {
            var offset = 2
            while (offset + 8 < bytes.size) {
                if ((bytes[offset++].toInt() and 255) != 255) return false
                while (offset < bytes.size && bytes[offset] == 255.toByte()) offset++
                if (offset + 2 >= bytes.size) return false
                val marker = bytes[offset++].toInt() and 255
                if (marker == 217 || marker == 218) break
                if (marker == 1 || marker in 208..215) continue
                val length = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset+1].toInt() and 255)
                if (length < 2 || offset + length > bytes.size) return false
                if (marker in listOf(192,193,194)) {
                    if (length < 8) return false
                    height = ((bytes[offset+3].toInt() and 255) shl 8) or (bytes[offset+4].toInt() and 255)
                    width = ((bytes[offset+5].toInt() and 255) shl 8) or (bytes[offset+6].toInt() and 255)
                    break
                }
                offset += length
            }
        }
        return width in 1..MAX_EDGE && height in 1..MAX_EDGE
    }
}
