package sefirah.domain.model

data class DiscoveredDevice(
    override val deviceId: String,
    override val deviceName: String,
    override val publicKey: String,
    override val address: String? = null,
    val addresses: List<String> = emptyList(),
    val verificationCode: String,
    val isPairing: Boolean = false,
    val port: Int? = null
) : BaseRemoteDevice()