package sefirah.network.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

class CryptoUtils(private val context: Context) {
    companion object {
        private const val TAG = "CryptoUtils"
        private const val CERT_VALIDITY_YEARS = 10
        private const val KEY_ALIAS = "Sefirah"
    }

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
            setDigests(KeyProperties.DIGEST_SHA256)
            setUserAuthenticationRequired(false)
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()

        // Get the certificate from AndroidKeyStore
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
}

object ECDHHelper {
    private val bcProvider = BouncyCastleProvider()

    fun generateKeys(): Pair<String, String> {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", bcProvider)
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        // Get the public key point directly using BC's ECPublicKeyParameters
        val ecKey = BCECPublicKey::class.java.cast(keyPair.public)
        val q = ecKey?.q
        val encodedPoint = q?.getEncoded(false)  // false = uncompressed format
        val publicKeyBase64 = Base64.encodeToString(encodedPoint, Base64.NO_WRAP)

        // Get private key in Base64
        val privateKey = BCECPrivateKey::class.java.cast(keyPair.private)
        val privateKeyBase64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)

        return Pair(publicKeyBase64, privateKeyBase64)
    }

    fun deriveSharedSecret(privateKeyBase64: String, otherPublicKeyBase64: String): ByteArray {
        val publicKeyBytes = Base64.decode(otherPublicKeyBase64, Base64.NO_WRAP)
        val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)

        // Create EC point from the public key bytes
        val x9Params = ECNamedCurveTable.getParameterSpec("secp256r1")
        val ecPoint = x9Params.curve.decodePoint(publicKeyBytes)

        // Create domain parameters
        val domainParams = ECDomainParameters(x9Params.curve, x9Params.g, x9Params.n, x9Params.h)
        val publicKeyParams = ECPublicKeyParameters(ecPoint, domainParams)

        // Reconstruct private key using PKCS8EncodedKeySpec
        val keyFactory = KeyFactory.getInstance("EC", bcProvider)
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val privateKeySpec = (privateKey as BCECPrivateKey).s
        val privateKeyParams = ECPrivateKeyParameters(privateKeySpec, domainParams)

        // Use BC's ECDH implementation
        val agreement = ECDHBasicAgreement()
        agreement.init(privateKeyParams)
        // Get shared secret as BigInteger first
        val sharedSecretBigInt = agreement.calculateAgreement(publicKeyParams)

        // Convert to unsigned byte array
        val sharedSecretBytes = sharedSecretBigInt.toByteArray()
        // Remove leading zero if present
        val trimmedSecret = if (sharedSecretBytes[0] == 0.toByte() && sharedSecretBytes.size > 1)
            sharedSecretBytes.copyOfRange(1, sharedSecretBytes.size)
        else
            sharedSecretBytes

//        Log.d("SharedSecret", "Raw shared secret: ${trimmedSecret.joinToString("") { "%02X".format(it) }}")

        // Use BouncyCastle's SHA256Digest
        val sha256 = SHA256Digest()
        val hashedSecret = ByteArray(sha256.digestSize)
        sha256.update(trimmedSecret, 0, trimmedSecret.size)
        sha256.doFinal(hashedSecret, 0)

        return hashedSecret
    }

    fun generateNonce(): String {
        val nonce = ByteArray(32)
        SecureRandom().nextBytes(nonce)
        return Base64.encodeToString(nonce, Base64.NO_WRAP)
    }

    fun generateProof(sharedSecret: ByteArray, nonce: String): String {
        try {
            val nonceBytes = Base64.decode(nonce, Base64.NO_WRAP)
            val hmac = Mac.getInstance("HmacSHA256")
            hmac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
            val proof = hmac.doFinal(nonceBytes)
            return Base64.encodeToString(proof, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("EcdhHelper", "Failed to generate proof", e)
            return ""
        }
    }

    fun verifyProof(sharedSecret: ByteArray, nonce: String, proof: String): Boolean {
        val expectedProof = generateProof(sharedSecret, nonce)
        return expectedProof == proof
    }
}