package com.ekoehler.expressivecutout.bridge.model

/**
 * Domain states representing the Android Bridge pairing lifecycle and live session status.
 */
sealed interface BridgeState {

    /** No active pairing relationship exists. */
    data object Unpaired : BridgeState

    /** Camera QR scanner is actively looking for a pairing offer code. */
    data object Scanning : BridgeState

    /** An offer was rejected due to expiry, malformed format, or protocol mismatch. */
    data class OfferRejected(val reason: String) : BridgeState

    /** Establishing TLS 1.3 connection to the Mac endpoint. */
    data object Connecting : BridgeState

    /** Both devices are showing the 6-digit confirmation code awaiting user approval. */
    data class AwaitingConfirmation(
        val confirmationCode: String,
        val peerName: String,
    ) : BridgeState

    /** Local user confirmed the code; awaiting peer's confirmation. */
    data class AwaitingPeerConfirmation(
        val peerName: String,
    ) : BridgeState

    /** Mutually authenticated session is live and connected. */
    data class Connected(
        val record: PairingRecord,
    ) : BridgeState

    /** Pairing is durable and persisted, but current network connection is offline. */
    data class Offline(
        val record: PairingRecord,
    ) : BridgeState
}
