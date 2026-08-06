package com.shahabcodes.filestorm.data.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Key handling for the vault.
 *
 * Deliberately free of Android imports so the whole thing can be exercised by
 * ordinary unit tests on a desktop JVM. Nothing here touches the filesystem or
 * app state either, which is what lets a vault survive the app being
 * uninstalled: every secret needed to open a file travels inside the file.
 */
object VaultCrypto {

    const val KDF_PBKDF2_SHA256 = 1

    /** Unlock cost. Higher is slower to open and slower to attack. */
    enum class Strength(val id: Int, val iterations: Int, val label: String) {
        STANDARD(1, 210_000, "Standard"),
        HIGH(2, 600_000, "High"),
        MAXIMUM(3, 1_800_000, "Maximum"),
        ;

        companion object {
            fun forIterations(iterations: Int): Strength =
                entries.firstOrNull { it.iterations == iterations } ?: STANDARD
        }
    }

    const val KEY_BITS = 256
    const val TAG_BITS = 128
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12

    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    fun newMasterKey(): ByteArray = randomBytes(KEY_BITS / 8)

    fun newSalt(): ByteArray = randomBytes(SALT_BYTES)

    /**
     * Turns a passphrase into a key-encrypting key. This is the expensive step
     * on purpose — it is the only thing standing between a copied vault and a
     * brute-force attack, so it runs once per unlock rather than per file.
     */
    fun deriveKek(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * A 20-character code, shown once at setup, that unwraps the master key
     * independently of the passphrase. Ambiguous characters are left out so it
     * can be written down and typed back without guesswork.
     */
    fun newRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val bytes = randomBytes(20)
        val builder = StringBuilder()
        bytes.forEachIndexed { index, byte ->
            if (index > 0 && index % 5 == 0) builder.append('-')
            builder.append(alphabet[(byte.toInt() and 0xFF) % alphabet.length])
        }
        return builder.toString()
    }

    fun normaliseRecoveryCode(code: String): CharArray =
        code.uppercase().filter { it.isLetterOrDigit() }.toCharArray()

    /** Encrypts the master key with a KEK. Result is nonce-prefixed. */
    fun wrapKey(masterKey: ByteArray, kek: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(kek, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(masterKey)
        return nonce + sealed
    }

    /** Returns null when the passphrase or recovery code is wrong. */
    fun unwrapKey(wrapped: ByteArray, kek: ByteArray, associatedData: ByteArray): ByteArray? {
        if (wrapped.size <= NONCE_BYTES) return null
        return runCatching {
            val nonce = wrapped.copyOfRange(0, NONCE_BYTES)
            val body = wrapped.copyOfRange(NONCE_BYTES, wrapped.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(kek, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(body)
        }.getOrNull()
    }

    /**
     * Nonce for one chunk: a per-file random prefix with the chunk number
     * appended. The prefix keeps two files from ever sharing a nonce under the
     * same master key, and the counter keeps chunks unique within a file.
     */
    fun chunkNonce(prefix: ByteArray, index: Int): ByteArray {
        require(prefix.size == 8) { "nonce prefix must be 8 bytes" }
        val nonce = ByteArray(NONCE_BYTES)
        System.arraycopy(prefix, 0, nonce, 0, 8)
        nonce[8] = (index ushr 24).toByte()
        nonce[9] = (index ushr 16).toByte()
        nonce[10] = (index ushr 8).toByte()
        nonce[11] = index.toByte()
        return nonce
    }

    /**
     * Bound into every chunk so a chunk cannot be moved, repeated, or dropped:
     * its position is authenticated, and the last chunk says that it is last,
     * which is what makes a truncated file fail instead of looking complete.
     */
    fun chunkAad(index: Int, isFinal: Boolean): ByteArray = byteArrayOf(
        (index ushr 24).toByte(),
        (index ushr 16).toByte(),
        (index ushr 8).toByte(),
        index.toByte(),
        if (isFinal) 1 else 0,
    )

    /** Best-effort wipe of key material once it is no longer needed. */
    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}
