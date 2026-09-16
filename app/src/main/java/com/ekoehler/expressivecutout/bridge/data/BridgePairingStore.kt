package com.ekoehler.expressivecutout.bridge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ekoehler.expressivecutout.bridge.model.PairingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Backing DataStore for persisted Android Bridge pairing records. */
private val Context.bridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "android_bridge_prefs")

/**
 * Persists and retrieves trusted Mac peer pairing metadata.
 * Private keys are deliberately kept out of DataStore and handled in Android KeyStore.
 */
class BridgePairingStore(private val context: Context) {

    private object Keys {
        val PEER_ID = stringPreferencesKey("peer_id")
        val SERVICE_NAME = stringPreferencesKey("service_name")
        val LAST_ENDPOINT = stringPreferencesKey("last_endpoint")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val PEER_PUBLIC_KEY = stringPreferencesKey("peer_public_key")
        val PEER_NAME = stringPreferencesKey("peer_name")
        val PAIRED_AT = longPreferencesKey("paired_at")
    }

    /** Observes the active [PairingRecord], emitting null if the device is not currently paired. */
    val pairingRecord: Flow<PairingRecord?> = context.bridgeDataStore.data.map { prefs ->
        val peerId = prefs[Keys.PEER_ID] ?: return@map null
        val serviceName = prefs[Keys.SERVICE_NAME] ?: return@map null
        val lastEndpoint = prefs[Keys.LAST_ENDPOINT] ?: return@map null
        val fingerprint = prefs[Keys.FINGERPRINT] ?: return@map null
        val peerPublicKey = prefs[Keys.PEER_PUBLIC_KEY] ?: return@map null
        val peerName = prefs[Keys.PEER_NAME] ?: "Mac"
        val pairedAt = prefs[Keys.PAIRED_AT] ?: 0L

        PairingRecord(
            peerId = peerId,
            serviceName = serviceName,
            lastEndpoint = lastEndpoint,
            fingerprint = fingerprint,
            peerPublicKey = peerPublicKey,
            peerName = peerName,
            pairedAt = pairedAt,
        )
    }

    /** Reads the current pairing record synchronously in a coroutine. */
    suspend fun getPairing(): PairingRecord? = pairingRecord.first()

    /** Stores an authenticated pairing record. */
    suspend fun savePairing(record: PairingRecord) {
        context.bridgeDataStore.edit { prefs ->
            prefs[Keys.PEER_ID] = record.peerId
            prefs[Keys.SERVICE_NAME] = record.serviceName
            prefs[Keys.LAST_ENDPOINT] = record.lastEndpoint
            prefs[Keys.FINGERPRINT] = record.fingerprint
            prefs[Keys.PEER_PUBLIC_KEY] = record.peerPublicKey
            prefs[Keys.PEER_NAME] = record.peerName
            prefs[Keys.PAIRED_AT] = record.pairedAt
        }
    }

    /** Updates only the last known endpoint after successful DNS-SD discovery. */
    suspend fun updateLastEndpoint(newEndpoint: String) {
        context.bridgeDataStore.edit { prefs ->
            if (prefs.contains(Keys.PEER_ID)) {
                prefs[Keys.LAST_ENDPOINT] = newEndpoint
            }
        }
    }

    /** Clears the persisted pairing record and revokes the trusted relationship locally. */
    suspend fun clearPairing() {
        context.bridgeDataStore.edit { prefs ->
            prefs.remove(Keys.PEER_ID)
            prefs.remove(Keys.SERVICE_NAME)
            prefs.remove(Keys.LAST_ENDPOINT)
            prefs.remove(Keys.FINGERPRINT)
            prefs.remove(Keys.PEER_PUBLIC_KEY)
            prefs.remove(Keys.PEER_NAME)
            prefs.remove(Keys.PAIRED_AT)
        }
    }
}
