package sefirah.domain.model

data class PairedDevice(
    override val deviceId: String,
    override val deviceName: String,
    val certificate: ByteArray,
    val addresses: List<AddressEntry> = emptyList(),
    val avatar: String? = null,
    val lastConnected: Long? = null,
    val address: String? = null,
    val port: Int? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
) : BaseRemoteDevice() {
    
    /** Returns enabled addresses sorted by priority, or all addresses if none enabled */
    fun getAddressesToTry(): List<String> {
        val enabled = addresses.filter { it.isEnabled }.sortedBy { it.priority }
        return if (enabled.isNotEmpty()) {
            enabled.map { it.address }
        } else {
            addresses.sortedBy { it.priority }.map { it.address }
        }
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is PairedDevice -> false
        else -> deviceId == other.deviceId && deviceName == other.deviceName &&
            address == other.address && addresses == other.addresses && avatar == other.avatar &&
            lastConnected == other.lastConnected && connectionState == other.connectionState &&
            port == other.port && (certificate.contentEquals(other.certificate))
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + deviceName.hashCode()
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + addresses.hashCode()
        result = 31 * result + (avatar?.hashCode() ?: 0)
        result = 31 * result + (lastConnected?.hashCode() ?: 0)
        result = 31 * result + connectionState.hashCode()
        result = 31 * result + (port ?: 0)
        result = 31 * result + certificate.contentHashCode()
        return result
    }
}
