package com.shahaabapps.filestorm.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Encrypting several files at once must not weaken anything the earlier stages
 * proved. The three things concurrency can break here are the journal (two
 * threads writing a line at the same moment), the name allocator (two threads
 * claiming one name) and the running totals — so those are what these check,
 * alongside a full round trip.
 */
class VaultConcurrencyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val passphrase = "correct horse battery staple".toCharArray()

    private fun makeVault(root: File): Pair<VaultFolder, ByteArray> {
        val master = VaultCrypto.newMasterKey()
        val salt = VaultCrypto.newSalt()
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        val kek = VaultCrypto.deriveKek(passphrase.copyOf(), salt, 1_000)
        val preamble = VaultPreamble(
            1, VaultCrypto.KDF_PBKDF2_SHA256, 1_000, salt,
            VaultCrypto.wrapKey(master, kek, aad), VaultCrypto.wrapKey(master, kek, aad),
        )
        val folder = VaultFolder(root)
        root.mkdirs()
        folder.writeKeyFile(preamble)
        return folder to master
    }

    private class Sample(val relative: String, val bytes: ByteArray, val modified: Long)

    private fun buildTree(root: File, count: Int): List<Sample> {
        val samples = (0 until count).map { i ->
            val folder = when (i % 4) {
                0 -> ""
                1 -> "Camera/"
                2 -> ".hidden/"
                else -> "Camera/deep/"
            }
            Sample(
                relative = "$folder file_$i.bin",
                bytes = Random(i).nextBytes(2_000 + i * 137),
                modified = 1_500_000_000_000L + i * 86_400_000L,
            )
        }
        samples.forEach { sample ->
            val file = File(root, sample.relative)
            file.parentFile?.mkdirs()
            file.writeBytes(sample.bytes)
            file.setLastModified(sample.modified)
        }
        return samples
    }

    @Test
    fun `four at a time round trips exactly like one at a time`() {
        val root = temp.newFolder("parallel")
        val samples = buildTree(root, 40)
        val (folder, master) = makeVault(root)

        val summary = VaultEngine.lockFolder(folder, master, VaultOptions(workers = 4))
        assertEquals("every file should encrypt", samples.size, summary.succeeded)
        assertEquals(0, summary.failed)
        assertEquals(samples.size, folder.sealedFiles().size)

        val unlocked = VaultEngine.unlockFolder(folder, master, VaultOptions(workers = 4))
        assertEquals(samples.size, unlocked.succeeded)

        samples.forEach { sample ->
            val restored = File(root, sample.relative)
            assertTrue("${sample.relative} missing", restored.isFile)
            assertArrayEquals("${sample.relative} differs", sample.bytes, restored.readBytes())
            assertEquals("${sample.relative} date differs", sample.modified, restored.lastModified())
        }
    }

    @Test
    fun `every file gets its own name`() {
        val root = temp.newFolder("names")
        val samples = buildTree(root, 60)
        val (folder, master) = makeVault(root)

        VaultEngine.lockFolder(folder, master, VaultOptions(workers = 4))

        val names = folder.sealedFiles().map { it.name }
        assertEquals("a name was handed out twice", names.size, names.toSet().size)
        assertEquals(samples.size, names.size)
    }

    @Test
    fun `the running total matches what was encrypted`() {
        val root = temp.newFolder("totals")
        val samples = buildTree(root, 30)
        val (folder, master) = makeVault(root)

        val summary = VaultEngine.lockFolder(folder, master, VaultOptions(workers = 4))

        assertEquals(
            "bytes reported should equal bytes encrypted",
            samples.sumOf { it.bytes.size.toLong() },
            summary.bytes,
        )
    }

    @Test
    fun `journal lines survive threads writing at once`() {
        val journalFile = File(temp.root, "concurrent.journal")
        val journal = VaultJournal(journalFile)
        val threads = 8
        val each = 60

        val pool = Executors.newFixedThreadPool(threads)
        (0 until threads).map { t ->
            pool.submit {
                repeat(each) { n ->
                    journal.append(
                        VaultRecord(
                            id = "t$t-$n",
                            state = VaultState.STARTED,
                            source = "/storage/emulated/0/DCIM/photo_${t}_$n.jpg",
                            temp = "/tmp/$t-$n.tmp",
                            target = "/vault/$t-$n.fsv",
                        )
                    )
                }
            }
        }.forEach { it.get() }
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)

        // Every record must be readable: a torn or interleaved line would fail
        // its CRC and vanish from the replay.
        val replayed = journal.replay()
        assertEquals("records were lost to interleaving", threads * each, replayed.size)
        replayed.values.forEach {
            assertTrue(it.source.startsWith("/storage/"))
            assertTrue(it.target.startsWith("/vault/"))
        }
    }

    @Test
    fun `stopping partway with several in flight loses nothing`() {
        val root = temp.newFolder("stopmulti")
        val samples = buildTree(root, 40)
        val (folder, master) = makeVault(root)

        var seen = 0
        VaultEngine.lockFolder(
            folder, master, VaultOptions(workers = 4),
            shouldStop = { seen++ > 10 },
        )

        val encrypted = VaultEngine.listContents(folder, master)
            .map { it.second.relativePath }.toSet()
        samples.forEach { sample ->
            val plain = File(root, sample.relative)
            val isPlain = plain.isFile && plain.readBytes().contentEquals(sample.bytes)
            assertTrue(
                "${sample.relative} was neither kept nor encrypted",
                isPlain || encrypted.contains(sample.relative),
            )
        }

        // Finishing afterwards still produces an exact round trip.
        VaultEngine.lockFolder(folder, master, VaultOptions(workers = 4))
        assertTrue(folder.plaintextStragglers().isEmpty())
        VaultEngine.unlockFolder(folder, master, VaultOptions(workers = 4))
        samples.forEach {
            assertArrayEquals(it.bytes, File(root, it.relative).readBytes())
        }
    }

    @Test
    fun `a failure among many does not take the others down`() {
        val root = temp.newFolder("mixed")
        val samples = buildTree(root, 20)
        val (folder, master) = makeVault(root)

        // Refusing disposal for one file leaves its original in place; the rest
        // must still finish normally.
        val summary = VaultEngine.lockFolder(
            folder, master,
            VaultOptions(
                workers = 4,
                removeOriginal = { file -> if (file.name.endsWith("file_3.bin")) false else file.delete() },
            ),
        )

        assertEquals(samples.size, summary.succeeded)
        assertEquals(0, summary.failed)
        assertTrue("the kept original should remain", File(root, samples[3].relative).isFile)
    }

    @Test
    fun `one worker behaves exactly as before`() {
        val root = temp.newFolder("single")
        val samples = buildTree(root, 8)
        val (folder, master) = makeVault(root)

        val summary = VaultEngine.lockFolder(folder, master, VaultOptions(workers = 1))
        assertEquals(samples.size, summary.succeeded)

        VaultEngine.unlockFolder(folder, master, VaultOptions(workers = 1))
        samples.forEach {
            assertArrayEquals(it.bytes, File(root, it.relative).readBytes())
        }
    }
}
