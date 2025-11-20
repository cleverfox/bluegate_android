package com.example.bluegate

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

class KeyManager(private val keyStore: KeyStore) {

    fun getOrCreateKeyPair(): KeyPair {
        val alias = "BlueGate"
        if (keyStore.containsAlias(alias)) {
            try {
                val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
                // The following line is for validation and might not be strictly necessary
                // if getEntry succeeds, but it's good practice.
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            } catch (e: Exception) {
                // The stored key is likely corrupted or from an old, incompatible version.
                // Delete it and generate a new one.
                keyStore.deleteEntry(alias)
            }
        }
        return generateNewKeyPair(alias)
    }

    private fun generateNewKeyPair(alias: String): KeyPair {
        val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        val parameterSpec: KeyGenParameterSpec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setAlgorithmParameterSpec(ECGenParameterSpec("ed25519"))
                .build()
        } else {
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        }
        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }
}
