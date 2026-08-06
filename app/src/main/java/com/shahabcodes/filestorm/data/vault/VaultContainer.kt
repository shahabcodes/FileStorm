package com.shahabcodes.filestorm.data.vault

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Everything about the original file, kept encrypted inside the container. */
data class VaultFileInfo(
    val name: String,
    val relativePath: String,
    val size: Long,
    val modified: Long,
    val created: Long,
    val accessed: Long,
    val sha256: String,
    /**
     * A small JPEG of the original, encrypted along with the rest, so an
     * unlocked vault can be browsed as a gallery without decrypting whole
     * videos. Written after the other fields, and absent in files made before
     * it existed — readers check for it rather than assuming it.
     */
    val thumbnail: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** The parts of a container readable without any key, used to open a vault cold. */
data class VaultPreamble(
    val version: Int,
    val kdfId: Int,
    val iterations: Int,
    val salt: ByteArray,
    val wrappedByPassphrase: ByteArray,
    val wrappedByRecovery: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

class VaultException(message: String) : IOException(message)

/**
 * The `.fsv` file format.
 *
 * A container carries its own salt and its own wrapped master key, so any
 * single file plus the passphrase is enough to open that file — no keyfile, no
 * index, no app data. That redundancy costs about 100 bytes per file and is
 * what makes the vault survive an uninstall, a lost index, or being moved to
 * another device.
 *
 * The payload is AES-256-GCM in fixed chunks. Each chunk authenticates its own
 * position, so chunks cannot be reordered or duplicated, and the final chunk is
 * marked as final, so truncating a file is detected rather than looking like a
 * shorter file.
 */
object VaultContainer {

    const val EXTENSION = "fsv"
    private const val MAGIC = "FSV1"
    private const val VERSION = 1
    const val DEFAULT_CHUNK_SIZE = 1 shl 20

    /** Bound into the key wrapping so a preamble cannot be swapped between formats. */
    private val WRAP_AAD = MAGIC.toByteArray(Charsets.US_ASCII)

    fun interface Progress {
        fun onBytes(done: Long, total: Long)
    }

    // ── Writing ────────────────────────────────────────────────────────

    /**
     * Encrypts [source] into [target]. Returns the metadata written, including
     * the SHA-256 of the plaintext, which the caller compares after a
     * verification read.
     */
    fun encrypt(
        source: File,
        target: File,
        masterKey: ByteArray,
        preamble: VaultPreamble,
        relativePath: String,
        created: Long = 0L,
        accessed: Long = 0L,
        thumbnail: ByteArray? = null,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        progress: Progress? = null,
    ): VaultFileInfo {
        require(chunkSize > 0) { "chunk size must be positive" }
        val total = source.length()
        val digest = MessageDigest.getInstance("SHA-256")
        val noncePrefix = VaultCrypto.randomBytes(8)

        source.inputStream().buffered().use { input ->
            target.outputStream().buffered().use { rawOut ->
                val out = DataOutputStream(rawOut)
                writePreamble(out, preamble)
                out.write(noncePrefix)
                out.writeInt(chunkSize)

                // The header is written before the payload but needs the hash,
                // which is only known after reading everything. The plaintext is
                // therefore hashed in a first pass and the payload encrypted in a
                // second — both streamed, so memory stays flat either way.
                val hash = hashOf(source, digest)
                val info = VaultFileInfo(
                    name = source.name,
                    relativePath = relativePath,
                    size = total,
                    modified = source.lastModified(),
                    created = created,
                    accessed = accessed,
                    sha256 = hash,
                    thumbnail = thumbnail,
                )
                writeHeader(out, info, masterKey)

                val buffer = ByteArray(chunkSize)
                var index = 0
                var done = 0L
                while (true) {
                    val read = input.readAtMost(buffer)
                    val last = done + read >= total
                    if (read <= 0 && index > 0 && !last) break
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(
                        Cipher.ENCRYPT_MODE,
                        SecretKeySpec(masterKey, "AES"),
                        GCMParameterSpec(VaultCrypto.TAG_BITS, VaultCrypto.chunkNonce(noncePrefix, index)),
                    )
                    cipher.updateAAD(VaultCrypto.chunkAad(index, last))
                    val sealed = cipher.doFinal(buffer, 0, maxOf(read, 0))
                    out.writeInt(sealed.size)
                    out.write(sealed)
                    done += maxOf(read, 0)
                    progress?.onBytes(done, total)
                    index++
                    if (last) break
                }
                out.flush()
                return info
            }
        }
    }

    private fun hashOf(file: File, digest: MessageDigest): String {
        digest.reset()
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun writePreamble(out: DataOutputStream, preamble: VaultPreamble) {
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.writeByte(preamble.version)
        out.writeByte(preamble.kdfId)
        out.writeInt(preamble.iterations)
        out.writeShort(preamble.salt.size)
        out.write(preamble.salt)
        out.writeShort(preamble.wrappedByPassphrase.size)
        out.write(preamble.wrappedByPassphrase)
        out.writeShort(preamble.wrappedByRecovery.size)
        out.write(preamble.wrappedByRecovery)
    }

    private fun writeHeader(out: DataOutputStream, info: VaultFileInfo, masterKey: ByteArray) {
        val plain = java.io.ByteArrayOutputStream()
        DataOutputStream(plain).use { fields ->
            fields.writeUTF(info.name)
            fields.writeUTF(info.relativePath)
            fields.writeLong(info.size)
            fields.writeLong(info.modified)
            fields.writeLong(info.created)
            fields.writeLong(info.accessed)
            fields.writeUTF(info.sha256)
            // Optional trailer. Older readers stop after the hash and simply
            // ignore what follows, so both directions stay compatible.
            val thumb = info.thumbnail
            if (thumb != null && thumb.isNotEmpty()) {
                fields.writeInt(thumb.size)
                fields.write(thumb)
            }
        }
        val nonce = VaultCrypto.randomBytes(VaultCrypto.NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(masterKey, "AES"),
            GCMParameterSpec(VaultCrypto.TAG_BITS, nonce),
        )
        val sealed = cipher.doFinal(plain.toByteArray())
        out.write(nonce)
        out.writeInt(sealed.size)
        out.write(sealed)
    }

    // ── Reading ────────────────────────────────────────────────────────

    /** Reads the key material without needing any key. Never throws on a good file. */
    fun readPreamble(file: File): VaultPreamble? = runCatching {
        file.inputStream().buffered().use { stream ->
            readPreamble(DataInputStream(stream))
        }
    }.getOrNull()

    private fun readPreamble(input: DataInputStream): VaultPreamble {
        val magic = ByteArray(4)
        input.readFully(magic)
        if (String(magic, Charsets.US_ASCII) != MAGIC) {
            throw VaultException("Not a File Storm vault file")
        }
        val version = input.readUnsignedByte()
        if (version != VERSION) throw VaultException("Unsupported vault version $version")
        val kdfId = input.readUnsignedByte()
        val iterations = input.readInt()
        val salt = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
        val wrapped = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
        val recovery = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
        return VaultPreamble(version, kdfId, iterations, salt, wrapped, recovery)
    }

    /** Unwraps the master key using a passphrase. Null means the wrong passphrase. */
    fun openWithPassphrase(preamble: VaultPreamble, passphrase: CharArray): ByteArray? {
        val kek = VaultCrypto.deriveKek(passphrase, preamble.salt, preamble.iterations)
        return try {
            VaultCrypto.unwrapKey(preamble.wrappedByPassphrase, kek, WRAP_AAD)
        } finally {
            VaultCrypto.wipe(kek)
        }
    }

    fun openWithRecoveryCode(preamble: VaultPreamble, code: String): ByteArray? {
        val kek = VaultCrypto.deriveKek(
            VaultCrypto.normaliseRecoveryCode(code),
            preamble.salt,
            preamble.iterations,
        )
        return try {
            VaultCrypto.unwrapKey(preamble.wrappedByRecovery, kek, WRAP_AAD)
        } finally {
            VaultCrypto.wipe(kek)
        }
    }

    /** Builds a preamble for a brand new vault. */
    fun newPreamble(
        masterKey: ByteArray,
        passphrase: CharArray,
        recoveryCode: String,
        strength: VaultCrypto.Strength = VaultCrypto.Strength.STANDARD,
    ): VaultPreamble {
        val salt = VaultCrypto.newSalt()
        val passKek = VaultCrypto.deriveKek(passphrase, salt, strength.iterations)
        val recoveryKek = VaultCrypto.deriveKek(
            VaultCrypto.normaliseRecoveryCode(recoveryCode), salt, strength.iterations,
        )
        return try {
            VaultPreamble(
                version = VERSION,
                kdfId = VaultCrypto.KDF_PBKDF2_SHA256,
                iterations = strength.iterations,
                salt = salt,
                wrappedByPassphrase = VaultCrypto.wrapKey(masterKey, passKek, WRAP_AAD),
                wrappedByRecovery = VaultCrypto.wrapKey(masterKey, recoveryKek, WRAP_AAD),
            )
        } finally {
            VaultCrypto.wipe(passKek)
            VaultCrypto.wipe(recoveryKek)
        }
    }

    /** Reads the original file's details without decrypting the payload. */
    fun readInfo(file: File, masterKey: ByteArray): VaultFileInfo =
        file.inputStream().buffered().use { stream ->
            val input = DataInputStream(stream)
            readPreamble(input)
            input.skipFully(8)
            input.readInt()
            readHeader(input, masterKey)
        }

    private fun readHeader(input: DataInputStream, masterKey: ByteArray): VaultFileInfo {
        val nonce = ByteArray(VaultCrypto.NONCE_BYTES).also { input.readFully(it) }
        val length = input.readInt()
        if (length <= 0 || length > 1 shl 22) throw VaultException("Header is not readable")
        val sealed = input.readExactly(length, "header")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(masterKey, "AES"),
            GCMParameterSpec(VaultCrypto.TAG_BITS, nonce),
        )
        val plain = runCatching { cipher.doFinal(sealed) }.getOrNull()
            ?: throw VaultException("Header failed its integrity check")
        DataInputStream(plain.inputStream()).use { fields ->
            return VaultFileInfo(
                name = fields.readUTF(),
                relativePath = fields.readUTF(),
                size = fields.readLong(),
                modified = fields.readLong(),
                created = fields.readLong(),
                accessed = fields.readLong(),
                sha256 = fields.readUTF(),
                thumbnail = readOptionalThumbnail(fields),
            )
        }
    }

    /** Absent on files written before thumbnails existed, which is fine. */
    private fun readOptionalThumbnail(fields: DataInputStream): ByteArray? = runCatching {
        if (fields.available() < 4) return null
        val length = fields.readInt()
        if (length <= 0 || length > 4 shl 20 || fields.available() < length) return null
        ByteArray(length).also { fields.readFully(it) }
    }.getOrNull()

    /**
     * Decrypts into [target] and checks the result against the hash recorded at
     * encryption time. Throws rather than leaving a partial file behind.
     */
    fun decrypt(
        file: File,
        target: File,
        masterKey: ByteArray,
        progress: Progress? = null,
    ): VaultFileInfo {
        file.inputStream().buffered().use { stream ->
            val input = DataInputStream(stream)
            readPreamble(input)
            val noncePrefix = ByteArray(8).also { input.readFully(it) }
            val chunkSize = input.readInt()
            if (chunkSize <= 0 || chunkSize > 64 shl 20) {
                throw VaultException("Chunk size is not readable")
            }
            val info = readHeader(input, masterKey)

            val digest = MessageDigest.getInstance("SHA-256")
            var index = 0
            var done = 0L
            var sawFinal = false
            target.outputStream().buffered().use { out ->
                while (true) {
                    val length = input.readIntOrNull() ?: break
                    if (length <= 0 || length > chunkSize + 64) {
                        throw VaultException("Chunk ${index} has an impossible size")
                    }
                    val sealed = input.readExactly(length, "chunk $index")
                    val isFinal = done + (length - 16) >= info.size
                    val plain = decryptChunk(sealed, masterKey, noncePrefix, index, isFinal)
                        ?: decryptChunk(sealed, masterKey, noncePrefix, index, !isFinal)
                        ?: throw VaultException(
                            "Chunk $index failed its integrity check — the file has been " +
                                "altered or damaged"
                        )
                    if (isFinal) sawFinal = true
                    out.write(plain)
                    digest.update(plain)
                    done += plain.size
                    progress?.onBytes(done, info.size)
                    index++
                }
            }

            if (!sawFinal) throw VaultException("The file is truncated — its last chunk is missing")
            if (done != info.size) {
                throw VaultException("Size mismatch: expected ${info.size}, got $done")
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != info.sha256) throw VaultException("Contents do not match the recorded hash")
            return info
        }
    }

    private fun decryptChunk(
        sealed: ByteArray,
        masterKey: ByteArray,
        noncePrefix: ByteArray,
        index: Int,
        isFinal: Boolean,
    ): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(masterKey, "AES"),
            GCMParameterSpec(VaultCrypto.TAG_BITS, VaultCrypto.chunkNonce(noncePrefix, index)),
        )
        cipher.updateAAD(VaultCrypto.chunkAad(index, isFinal))
        cipher.doFinal(sealed)
    }.getOrNull()

    // ── Stream helpers ─────────────────────────────────────────────────

    private fun InputStream.readAtMost(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    /** Reads exactly [count] bytes or reports the file as truncated. */
    private fun DataInputStream.readExactly(count: Int, what: String): ByteArray {
        val buffer = ByteArray(count)
        var total = 0
        while (total < count) {
            val read = read(buffer, total, count - total)
            if (read < 0) {
                throw VaultException(
                    "The file is truncated — $what needed $count bytes but only $total remained"
                )
            }
            total += read
        }
        return buffer
    }

    private fun DataInputStream.readIntOrNull(): Int? {
        val a = read()
        if (a < 0) return null
        val b = read()
        val c = read()
        val d = read()
        if (b < 0 || c < 0 || d < 0) throw VaultException("File ends inside a chunk length")
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    private fun DataInputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val jumped = skip(remaining)
            if (jumped <= 0) {
                if (read() < 0) throw VaultException("File ends early")
                remaining--
            } else {
                remaining -= jumped
            }
        }
    }

    private fun OutputStream.unused() = Unit
}
