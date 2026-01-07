package sefirah.domain.model

abstract class BaseRemoteDevice {
    abstract val deviceId: String
    abstract val deviceName: String
    abstract val publicKey: String
    abstract val address: String?
}

