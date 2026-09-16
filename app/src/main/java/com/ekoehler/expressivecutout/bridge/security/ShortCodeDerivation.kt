package com.ekoehler.expressivecutout.bridge.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Derives a matching six-digit Short Authentication String (SAS) confirmation code
 * for user verification on both devices.
 */
object ShortCodeDerivation {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val CODE_MODULUS = 1_000_000

    /**
     * Derives a 6-digit confirmation code string from the challenge and both device public keys.
     *
     * @param challenge The raw or decoded bytes of the one-time challenge from the pairing offer.
     * @param macPublicKey The raw DER public key bytes of the Mac device.
     * @param androidPublicKey The raw DER public key bytes of the Android device.
     * @return Formatted 6-digit code with leading zeroes (e.g. "048291").
     */
    fun derive(
        challenge: ByteArray,
        macPublicKey: ByteArray,
        androidPublicKey: ByteArray,
    ): String {
        val hmacKey = SecretKeySpec(challenge, HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply {
            init(hmacKey)
            update(macPublicKey)
            update(androidPublicKey)
        }
        val digest = mac.doFinal()
        val intVal = ByteBuffer.wrap(digest, 0, 4).int
        val codeNumber = abs(intVal % CODE_MODULUS)
        return "%06d".format(codeNumber)
    }

    /**
     * Computes SHA-256 hexadecimal fingerprint for a certificate or public key.
     */
    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
