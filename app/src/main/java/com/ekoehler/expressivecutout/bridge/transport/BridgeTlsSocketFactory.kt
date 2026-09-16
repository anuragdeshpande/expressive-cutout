package com.ekoehler.expressivecutout.bridge.transport

import com.ekoehler.expressivecutout.bridge.security.ShortCodeDerivation
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Creates custom TLS 1.3 socket factories that validate the server certificate
 * directly against a pinned SHA-256 fingerprint from the pairing offer or record.
 */
object BridgeTlsSocketFactory {

    private const val TLS_PROTOCOL = "TLSv1.3"

    /**
     * Builds a pinned [X509TrustManager] and configured [SSLSocketFactory] for the expected certificate fingerprint.
     *
     * @param expectedFingerprintHex Hex-encoded SHA-256 fingerprint expected from the peer's certificate.
     */
    fun createPinnedSocketFactory(expectedFingerprintHex: String): Pair<SSLSocketFactory, X509TrustManager> {
        val targetFingerprint = expectedFingerprintHex.replace(":", "").lowercase()

        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // Client trust check is not used when connecting as a client
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain == null || chain.isEmpty()) {
                    throw CertificateException("Server certificate chain is empty")
                }
                val leafCert = chain[0]
                val actualFp = ShortCodeDerivation.sha256Hex(leafCert.encoded).lowercase()

                if (actualFp != targetFingerprint) {
                    throw CertificateException(
                        "Peer TLS certificate mismatch! Expected: $targetFingerprint, actual: $actualFp"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance(TLS_PROTOCOL).apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }

        return Pair(sslContext.socketFactory, trustManager)
    }

    /**
     * Hostname verifier for direct LAN connections where trust is pinned to the certificate fingerprint
     * rather than an official domain name CA.
     */
    val pinnedHostnameVerifier = HostnameVerifier { _, _ -> true }
}
