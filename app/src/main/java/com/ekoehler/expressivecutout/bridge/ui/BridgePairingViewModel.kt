package com.ekoehler.expressivecutout.bridge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ekoehler.expressivecutout.bridge.data.BridgePairingStore
import com.ekoehler.expressivecutout.bridge.discovery.BridgeNsdResolver
import com.ekoehler.expressivecutout.bridge.model.BridgeState
import com.ekoehler.expressivecutout.bridge.model.PairingOfferParser
import com.ekoehler.expressivecutout.bridge.model.PairingRecord
import com.ekoehler.expressivecutout.bridge.security.BridgeIdentityStore
import com.ekoehler.expressivecutout.bridge.transport.BridgeSessionClient
import com.ekoehler.expressivecutout.bridge.transport.BridgeSessionListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State coordinator for Android Bridge QR pairing, session connectivity, and identity lifecycle.
 */
class BridgePairingViewModel(application: Application) : AndroidViewModel(application) {

    private val identityStore = BridgeIdentityStore()
    private val pairingStore = BridgePairingStore(application)
    private val nsdResolver = BridgeNsdResolver(application)

    private val _state = MutableStateFlow<BridgeState>(BridgeState.Unpaired)
    /** Public observable state flow of the bridge lifecycle. */
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val sessionListener = object : BridgeSessionListener {
        override fun onAwaitingConfirmation(code: String, peerName: String) {
            _state.value = BridgeState.AwaitingConfirmation(code, peerName)
        }

        override fun onPairingSuccess(record: PairingRecord) {
            viewModelScope.launch {
                pairingStore.savePairing(record)
                _state.value = BridgeState.Connected(record)
            }
        }

        override fun onSessionConnected(record: PairingRecord) {
            _state.value = BridgeState.Connected(record)
        }

        override fun onSessionDisconnected(error: Throwable?) {
            viewModelScope.launch {
                val record = pairingStore.getPairing()
                if (record != null) {
                    _state.value = BridgeState.Offline(record)
                } else if (_state.value is BridgeState.Connecting) {
                    _state.value = BridgeState.OfferRejected(error?.localizedMessage ?: "Connection failed")
                }
            }
        }
    }

    private val sessionClient = BridgeSessionClient(identityStore, sessionListener)

    init {
        viewModelScope.launch {
            pairingStore.pairingRecord.collect { record ->
                if (record != null) {
                    if (_state.value is BridgeState.Unpaired) {
                        _state.value = BridgeState.Offline(record)
                        reconnect()
                    }
                } else {
                    if (_state.value !is BridgeState.Scanning && _state.value !is BridgeState.Connecting) {
                        _state.value = BridgeState.Unpaired
                    }
                }
            }
        }
    }

    /** Transitions state to active camera QR scanning. */
    fun startScanning() {
        _state.value = BridgeState.Scanning
    }

    /** Cancels scanning and returns to the previous steady state. */
    fun cancelScanning() {
        viewModelScope.launch {
            val record = pairingStore.getPairing()
            _state.value = if (record != null) BridgeState.Offline(record) else BridgeState.Unpaired
        }
    }

    /** Processes raw scanned QR content, validating the bootstrap offer. */
    fun onQrCodeScanned(qrText: String) {
        val result = PairingOfferParser.parse(qrText)
        val offer = result.getOrNull()
        if (offer == null) {
            val reason = result.exceptionOrNull()?.localizedMessage ?: "Invalid pairing offer"
            _state.value = BridgeState.OfferRejected(reason)
            return
        }

        _state.value = BridgeState.Connecting
        sessionClient.startPairing(offer)
    }

    /** Submits the matching 6-digit confirmation code approved by the user. */
    fun confirmPairing() {
        val currentState = _state.value
        if (currentState is BridgeState.AwaitingConfirmation) {
            _state.value = BridgeState.AwaitingPeerConfirmation(currentState.peerName)
            sessionClient.confirmPairing()
        }
    }

    /** Re-establishes connection to the stored Mac peer over LAN. */
    fun reconnect() {
        viewModelScope.launch {
            val record = pairingStore.getPairing() ?: return@launch
            _state.value = BridgeState.Connecting

            // First attempt connection via last known endpoint
            sessionClient.connectSession(record)

            // If discovery is needed because endpoint changed, try resolving via mDNS
            val discoveredEndpoint = nsdResolver.resolveService(record.serviceName)
            if (discoveredEndpoint != null && discoveredEndpoint != record.lastEndpoint) {
                pairingStore.updateLastEndpoint(discoveredEndpoint)
                val updatedRecord = record.copy(lastEndpoint = discoveredEndpoint)
                sessionClient.connectSession(updatedRecord)
            }
        }
    }

    /** Revokes the pairing relationship, deletes stored metadata, and closes the active session. */
    fun forgetDevice() {
        sessionClient.stop()
        viewModelScope.launch {
            pairingStore.clearPairing()
            _state.value = BridgeState.Unpaired
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionClient.stop()
    }
}
