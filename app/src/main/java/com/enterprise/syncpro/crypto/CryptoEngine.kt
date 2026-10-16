package com.enterprise.syncpro.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    const val CHUNK_SIZE_64KB: Int = 64 * 1024 // Strict 65,536-byte chunk mandate

    fun generateRandomHexSecret(byteCount: Int = 32): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return bytes.toHexString()
    }

    fun sha256String(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.toHexString()
    }

    /**
     * Computes 256-bit SHA-256 hash using strict 64KB chunks exclusively on Dispatchers.IO.
     */
    suspend fun computeFileSha256Chunked(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE_64KB)
        FileInputStream(file).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer, 0, CHUNK_SIZE_64KB).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().toHexString()
    }

    /**
     * Generates a cryptographic HMAC-SHA256 authentication tag tying a pre-flight handshake
     * to the paired E2EE shared secret and PIN hash (Certificate/Token Pinning).
     */
    fun computeHandshakeAuthTag(
        sharedSecretHex: String,
        pinHash: String,
        sha256: String,
        fileSize: Long
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest("$sharedSecretHex:$pinHash".toByteArray(Charsets.UTF_8))
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val payload = "$sha256|$fileSize".toByteArray(Charsets.UTF_8)
        return mac.doFinal(payload).toHexString()
    }

    /**
     * Wraps an OutputStream in an AES-256-CTR E2EE stream cipher derived from the PairingToken
     * shared secret and file SHA-256.
     */
    fun wrapEncryptedOutputStream(
        rawOut: OutputStream,
        sharedSecretHex: String,
        fileSha256: String
    ): OutputStream {
        val cipher = createAesCtrCipher(Cipher.ENCRYPT_MODE, sharedSecretHex, fileSha256)
        return CipherOutputStream(rawOut, cipher)
    }

    /**
     * Wraps an InputStream in an AES-256-CTR E2EE stream cipher derived from the PairingToken
     * shared secret and file SHA-256.
     */
    fun wrapDecryptedInputStream(
        rawIn: InputStream,
        sharedSecretHex: String,
        fileSha256: String
    ): InputStream {
        val cipher = createAesCtrCipher(Cipher.DECRYPT_MODE, sharedSecretHex, fileSha256)
        return CipherInputStream(rawIn, cipher)
    }

    private fun createAesCtrCipher(
        mode: Int,
        sharedSecretHex: String,
        fileSha256: String
    ): Cipher {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(sharedSecretHex.toByteArray(Charsets.UTF_8))
        val ivFull = MessageDigest.getInstance("SHA-256")
            .digest(fileSha256.toByteArray(Charsets.UTF_8))
        val iv16 = ivFull.copyOfRange(0, 16)

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(mode, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv16))
        return cipher
    }

    /**
     * Filename Collision Guard: Appends unique 8-character SHA-256 suffix before extension
     * e.g., IMG_0001.jpg -> IMG_0001_A9F40C12.jpg
     */
    fun buildCollisionSafeFileName(originalName: String, sha256: String): String {
        val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val hashSuffix = sha256.take(8).uppercase()
        val dotIndex = sanitized.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < sanitized.length - 1) {
            val base = sanitized.substring(0, dotIndex)
            val ext = sanitized.substring(dotIndex)
            if (base.endsWith("_$hashSuffix")) sanitized else "${base}_${hashSuffix}$ext"
        } else {
            "${sanitized}_$hashSuffix"
        }
    }

    private fun ByteArray.toHexString(): String {
        val hexChars = "0123456789abcdef"
        val result = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }
}