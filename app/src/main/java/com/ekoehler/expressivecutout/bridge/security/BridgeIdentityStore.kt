package com.ekoehler.expressivecutout.bridge.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Manages the Android device's cryptographic identity using hardware-backed Android KeyStore.
 * Generates and stores an EC P-256 (secp256r1) keypair for mutual authentication.
 */
class BridgeIdentityStore {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /** Retrieves existing keypair or generates a new EC P-256 identity in Android KeyStore. */
    fun getOrCreateKeyPair(): KeyPair {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS)
        if (privateKey != null && certificate != null) {
            return KeyPair(certificate.publicKey, privateKey)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /** Returns the device's public key encoded in Base64 (X.509 DER format). */
    fun getPublicKeyBase64(): String {
        val keyPair = getOrCreateKeyPair()
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /** Signs given data bytes using the device's private key with SHA256withECDSA. */
    fun sign(data: ByteArray): ByteArray {
        val keyPair = getOrCreateKeyPair()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(keyPair.private)
            update(data)
        }
        return signature.sign()
    }

    /** Verifies a SHA256withECDSA signature against peer public key bytes. */
    fun verify(publicKeyBytes: ByteArray, data: ByteArray, signature: ByteArray): Boolean = runCatching {
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
        val publicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initVerify(publicKey)
            update(data)
        }
        verifier.verify(signature)
    }.getOrDefault(false)

    /** Deletes the stored identity key, forcing a clean re-generation on next use. */
    fun resetIdentity() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "android_bridge_identity"
        const val EC_CURVE_NAME = "secp256r1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
