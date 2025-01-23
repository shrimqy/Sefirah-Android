package sefirah.common.util

import android.util.Base64
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

fun getCertFromByteArray(byteArray: ByteArray) : X509Certificate {
    val cert = CertificateFactory.getInstance("X.509")
        .generateCertificate(byteArray.inputStream()) as X509Certificate
    return cert
}

fun getCertFromString(string: String) : X509Certificate {
    val certBytes = Base64.decode(string, Base64.NO_WRAP)
    return CertificateFactory.getInstance("X.509")
        .generateCertificate(certBytes.inputStream()) as X509Certificate
}
