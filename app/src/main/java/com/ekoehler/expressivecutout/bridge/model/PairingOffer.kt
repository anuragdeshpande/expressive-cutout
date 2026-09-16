package com.ekoehler.expressivecutout.bridge.model

/**
 * Validated bootstrap payload parsed from an `androidbridge://pair` offer displayed by the Mac.
 *
 * @property version Protocol major and minor version string (e.g. "1.0").
 * @property service Mac service instance identifier advertised over DNS-SD/mDNS.
 * @property endpoint Current local network endpoint in `host:port` format.
 * @property fingerprint SHA-256 fingerprint of the Mac's TLS certificate.
 * @property challenge One-time cryptographic challenge for mutual authentication.
 * @property expiresAt Unix epoch timestamp in milliseconds when this offer expires.
 */
data class PairingOffer(
    val version: String,
    val service: String,
    val endpoint: String,
    val fingerprint: String,
    val challenge: String,
    val expiresAt: Long,
)
