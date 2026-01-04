package org.cleverfox.bluegate

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

class KeyManager(private val keyStore: KeyStore) {

    fun extractUncompressedECPoint(derEncodedKey: ByteArray): ByteArray? {
        // We are looking for the 'BIT STRING' that contains the actual EC point data.
        // The key data typically starts around byte 26 or 27 for secp256r1 keys.

        // A simple, non-robust manual search for the '0x04' prefix marker within the key data:
        var startIndexOfPoint = -1
        for (i in derEncodedKey.indices) {
            // Look for the Uncompressed Point Prefix (0x04)
            if (derEncodedKey[i] == 0x04.toByte()) {
                // Check if the next 64 bytes would fit within the array bounds
                if (i + 64 < derEncodedKey.size) {
                    // We likely found the start of the raw X, Y coordinates
                    startIndexOfPoint = i
                    break
                }
            }
        }

        if (startIndexOfPoint == -1) {
            // Pattern not found, return null or throw an error
            return null
        }

        // Return the 65 bytes starting from the 0x04 prefix
        return derEncodedKey.copyOfRange(startIndexOfPoint, startIndexOfPoint + 65)
    }

    /**
     * Compresses a 65-byte uncompressed EC point into a 33-byte compact format.
     */
    fun compressPublicKeyPoint(uncompressedPoint: ByteArray): ByteArray {
        if (uncompressedPoint.size != 65 || uncompressedPoint[0] != 0x04.toByte()) {
            throw IllegalArgumentException("Invalid uncompressed key point format")
        }

        val xCoord = uncompressedPoint.copyOfRange(1, 33)
        val yCoord = uncompressedPoint.copyOfRange(33, 65)

        // Determine prefix based on the parity (odd/even) of the Y coordinate
        val prefix = if (yCoord.last().toInt() and 1 == 1) 0x03.toByte() else 0x02.toByte()

        // Build the compressed key array (33 bytes)
        val compressedKey = ByteArray(33)
        compressedKey[0] = prefix
        System.arraycopy(xCoord, 0, compressedKey, 1, 32)

        return compressedKey
    }

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

//    fun toRawSignature(asn1Signature: ByteArray): ByteArray {
//        val r = asn1Signature.sliceArray(4 until 4 + 32)
//        val s = asn1Signature.sliceArray(4 + 32 + 2 until asn1Signature.size)
//        return r + s
//    }
    fun toRawSignature(derSig: ByteArray, keySizeBytes: Int = 32): ByteArray {
        var offset = 0

        // Expect SEQUENCE (0x30)
        require(derSig[offset++] == 0x30.toByte()) { "Not a DER SEQUENCE" }

        // Total length (we assume it fits in one byte; enough for ECDSA P-256)
        val seqLen = derSig[offset++].toInt() and 0xFF
        require(seqLen + 2 == derSig.size) { "Bad SEQUENCE length" }

        // INTEGER r
        require(derSig[offset++] == 0x02.toByte()) { "Expected INTEGER for r" }
        val rLen = derSig[offset++].toInt() and 0xFF
        var rBytes = derSig.copyOfRange(offset, offset + rLen)
        offset += rLen

        // INTEGER s
        require(derSig[offset++] == 0x02.toByte()) { "Expected INTEGER for s" }
        val sLen = derSig[offset++].toInt() and 0xFF
        var sBytes = derSig.copyOfRange(offset, offset + sLen)
        // offset += sLen   // not needed further

        // DER encodes them as signed integers -> may have leading 0x00.
        // We want unsigned, fixed-size keySizeBytes, so:
        // 1) Trim leading zeros if longer than keySizeBytes.
        // 2) Left-pad with zeros if shorter than keySizeBytes.

        if (rBytes.size > keySizeBytes) {
            // Trim leading zeros (keep least significant bytes)
            rBytes = rBytes.copyOfRange(rBytes.size - keySizeBytes, rBytes.size)
        }
        if (sBytes.size > keySizeBytes) {
            sBytes = sBytes.copyOfRange(sBytes.size - keySizeBytes, sBytes.size)
        }

        val rPadded = ByteArray(keySizeBytes)
        val sPadded = ByteArray(keySizeBytes)

        // Left-pad
        System.arraycopy(rBytes, 0, rPadded, keySizeBytes - rBytes.size, rBytes.size)
        System.arraycopy(sBytes, 0, sPadded, keySizeBytes - sBytes.size, sBytes.size)

        // Final: r || s
        return rPadded + sPadded
    }

    private fun generateNewKeyPair(alias: String): KeyPair {
        val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )
        val parameterSpec: KeyGenParameterSpec =
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            KeyGenParameterSpec.Builder(
//                alias,
//                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
//            ).setAlgorithmParameterSpec(ECGenParameterSpec("ed25519"))
//                .build()
//        } else {
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
//        }
        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }
}
