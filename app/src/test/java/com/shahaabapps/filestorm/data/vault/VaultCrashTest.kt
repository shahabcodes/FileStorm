package com.shahaabapps.filestorm.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Stage 2 verification: stopping anywhere must be survivable.
 *
 * A crash is reproduced by throwing from the step hook, which lands on the
 * exact instruction boundary every time — far more reliable than trying to kill
 * a process at the right moment, and it means the whole matrix runs in seconds.
 *
 * The invariant every test asserts is the same one the design exists to
 * provide: after recovery, either the original is still there, or the encrypted
 * file is complete and decrypts back to the original's exact bytes. Never
 * neither.
 */
class VaultCrashTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val passphrase = "correct horse battery staple".toCharArray()
    private val master = VaultCrypto.newMasterKey()

    private fun preamble(): VaultPreamble {
        val salt = VaultCrypto.newSalt()
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        val kek = VaultCrypto.deriveKek(passphrase.copyOf(), salt, 1_000)
        return VaultPreamble(1, VaultCrypto.KDF_PBKDF2_SHA256, 1_000, salt,
            VaultCrypto.wrapKey(master, kek, aad), VaultCrypto.wrapKey(master, kek, aad))
    }

    private class Fixture(root: File, val payload: ByteArray) {
        val source = File(root, "original.bin")
        val target = File(root, "sealed.fsv")
        val temp = File(root, "sealed.fsv.tmp")
        val scratch = File(root, "verify.tmp")
        val journalFile = File(root, ".fsjournal")
        val journal = VaultJournal(journalFile)
    }

    private fun fixture(size: Int = 300_000, seed: Int = 11): Fixture {
        val payload = Random(seed).nextBytes(size)
        val f = Fixture(temp.root, payload)
        f.source.writeBytes(payload)
        f.source.setLastModified(1_500_000_000_000L)
        return f
    }

    /** The guarantee, checked after every simulated crash. */
    private fun assertNothingLost(f: Fixture) {
        val originalSurvives = f.source.isFile && f.source.readBytes().contentEquals(f.payload)
        val sealedSurvives = f.target.isFile && runCatching {
            val out = File(temp.root, "check-${System.nanoTime()}.bin")
            VaultContainer.decrypt(f.target, out, master)
            out.readBytes().contentEquals(f.payload)
        }.getOrDefault(false)

        assertTrue(
            "DATA LOST: neither the original nor a readable encrypted file survived",
            originalSurvives || sealedSurvives,
        )
    }

    // ── The full crash matrix ──────────────────────────────────────────

    @Test
    fun `crash at every step leaves the data recoverable`() {
        VaultStep.entries.forEach { crashAt ->
            temp.root.listFiles()?.forEach { it.deleteRecursively() }
            val f = fixture()

            val first = VaultOperations.encryptFile(
                source = f.source, target = f.target, temp = f.temp, scratch = f.scratch,
                masterKey = master, preamble = preamble(), relativePath = "original.bin",
                journal = f.journal,
                onStep = { step -> if (step == crashAt) throw RuntimeException("simulated crash at $step") },
            )
            assertFalse("step $crashAt should have aborted", first.ok)

            // A fresh process starts here: nothing but the journal on disk.
            VaultOperations.recover(VaultJournal(f.journalFile), master)

            assertNothingLost(f)
        }
    }

    @Test
    fun `crash before the original is removed still completes on recovery`() {
        val f = fixture()
        VaultOperations.encryptFile(
            source = f.source, target = f.target, temp = f.temp, scratch = f.scratch,
            masterKey = master, preamble = preamble(), relativePath = "original.bin",
            journal = f.journal,
            onStep = { if (it == VaultStep.REMOVE_ORIGINAL) throw RuntimeException("crash") },
        )

        // The encrypted file is in place and proven; only the tidy-up was missed.
        assertTrue(f.target.isFile)
        assertTrue("original should still exist before recovery", f.source.isFile)

        val handled = VaultOperations.recover(VaultJournal(f.journalFile), master)
        assertEquals(1, handled)
        assertFalse("recovery should have removed the redundant original", f.source.exists())

        val restored = File(temp.root, "restored.bin")
        VaultContainer.decrypt(f.target, restored, master)
        assertArrayEquals(f.payload, restored.readBytes())
    }

    @Test
    fun `crash before verification keeps the original and discards the temp`() {
        val f = fixture()
        VaultOperations.encryptFile(
            source = f.source, target = f.target, temp = f.temp, scratch = f.scratch,
            masterKey = master, preamble = preamble(), relativePath = "original.bin",
            journal = f.journal,
            onStep = { if (it == VaultStep.VERIFY_TEMP) throw RuntimeException("crash") },
        )

        VaultOperations.recover(VaultJournal(f.journalFile), master)

        assertTrue("original must survive", f.source.isFile)
        assertArrayEquals(f.payload, f.source.readBytes())
        assertFalse("unverified temp should be gone", f.temp.exists())
        assertFalse("nothing should have been placed", f.target.exists())
    }

    @Test
    fun `a verified but unplaced file is completed rather than thrown away`() {
        val f = fixture()
        VaultOperations.encryptFile(
            source = f.source, target = f.target, temp = f.temp, scratch = f.scratch,
            masterKey = master, preamble = preamble(), relativePath = "original.bin",
            journal = f.journal,
            onStep = { if (it == VaultStep.PLACE) throw RuntimeException("crash") },
        )
        assertTrue("temp should be waiting", f.temp.isFile)

        VaultOperations.recover(VaultJournal(f.journalFile), master)

        assertTrue("verified work should have been finished", f.target.isFile)
        val restored = File(temp.root, "restored2.bin")
        VaultContainer.decrypt(f.target, restored, master)
        assertArrayEquals(f.payload, restored.readBytes())
    }

    // ── The journal itself has to survive a bad ending ─────────────────

    @Test
    fun `a half written journal line is ignored`() {
        val f = fixture(size = 1000)
        val journal = f.journal
        journal.append(VaultRecord("a", VaultState.STARTED, "/s", "/t", "/g"))
        journal.append(VaultRecord("a", VaultState.WRITTEN, "/s", "/t", "/g"))

        // The power goes mid-write, leaving a partial last line.
        RandomAccessFile(f.journalFile, "rw").use { raf -> raf.setLength(raf.length() - 12) }
        journal.append(VaultRecord("b", VaultState.STARTED, "/s2", "/t2", "/g2"))

        val replay = journal.replay()
        assertNotNull("records before the tear must survive", replay["b"])
        assertTrue(
            "the torn record must not be trusted",
            replay["a"] == null || replay["a"]!!.state == VaultState.STARTED,
        )
    }

    @Test
    fun `a corrupted journal line does not derail recovery`() {
        val f = fixture(size = 1000)
        f.journal.append(VaultRecord("x", VaultState.STARTED, "/s", "/t", "/g"))
        f.journalFile.appendText("this is not a journal line at all\n")
        f.journalFile.appendText("9999\tx\tSTARTED\t/s\t/t\t/g\t\n")

        val unfinished = f.journal.unfinished()
        assertEquals("only the genuine record should be replayed", 1, unfinished.size)
        assertEquals("x", unfinished.first().id)
    }

    @Test
    fun `recovery is safe to run twice`() {
        val f = fixture()
        VaultOperations.encryptFile(
            source = f.source, target = f.target, temp = f.temp, scratch = f.scratch,
            masterKey = master, preamble = preamble(), relativePath = "original.bin",
            journal = f.journal,
            onStep = { if (it == VaultStep.RECORD_DONE) throw RuntimeException("crash") },
        )

        VaultOperations.recover(VaultJournal(f.journalFile), master)
        VaultOperations.recover(VaultJournal(f.journalFile), master)

        assertNothingLost(f)
        val restored = File(temp.root, "restored3.bin")
        VaultContainer.decrypt(f.target, restored, master)
        assertArrayEquals(f.payload, restored.readBytes())
    }

    @Test
    fun `recovery on a clean journal does nothing`() {
        val journal = VaultJournal(File(temp.root, "empty.journal"))
        assertEquals(0, VaultOperations.recover(journal, master))
    }

    @Test
    fun `a file that fails to encrypt never loses its original`() {
        val f = fixture()
        // Stands in for any mid-write failure — no space, no permission,
        // storage unplugged. A plain file where the temp's parent directory
        // would go makes the write impossible on every platform, which an
        // absolute path like /proc does not.
        val blocker = File(temp.root, "blocker")
        blocker.writeText("a file, not a directory")
        val impossible = File(blocker, "sub/out.fsv")
        val result = VaultOperations.encryptFile(
            source = f.source, target = impossible,
            temp = File(blocker, "sub/out.fsv.tmp"),
            scratch = f.scratch,
            masterKey = master, preamble = preamble(), relativePath = "original.bin",
            journal = f.journal,
        )

        assertFalse(result.ok)
        VaultOperations.recover(VaultJournal(f.journalFile), master)
        assertTrue("original must survive a failed write", f.source.isFile)
        assertArrayEquals(f.payload, f.source.readBytes())
    }
}
