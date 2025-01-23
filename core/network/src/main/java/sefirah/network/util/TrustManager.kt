package sefirah.network.util

import android.content.Context
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TrustManager @Inject constructor(
    private val context: Context,
) {
    fun getRemoteTrustManager(certificate: X509Certificate): X509TrustManager {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null) // Initialize empty keystore
            setCertificateEntry("remote-cert", certificate)
        }
        return createTrustManager(keyStore)
    }

    // For our server certificate
    fun getLocalTrustManager(): X509TrustManager {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return createTrustManager(keyStore)
    }


    // For our server
    fun getLocalKeyManagerFactory(): KeyManagerFactory {
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            init(keyStore, null)
        }
    }

    fun getKeyStore(): KeyStore {
        return KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    private fun createTrustManager(keyStore: KeyStore): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }
}