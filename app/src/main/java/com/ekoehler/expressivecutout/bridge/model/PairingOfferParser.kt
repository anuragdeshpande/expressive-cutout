package com.ekoehler.expressivecutout.bridge.model

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Parses and validates raw QR code content into a [PairingOffer].
 * Rejects malformed, unsupported, expired, or missing fields before any network action.
 */
object PairingOfferParser {

    private const val EXPECTED_SCHEME = "androidbridge"
    private const val EXPECTED_HOST = "pair"
    private const val SUPPORTED_MAJOR_VERSION = "1"
    private const val FINGERPRINT_HEX_LENGTH = 64
    private const val SECONDS_THRESHOLD = 10_000_000_000L

    /**
     * Attempts to parse a raw string from a scanned QR code into a [PairingOffer].
     *
     * @param rawContent Scanned QR code text.
     * @param currentTimeMillis Current clock time used to verify expiry.
     * @return [Result] containing the parsed [PairingOffer] on success, or an [IllegalArgumentException] on failure.
     */
    fun parse(
        rawContent: String,
        currentTimeMillis: Long = System.currentTimeMillis(),
    ): Result<PairingOffer> = runCatching {
        val trimmed = rawContent.trim()
        val uri = URI(trimmed)

        val scheme = uri.scheme
        require(scheme != null && scheme.equals(EXPECTED_SCHEME, ignoreCase = true)) {
            "Invalid scheme: expected $EXPECTED_SCHEME"
        }

        val host = uri.host ?: uri.authority?.substringBefore("?")
        require(host != null && host.equals(EXPECTED_HOST, ignoreCase = true)) {
            "Invalid host: expected $EXPECTED_HOST"
        }

        val queryParams = parseQueryParams(uri.rawQuery ?: uri.query.orEmpty())

        val version = queryParams["v"] ?: throw IllegalArgumentException("Missing protocol version ('v')")
        val majorVersion = version.substringBefore(".")
        require(majorVersion == SUPPORTED_MAJOR_VERSION) {
            "Unsupported protocol version: $version"
        }

        val service = queryParams["service"]?.takeUnless { it.isEmpty() }
            ?: throw IllegalArgumentException("Missing service instance ('service')")

        val endpoint = queryParams["endpoint"]?.takeUnless { it.isEmpty() }
            ?: throw IllegalArgumentException("Missing network endpoint ('endpoint')")
        val endpointParts = endpoint.split(":")
        require(endpointParts.size == 2) { "Invalid endpoint format, expected host:port" }
        val port = endpointParts[1].toIntOrNull()
        require(port != null && port in 1..65535) { "Invalid port in endpoint: ${endpointParts[1]}" }

        val fingerprint = queryParams["fingerprint"]
            ?: throw IllegalArgumentException("Missing certificate fingerprint ('fingerprint')")
        val sanitizedFp = fingerprint.replace(":", "").lowercase()
        require(sanitizedFp.length == FINGERPRINT_HEX_LENGTH && sanitizedFp.all { it in "0123456789abcdef" }) {
            "Invalid SHA-256 fingerprint: must be 64 hexadecimal characters"
        }

        val challenge = queryParams["challenge"]?.takeUnless { it.isEmpty() }
            ?: throw IllegalArgumentException("Missing challenge ('challenge')")

        val expiresRaw = queryParams["expires"]?.toLongOrNull()
            ?: throw IllegalArgumentException("Missing or non-numeric expiry ('expires')")
        // Convert epoch seconds to milliseconds if formatted as seconds
        val expiresAtMs = if (expiresRaw < SECONDS_THRESHOLD) expiresRaw * 1000L else expiresRaw
        require(expiresAtMs > currentTimeMillis) {
            "Pairing offer has expired"
        }

        PairingOffer(
            version = version,
            service = service,
            endpoint = endpoint,
            fingerprint = sanitizedFp,
            challenge = challenge,
            expiresAt = expiresAtMs,
        )
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isEmpty()) return emptyMap()
        val params = mutableMapOf<String, String>()
        for (pair in queryString.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                params[key] = value
            }
        }
        return params
    }
}
