package sefirah.domain.model

data class PairedDevice(
    override val deviceId: String,
    override val deviceName: String,
    override val publicKey: String,
    override val address: String? = null,
    val addresses: List<AddressEntry> = emptyList(),
    val avatar: String? = null,
    val lastConnected: Long? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected(),
    val port: Int? = null,
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
}
