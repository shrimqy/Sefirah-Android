package sefirah.network.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Calendar
import javax.security.auth.x500.X500Principal

class CryptoUtils() {
    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Throws(Exception::class)
    private fun createECDSACertificate(): X509Certificate {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.add(Calendar.YEAR, CERT_VALIDITY_YEARS)

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setCertificateSubject(X500Principal("CN=SefirahAndroid"))
            setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
            setCertificateNotBefore(startDate.time)
            setCertificateNotAfter(endDate.time)
            setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA512
            )
            setUserAuthenticationRequired(false)
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore.getCertificate(KEY_ALIAS) as X509Certificate
    }

    suspend fun getOrCreateCertificate(): X509Certificate {
        return withContext(Dispatchers.IO) {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            keyStore.getCertificate(KEY_ALIAS)?.let { 
                return@withContext it as X509Certificate
            }

            // Create new certificate if it doesn't exist
            return@withContext createECDSACertificate()
        }
    }

    companion object {
        private const val CERT_VALIDITY_YEARS = 10
        const val KEY_ALIAS = "Sefirah"
    }
}

/**
 * Generate a random password with 12 characters, including uppercase letters, lowercase letters, numbers, and special characters.
 */
fun generateRandomPassword(): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + "!@#$%^&*"
    return (1..12).map { allowedChars.random() }.joinToString("")
}