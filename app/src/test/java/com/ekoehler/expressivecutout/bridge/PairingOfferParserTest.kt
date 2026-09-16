package com.ekoehler.expressivecutout.bridge

import com.ekoehler.expressivecutout.bridge.model.PairingOfferParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying [PairingOfferParser] validation rules and failure modes.
 */
class PairingOfferParserTest {

    private val validFingerprint = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"
    private val now = 1700000000000L
    private val futureExpiry = 1700000300L // 300s into future in epoch seconds

    @Test
    fun parse_validOffer_succeeds() {
        val uri = "androidbridge://pair?v=1.0&service=Droppy-MacBook&endpoint=192.168.1.50:8765" +
            "&fingerprint=$validFingerprint&challenge=dGVzdF9jaGFsbGVuZ2U&expires=$futureExpiry"

        val result = PairingOfferParser.parse(uri, currentTimeMillis = now)
        assertTrue(result.isSuccess)
        val offer = result.getOrThrow()
        assertEquals("1.0", offer.version)
        assertEquals("Droppy-MacBook", offer.service)
        assertEquals("192.168.1.50:8765", offer.endpoint)
        assertEquals(validFingerprint, offer.fingerprint)
        assertEquals("dGVzdF9jaGFsbGVuZ2U", offer.challenge)
        assertEquals(futureExpiry * 1000L, offer.expiresAt)
    }

    @Test
    fun parse_expiredOffer_fails() {
        val pastExpiry = 1699999000L
        val uri = "androidbridge://pair?v=1.0&service=Droppy-MacBook&endpoint=192.168.1.50:8765" +
            "&fingerprint=$validFingerprint&challenge=dGVzdA&expires=$pastExpiry"

        val result = PairingOfferParser.parse(uri, currentTimeMillis = now)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("expired") == true)
    }

    @Test
    fun parse_unsupportedMajorVersion_fails() {
        val uri = "androidbridge://pair?v=2.0&service=Droppy-MacBook&endpoint=192.168.1.50:8765" +
            "&fingerprint=$validFingerprint&challenge=dGVzdA&expires=$futureExpiry"

        val result = PairingOfferParser.parse(uri, currentTimeMillis = now)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("version") == true)
    }

    @Test
    fun parse_malformedFingerprint_fails() {
        val badFp = "tooshort"
        val uri = "androidbridge://pair?v=1.0&service=Droppy-MacBook&endpoint=192.168.1.50:8765" +
            "&fingerprint=$badFp&challenge=dGVzdA&expires=$futureExpiry"

        val result = PairingOfferParser.parse(uri, currentTimeMillis = now)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fingerprint") == true)
    }

    @Test
    fun parse_wrongScheme_fails() {
        val uri = "https://pair?v=1.0&service=Droppy-MacBook&endpoint=192.168.1.50:8765" +
            "&fingerprint=$validFingerprint&challenge=dGVzdA&expires=$futureExpiry"

        val result = PairingOfferParser.parse(uri, currentTimeMillis = now)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("scheme") == true)
    }
}
