package sefirah.network.util

import android.content.Context
import sefirah.network.R
import java.security.KeyStore
import javax.inject.Inject
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class TrustManager @Inject constructor(
    private val context: Context
) {
    fun getTrustManager(): X509TrustManager {
        val keyStore = loadKeyStore()
        return createTrustManager(keyStore)
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance("PKCS12").apply {
            context.resources.openRawResource(R.raw.server).use { stream ->
                load(stream, "1864thround".toCharArray())
            }
        }
    }

    private fun createTrustManager(keyStore: KeyStore): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(keyStore)
        return factory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }
}