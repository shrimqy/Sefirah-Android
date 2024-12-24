package sefirah.network.util

import android.content.Context
import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec

class CryptoUtils(private val context: Context) {
    companion object {
        private const val TAG = "CryptoUtils"
        const val KEY_ALIAS = "KumoSeki"
        private const val CERT_VALIDITY_YEARS = 10
        private const val CERTIFICATE_FILENAME = "kumoseki.cert"
    }

    init {
        // Register BouncyCastle as the security provider
        Security.addProvider(BouncyCastleProvider())
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

        // Convert to unsigned byte array like C# side
        val sharedSecretBytes = sharedSecretBigInt.toByteArray()
        // Remove leading zero if present (to match C# ToByteArrayUnsigned)
        val trimmedSecret = if (sharedSecretBytes[0] == 0.toByte() && sharedSecretBytes.size > 1)
            sharedSecretBytes.copyOfRange(1, sharedSecretBytes.size)
        else
            sharedSecretBytes

        Log.d("SharedSecret", "Raw shared secret: ${trimmedSecret.joinToString("") { "%02X".format(it) }}")

        // Use BouncyCastle's SHA256Digest like C# side
        val sha256 = SHA256Digest()
        val hashedSecret = ByteArray(sha256.digestSize)
        sha256.update(trimmedSecret, 0, trimmedSecret.size)
        sha256.doFinal(hashedSecret, 0)

        // Use same byte order as C# BitConverter
//        val derivedKey = abs(ByteBuffer.wrap(hashedSecret).order(ByteOrder.LITTLE_ENDIAN).int) % 1_000_000
//        derivedKey.toString().padStart(6, '0')
        return hashedSecret
    }
}