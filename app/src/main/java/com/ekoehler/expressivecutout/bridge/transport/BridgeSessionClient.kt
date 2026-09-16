package com.ekoehler.expressivecutout.bridge.transport

import android.os.Build
import android.util.Base64
import com.ekoehler.expressivecutout.bridge.model.PairingOffer
import com.ekoehler.expressivecutout.bridge.model.PairingRecord
import com.ekoehler.expressivecutout.bridge.security.BridgeIdentityStore
import com.ekoehler.expressivecutout.bridge.security.ShortCodeDerivation
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Callbacks emitted during the bridge connection and mutual pairing handshake.
 */
interface BridgeSessionListener {
    /** Called when the 6-digit confirmation code is derived and ready for verification. */
    fun onAwaitingConfirmation(code: String, peerName: String)

    /** Called when pairing has been mutually confirmed and acknowledged by the Mac. */
    fun onPairingSuccess(record: PairingRecord)

    /** Called when the authenticated session becomes live. */
    fun onSessionConnected(record: PairingRecord)

    /** Called when the connection drops or fails. */
    fun onSessionDisconnected(error: Throwable?)
}

/**
 * Manages the foreground TLS 1.3 WebSocket control session between Android and the Mac companion.
 */
class BridgeSessionClient(
    private val identityStore: BridgeIdentityStore,
    private val listener: BridgeSessionListener,
) {

    private var activeWebSocket: WebSocket? = null
    private var pendingRecord: PairingRecord? = null
    private var pendingCode: String? = null

    /**
     * Starts an initial pairing session using the bootstrap [PairingOffer].
     */
    fun startPairing(offer: PairingOffer) {
        stop()

        val (sslFactory, trustManager) = BridgeTlsSocketFactory.createPinnedSocketFactory(offer.fingerprint)

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslFactory, trustManager)
            .hostnameVerifier(BridgeTlsSocketFactory.pinnedHostnameVerifier)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://${offer.endpoint}/bridge")
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendPairHello(webSocket, offer)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handlePairingMessage(webSocket, text, offer)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onSessionDisconnected(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onSessionDisconnected(null)
            }
        })
    }

    /**
     * Connects to a previously paired Mac companion using its stored [PairingRecord].
     */
    fun connectSession(record: PairingRecord) {
        stop()

        val (sslFactory, trustManager) = BridgeTlsSocketFactory.createPinnedSocketFactory(record.fingerprint)

        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslFactory, trustManager)
            .hostnameVerifier(BridgeTlsSocketFactory.pinnedHostnameVerifier)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("wss://${record.lastEndpoint}/bridge")
            .build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                sendSessionHello(webSocket, record)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSessionMessage(text, record)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onSessionDisconnected(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onSessionDisconnected(null)
            }
        })
    }

    /**
     * Confirms the pairing code from the Android user interface.
     */
    fun confirmPairing() {
        val ws = activeWebSocket ?: return
        val code = pendingCode ?: return

        val message = JSONObject().apply {
            put("type", "pair_confirm")
            put("id", UUID.randomUUID().toString())
            put("payload", JSONObject().apply {
                put("code", code)
            })
        }
        ws.send(message.toString())
    }

    /**
     * Closes the active session cleanly.
     */
    fun stop() {
        activeWebSocket?.close(NORMAL_CLOSURE_STATUS, "Closing session")
        activeWebSocket = null
        pendingRecord = null
        pendingCode = null
    }

    private fun sendPairHello(webSocket: WebSocket, offer: PairingOffer) {
        val challengeBytes = runCatching {
            Base64.decode(offer.challenge, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }.getOrElse { offer.challenge.toByteArray(Charsets.UTF_8) }

        val signature = identityStore.sign(challengeBytes)
        val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val message = JSONObject().apply {
            put("type", "pair_hello")
            put("id", UUID.randomUUID().toString())
            put("payload", JSONObject().apply {
                put("protocolVersion", offer.version)
                put("androidIdentity", identityStore.getPublicKeyBase64())
                put("deviceName", deviceName)
                put("signedChallenge", signatureBase64)
            })
        }
        webSocket.send(message.toString())
    }

    private fun sendSessionHello(webSocket: WebSocket, record: PairingRecord) {
        val timestamp = System.currentTimeMillis().toString().toByteArray()
        val signature = identityStore.sign(timestamp)

        val message = JSONObject().apply {
            put("type", "session_hello")
            put("id", UUID.randomUUID().toString())
            put("payload", JSONObject().apply {
                put("peerId", record.peerId)
                put("androidIdentity", identityStore.getPublicKeyBase64())
                put("signedTimestamp", Base64.encodeToString(signature, Base64.NO_WRAP))
                put("timestamp", System.currentTimeMillis())
            })
        }
        webSocket.send(message.toString())
    }

    private fun handlePairingMessage(webSocket: WebSocket, text: String, offer: PairingOffer) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "pair_welcome" -> {
                val payload = json.optJSONObject("payload") ?: return
                val macIdentityBase64 = payload.optString("macIdentity")
                val macDeviceName = payload.optString("deviceName", "Mac")

                val macKeyBytes = Base64.decode(macIdentityBase64, Base64.NO_WRAP)
                val androidKeyBytes = Base64.decode(identityStore.getPublicKeyBase64(), Base64.NO_WRAP)
                val challengeBytes = runCatching {
                    Base64.decode(offer.challenge, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                }.getOrElse { offer.challenge.toByteArray(Charsets.UTF_8) }

                val code = ShortCodeDerivation.derive(challengeBytes, macKeyBytes, androidKeyBytes)
                pendingCode = code

                pendingRecord = PairingRecord(
                    peerId = offer.fingerprint,
                    serviceName = offer.service,
                    lastEndpoint = offer.endpoint,
                    fingerprint = offer.fingerprint,
                    peerPublicKey = macIdentityBase64,
                    peerName = macDeviceName,
                    pairedAt = System.currentTimeMillis(),
                )

                listener.onAwaitingConfirmation(code, macDeviceName)
            }

            "pair_ack" -> {
                val record = pendingRecord ?: return
                listener.onPairingSuccess(record)
            }

            "error" -> {
                listener.onSessionDisconnected(Exception(json.optString("message", "Pairing rejected by peer")))
            }
        }
    }

    private fun handleSessionMessage(text: String, record: PairingRecord) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "session_ack" -> {
                listener.onSessionConnected(record)
            }

            "ping" -> {
                val pong = JSONObject().apply {
                    put("type", "pong")
                    put("id", UUID.randomUUID().toString())
                }
                activeWebSocket?.send(pong.toString())
            }

            "error" -> {
                listener.onSessionDisconnected(Exception(json.optString("message", "Session rejected")))
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 8L
        const val READ_TIMEOUT_SECONDS = 30L
        const val PING_INTERVAL_SECONDS = 15L
        const val NORMAL_CLOSURE_STATUS = 1000
    }
}
