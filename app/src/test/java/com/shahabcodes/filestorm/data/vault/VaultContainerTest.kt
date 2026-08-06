package com.shahabcodes.filestorm.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Stage 1 verification. Everything the vault promises is asserted here before
 * any of it becomes reachable from the app: contents survive exactly, metadata
 * survives exactly, a file can be recovered from nothing but itself, and every
 * way of damaging a container is detected rather than silently producing wrong
 * plaintext.
 *
 * The KDF is deliberately run at a low iteration count in these tests — the
 * real cost is a settings choice and would only make the suite slow.
 */
class VaultContainerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val passphrase = "correct horse battery staple".toCharArray()
    private val recoveryCode = VaultCrypto.newRecoveryCode()

    private fun fastPreamble(masterKey: ByteArray): VaultPreamble {
        val salt = VaultCrypto.newSalt()
        val iterations = 1_000
        val passKek = VaultCrypto.deriveKek(passphrase.copyOf(), salt, iterations)
        val recoveryKek = VaultCrypto.deriveKek(
            VaultCrypto.normaliseRecoveryCode(recoveryCode), salt, iterations,
        )
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        return VaultPreamble(
            version = 1,
            kdfId = VaultCrypto.KDF_PBKDF2_SHA256,
            iterations = iterations,
            salt = salt,
            wrappedByPassphrase = VaultCrypto.wrapKey(masterKey, passKek, aad),
            wrappedByRecovery = VaultCrypto.wrapKey(masterKey, recoveryKek, aad),
        )
    }

    private fun sourceFile(name: String, bytes: ByteArray, modified: Long): File {
        val file = temp.newFile(name)
        file.writeBytes(bytes)
        file.setLastModified(modified)
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // ── 1. Contents come back byte for byte ────────────────────────────

    @Test
    fun `round trip returns identical bytes`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val payload = Random(7).nextBytes(3 * 1024 * 1024 + 517)
        val source = sourceFile("photo.jpg", payload, 1_600_000_000_000L)
        val sealed = File(temp.root, "a.fsv")
        val restored = File(temp.root, "restored.jpg")

        VaultContainer.encrypt(source, sealed, master, preamble, "Camera/photo.jpg")
        VaultContainer.decrypt(sealed, restored, master)

        assertArrayEquals(payload, restored.readBytes())
        assertEquals(sha256(source), sha256(restored))
    }

    @Test
    fun `empty file round trips`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val source = sourceFile("empty.txt", ByteArray(0), 1_500_000_000_000L)
        val sealed = File(temp.root, "empty.fsv")
        val restored = File(temp.root, "empty-out.txt")

        VaultContainer.encrypt(source, sealed, master, preamble, "empty.txt")
        val info = VaultContainer.decrypt(sealed, restored, master)

        assertEquals(0L, info.size)
        assertEquals(0L, restored.length())
    }

    // ── 2. Metadata is preserved exactly ───────────────────────────────

    @Test
    fun `metadata survives the round trip`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val modified = 1_486_000_000_000L
        val created = 1_400_000_000_000L
        val accessed = 1_490_000_000_000L
        val source = sourceFile("IMG_20240612_wedding.jpg", Random(1).nextBytes(4096), modified)
        val sealed = File(temp.root, "b.fsv")

        VaultContainer.encrypt(
            source, sealed, master, preamble,
            relativePath = "DCIM/Camera/IMG_20240612_wedding.jpg",
            created = created,
            accessed = accessed,
        )
        val info = VaultContainer.readInfo(sealed, master)

        assertEquals("IMG_20240612_wedding.jpg", info.name)
        assertEquals("DCIM/Camera/IMG_20240612_wedding.jpg", info.relativePath)
        assertEquals(4096L, info.size)
        assertEquals(modified, info.modified)
        assertEquals(created, info.created)
        assertEquals(accessed, info.accessed)
    }

    @Test
    fun `original name and dates are not readable without the key`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val source = sourceFile("Passport_scan.pdf", Random(2).nextBytes(2048), 1_486_000_000_000L)
        val sealed = File(temp.root, "c.fsv")

        VaultContainer.encrypt(source, sealed, master, preamble, "Docs/Passport_scan.pdf")

        val raw = sealed.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue("filename leaked into the container", !raw.contains("Passport_scan"))
        assertTrue("path leaked into the container", !raw.contains("Docs/"))
    }

    // ── 3. Large files stream without buffering ────────────────────────

    @Test
    fun `large file streams across many chunks`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        // 64 MB at a 256 KB chunk size is 256 chunks, which exercises the
        // counter and the final-chunk marker far past any single buffer.
        val size = 64 * 1024 * 1024
        val source = temp.newFile("big.bin")
        source.outputStream().buffered().use { out ->
            val block = Random(3).nextBytes(1 shl 20)
            repeat(size / block.size) { out.write(block) }
        }
        val sealed = File(temp.root, "big.fsv")
        val restored = File(temp.root, "big-out.bin")

        var peak = 0L
        VaultContainer.encrypt(
            source, sealed, master, preamble, "big.bin",
            chunkSize = 256 * 1024,
        ) { done, _ -> peak = maxOf(peak, done) }
        VaultContainer.decrypt(sealed, restored, master)

        assertEquals(size.toLong(), restored.length())
        assertEquals(sha256(source), sha256(restored))
        // Overhead is a tag plus a length per chunk, so it stays under a
        // fraction of a percent even at this small chunk size.
        val overhead = sealed.length() - source.length()
        assertTrue("overhead was $overhead bytes", overhead < size * 0.01)
    }

    // ── 4. Wrong credentials are rejected ──────────────────────────────

    @Test
    fun `wrong passphrase does not unwrap the key`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)

        val wrong = VaultCrypto.deriveKek("not the passphrase".toCharArray(), preamble.salt, 1_000)
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        assertNull(VaultCrypto.unwrapKey(preamble.wrappedByPassphrase, wrong, aad))

        val right = VaultCrypto.deriveKek(passphrase.copyOf(), preamble.salt, 1_000)
        assertArrayEquals(master, VaultCrypto.unwrapKey(preamble.wrappedByPassphrase, right, aad))
    }

    @Test
    fun `recovery code unwraps the same master key`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val kek = VaultCrypto.deriveKek(
            VaultCrypto.normaliseRecoveryCode(recoveryCode.lowercase()), preamble.salt, 1_000,
        )
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(master, VaultCrypto.unwrapKey(preamble.wrappedByRecovery, kek, aad))
    }

    // ── 5. A file can be opened from nothing but itself ────────────────

    @Test
    fun `file recovers with no keyfile no index and no app state`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = VaultContainer.newPreamble(
            master, passphrase.copyOf(), recoveryCode, VaultCrypto.Strength.STANDARD,
        )
        val payload = Random(4).nextBytes(200_000)
        val source = sourceFile("holiday.mp4", payload, 1_477_000_000_000L)
        val sealed = File(temp.root, "lonely.fsv")
        VaultContainer.encrypt(source, sealed, master, preamble, "Movies/holiday.mp4")

        // Everything else is gone: pretend this file was found on its own.
        val discovered = VaultContainer.readPreamble(sealed)
        assertNotNull("preamble should be readable without any key", discovered)

        val recoveredKey = VaultContainer.openWithPassphrase(discovered!!, passphrase.copyOf())
        assertNotNull("passphrase should unwrap the key from the file alone", recoveredKey)

        val restored = File(temp.root, "holiday-out.mp4")
        val info = VaultContainer.decrypt(sealed, restored, recoveredKey!!)
        assertEquals("holiday.mp4", info.name)
        assertEquals("Movies/holiday.mp4", info.relativePath)
        assertArrayEquals(payload, restored.readBytes())
    }

    // ── 6. Damage is always detected ───────────────────────────────────

    @Test
    fun `a flipped bit in the payload is detected`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val source = sourceFile("d.bin", Random(5).nextBytes(500_000), 1_400_000_000_000L)
        val sealed = File(temp.root, "d.fsv")
        VaultContainer.encrypt(source, sealed, master, preamble, "d.bin", chunkSize = 64 * 1024)

        RandomAccessFile(sealed, "rw").use { raf ->
            val at = raf.length() - 200
            raf.seek(at)
            val byte = raf.readByte()
            raf.seek(at)
            raf.writeByte(byte.toInt() xor 0x01)
        }

        try {
            VaultContainer.decrypt(sealed, File(temp.root, "d-out.bin"), master)
            fail("a corrupted chunk should not decrypt")
        } catch (e: VaultException) {
            assertTrue(e.message!!.contains("integrity") || e.message!!.contains("hash"))
        }
    }

    @Test
    fun `a truncated file is detected`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val source = sourceFile("t.bin", Random(6).nextBytes(400_000), 1_400_000_000_000L)
        val sealed = File(temp.root, "t.fsv")
        VaultContainer.encrypt(source, sealed, master, preamble, "t.bin", chunkSize = 64 * 1024)

        RandomAccessFile(sealed, "rw").use { raf -> raf.setLength(raf.length() - 70_000) }

        try {
            VaultContainer.decrypt(sealed, File(temp.root, "t-out.bin"), master)
            fail("a truncated container should not decrypt")
        } catch (e: VaultException) {
            assertTrue(
                "unexpected message: ${e.message}",
                e.message!!.contains("truncated") || e.message!!.contains("Size mismatch") ||
                    e.message!!.contains("integrity") || e.message!!.contains("chunk"),
            )
        }
    }

    @Test
    fun `a key from another vault cannot read the file`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val source = sourceFile("x.bin", Random(8).nextBytes(50_000), 1_400_000_000_000L)
        val sealed = File(temp.root, "x.fsv")
        VaultContainer.encrypt(source, sealed, master, preamble, "x.bin")

        try {
            VaultContainer.readInfo(sealed, VaultCrypto.newMasterKey())
            fail("a foreign key should not read the header")
        } catch (e: VaultException) {
            assertTrue(e.message!!.contains("integrity"))
        }
    }

    @Test
    fun `an ordinary file is not mistaken for a container`() {
        val plain = temp.newFile("notes.txt")
        plain.writeText("just some text")
        assertNull(VaultContainer.readPreamble(plain))
    }

    // ── 7. Nonces never repeat ─────────────────────────────────────────

    @Test
    fun `chunk nonces are unique and carry the index`() {
        val prefix = VaultCrypto.randomBytes(8)
        val seen = HashSet<String>()
        repeat(5000) { index ->
            val nonce = VaultCrypto.chunkNonce(prefix, index)
            assertEquals(12, nonce.size)
            assertTrue("nonce repeated at $index", seen.add(nonce.joinToString(",")))
        }
    }

    @Test
    fun `two files never share a nonce prefix`() {
        val master = VaultCrypto.newMasterKey()
        val preamble = fastPreamble(master)
        val prefixes = HashSet<String>()
        repeat(50) { n ->
            val source = sourceFile("f$n.bin", Random(n).nextBytes(1024), 1_400_000_000_000L)
            val sealed = File(temp.root, "f$n.fsv")
            VaultContainer.encrypt(source, sealed, master, preamble, "f$n.bin")
            // The prefix sits directly after the preamble.
            val bytes = sealed.readBytes()
            val start = 4 + 1 + 1 + 4 + 2 + preamble.salt.size + 2 +
                preamble.wrappedByPassphrase.size + 2 + preamble.wrappedByRecovery.size
            val prefix = bytes.copyOfRange(start, start + 8).joinToString(",")
            assertTrue("nonce prefix reused on file $n", prefixes.add(prefix))
        }
    }
}
