package com.nuvio.tv.core.media.provider.security

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class ProviderTlsClientFactoryTest {

    private fun OkHttpClient.requiredTrustManager(): X509TrustManager =
        x509TrustManager ?: error("OkHttpClient did not expose an X509TrustManager")

    @Test
    fun `default client uses the platform default hostname verifier`() {
        val client = ProviderTlsClientFactory().createClient()

        assertSame(HttpsURLConnection.getDefaultHostnameVerifier(), client.hostnameVerifier)
    }

    @Test
    fun `default client uses the platform default TLS implementation`() {
        val client = ProviderTlsClientFactory().createClient()
        val platformSocketFactoryClass = SSLContext.getDefault().socketFactory.javaClass

        // JSSE may return a new socket-factory wrapper on each property access,
        // so implementation class + trust behavior (next test) are the stable
        // assertions, rather than reference identity.
        assertEquals(platformSocketFactoryClass, client.sslSocketFactory.javaClass)
    }

    @Test
    fun `default trust manager comes from the default trust store and fails closed`() {
        val client = ProviderTlsClientFactory().createClient()
        val trustManager = client.requiredTrustManager()

        assertTrue(trustManager.acceptedIssuers.isNotEmpty())
        // A trust-all manager such as the app's shared NetworkModule manager
        // accepts this invalid chain. The platform manager rejects it.
        assertThrows(Exception::class.java) {
            trustManager.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `injected hostname verifier is visible on the built client`() {
        val injected = HostnameVerifier { _, _ -> true }
        val client = ProviderTlsClientFactory(hostnameVerifier = injected).createClient()

        assertSame(injected, client.hostnameVerifier)
        assertNotEquals(HttpsURLConnection.getDefaultHostnameVerifier(), client.hostnameVerifier)
    }

    @Test
    fun `injected trust manager drives a dedicated ssl context`() {
        val injected = RecordingX509TrustManager()
        val client = ProviderTlsClientFactory(trustManager = injected).createClient()
        val defaultClient = ProviderTlsClientFactory().createClient()

        assertSame(injected, client.x509TrustManager)
        assertNotEquals(defaultClient.sslSocketFactory, client.sslSocketFactory)
        assertNotEquals(defaultClient.x509TrustManager, client.x509TrustManager)
        // Building the client with a test trust manager must not alter the
        // default-constructed factory, which remains backed by platform CAs.
        assertTrue(defaultClient.requiredTrustManager().acceptedIssuers.isNotEmpty())
    }

    @Test
    fun `factory never derives from the app DI network module`() {
        val classes = securityPackageClassBytes()
        assertTrue("expected compiled security classes on the test classpath", classes.isNotEmpty())
        classes.forEach { (name, bytes) ->
            val text = String(bytes, Charsets.ISO_8859_1)
            assertFalse(
                "$name must not reference the app DI network layer",
                text.contains("com/nuvio/tv/core/di"),
            )
            assertFalse(
                "$name must not reference the app player networking",
                text.contains("com/nuvio/tv/ui/screens/player"),
            )
        }
    }

    @Test
    fun `clients are independent instances`() {
        val factory = ProviderTlsClientFactory()
        val a: OkHttpClient = factory.createClient()
        val b: OkHttpClient = factory.createClient()

        assertNotSame(a, b)
        assertSame(a.hostnameVerifier, b.hostnameVerifier)
        assertEquals(a.sslSocketFactory.javaClass, b.sslSocketFactory.javaClass)
        assertEquals(a.requiredTrustManager().javaClass, b.requiredTrustManager().javaClass)
    }

    /** Test-only trust manager: accepts everything but records invocations. */
    private class RecordingX509TrustManager : X509TrustManager {
        val serverChecks = mutableListOf<String>()

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            serverChecks += authType
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
