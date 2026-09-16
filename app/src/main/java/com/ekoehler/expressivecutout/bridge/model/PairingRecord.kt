package com.ekoehler.expressivecutout.bridge.model

/**
 * Persisted trust record for a paired Mac device.
 * Survives application restarts, address changes, and network loss.
 *
 * @property peerId Unique identifier for this peer relationship.
 * @property serviceName The DNS-SD/mDNS service instance name used for LAN discovery.
 * @property lastEndpoint The last successfully connected network address in `host:port` form.
 * @property fingerprint Pinned SHA-256 certificate fingerprint of the peer.
 * @property peerPublicKey Base64-encoded DER representation of the peer's public key.
 * @property peerName Human-readable device name displayed in the UI.
 * @property pairedAt Unix epoch timestamp in milliseconds when pairing completed.
 */
data class PairingRecord(
    val peerId: String,
    val serviceName: String,
    val lastEndpoint: String,
    val fingerprint: String,
    val peerPublicKey: String,
    val peerName: String,
    val pairedAt: Long,
)
