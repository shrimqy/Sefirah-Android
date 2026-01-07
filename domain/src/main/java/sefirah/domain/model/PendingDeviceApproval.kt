package sefirah.domain.model

data class PendingDeviceApproval(
    val deviceId: String,
    val deviceName: String,
    val verificationCode: String
)

