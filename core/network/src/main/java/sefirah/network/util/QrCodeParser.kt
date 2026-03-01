package sefirah.network.util

import android.util.Log
import kotlinx.serialization.json.Json
import sefirah.domain.model.ConnectionDetails
import sefirah.domain.model.QrCodeConnectionData

object QrCodeParser {
    private const val TAG = "QrCodeParser"
    private val json = Json { ignoreUnknownKeys = true }

    fun parseQrCode(qrCodeData: String): QrCodeConnectionData? {
        return try {
            json.decodeFromString<QrCodeConnectionData>(qrCodeData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse QR code data: $qrCodeData", e)
            null
        }
    }

    fun toConnectionDetails(qrData: QrCodeConnectionData, selectedIp: String? = null): ConnectionDetails {
        return ConnectionDetails(
            deviceId = qrData.deviceId,
            prefAddress = selectedIp,
            addresses = if (!selectedIp.isNullOrEmpty() && qrData.addresses.contains(selectedIp)) {
                qrData.addresses - selectedIp
            } else {
                qrData.addresses
            },
            port = qrData.port
        )
    }
}