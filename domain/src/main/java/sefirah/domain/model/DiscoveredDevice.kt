package sefirah.domain.model

import java.security.cert.Certificate

data class DiscoveredDevice(
    override val deviceId: String,
    override val deviceName: String,
    val address: String,
    val port: Int,
    val addresses: List<String> = emptyList(),
    val certificate: Certificate,
    val verificationCode: String,
    val isPairing: Boolean = false,
) : BaseRemoteDevice()