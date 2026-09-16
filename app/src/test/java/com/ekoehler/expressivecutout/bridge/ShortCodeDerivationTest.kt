package com.ekoehler.expressivecutout.bridge

import com.ekoehler.expressivecutout.bridge.security.ShortCodeDerivation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying SAS short code derivation and fingerprint calculation.
 */
class ShortCodeDerivationTest {

    @Test
    fun derive_isDeterministicAndSixDigits() {
        val challenge = "static_challenge_bytes_123456789".toByteArray()
        val macPub = "mac_public_key_mock_data_bytes_987".toByteArray()
        val androidPub = "android_public_key_mock_bytes_543".toByteArray()

        val code1 = ShortCodeDerivation.derive(challenge, macPub, androidPub)
        val code2 = ShortCodeDerivation.derive(challenge, macPub, androidPub)

        assertEquals(code1, code2)
        assertEquals(6, code1.length)
        assertTrue(code1.all { it.isDigit() })
    }

    @Test
    fun sha256Hex_producesSixtyFourHexCharacters() {
        val input = "test_certificate_bytes".toByteArray()
        val hex = ShortCodeDerivation.sha256Hex(input)

        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdef" })
    }
}
