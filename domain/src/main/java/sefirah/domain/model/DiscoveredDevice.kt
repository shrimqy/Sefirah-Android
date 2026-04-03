package sefirah.domain.model

import java.security.cert.Certificate

data class DiscoveredDevice(
    override val deviceId: String,
    override val deviceName: String,
    val address: String,
    val addresses: List<String> = emptyList(),
    val certificate: Certificate,
    val verificationCode: String,
    val port: Int? = null,
    val isPairing: Boolean = false,
) : BaseRemoteDevice()