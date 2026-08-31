package com.nuvio.tv.core.media.provider.security

import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Builds the dedicated OkHttpClient used by AI media provider adapters.
 *
 * This client is constructed from scratch on [OkHttpClient.Builder]: it has no
 * connection to the application's shared DI clients (see
 * NetworkModule.provideOkHttpClient and the plugin runtime clients), which
 * disable TLS certificate and hostname validation. Production defaults here
 * are the platform ones:
 *
 *  - TLS: [SSLContext.getDefault] with the default [X509TrustManager]
 *    (obtained from the default-algorithm [TrustManagerFactory] initialized
 *    with the platform default trust store);
 *  - hostname verification: [HttpsURLConnection.getDefaultHostnameVerifier].
 *
 * [hostnameVerifier] and [trustManager] exist so tests (and only tests) can
 * inject fakes; passing them never relaxes the default configuration for
 * production callers, which construct the factory with no arguments.
 *
 * No interceptors are installed — in particular no logging interceptor, since
 * provider requests carry the user's BYOK key in Authorization headers.
 */
class ProviderTlsClientFactory(
    private val hostnameVerifier: HostnameVerifier? = null,
    private val trustManager: X509TrustManager? = null,
) {
    private val platformHostnameVerifier: HostnameVerifier =
        HttpsURLConnection.getDefaultHostnameVerifier()

    fun createClient(): OkHttpClient {
        val effectiveTrustManager = trustManager ?: defaultX509TrustManager()
        val sslContext = if (trustManager == null) {
            SSLContext.getDefault()
        } else {
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(effectiveTrustManager), null)
            }
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, effectiveTrustManager)
            .hostnameVerifier(hostnameVerifier ?: platformHostnameVerifier)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        // Timeouts chosen for transcription workloads (long audio uploads /
        // streamed responses) while still bounding hung connections.
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 120L
        const val WRITE_TIMEOUT_SECONDS = 120L
        const val CALL_TIMEOUT_SECONDS = 900L

        /** The platform default X509TrustManager (default CA trust store). */
        fun defaultX509TrustManager(): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
                ?: error("No X509TrustManager available from the default TrustManagerFactory")
        }
    }
}
