package sefirah.network.util

import kotlinx.coroutines.runBlocking
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Singleton
class TrustManager @Inject constructor() {
    // Trust all certificates since we're doing our own authentication
    private val trustAllCerts = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    fun getRemoteTrustManager(): X509TrustManager {
        return trustAllCerts
    }

    // For our server certificate
    fun getLocalTrustManager(): X509TrustManager {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return createTrustManager(keyStore)
    }

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        
        // Use CryptoUtils to create/get the certificate
        val cryptoUtils = CryptoUtils()
        runBlocking {
            cryptoUtils.getOrCreateCertificate()
        }

        return keyStore
    }

    fun getLocalKeyManagerFactory(): KeyManagerFactory {
        val keyStore = createKeyStore()
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, null)
        }
    }

    fun getKeyStore(): KeyStore {
        return KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            
            if (!containsAlias(CryptoUtils.KEY_ALIAS)) {
                runBlocking {
                    // Create certificate and reload
                    CryptoUtils().getOrCreateCertificate()
                    load(null)
                }
                
                if (!containsAlias(CryptoUtils.KEY_ALIAS)) {
                    throw IllegalStateException("Failed to initialize SFTP certificate")
                }
            }
        }
    }

    private fun createTrustManager(keyStore: KeyStore): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }
}