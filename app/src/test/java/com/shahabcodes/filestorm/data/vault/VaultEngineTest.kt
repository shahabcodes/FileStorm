package com.shahabcodes.filestorm.data.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Stage 3: locking and unlocking real folder trees.
 *
 * The shape being proven is that a folder can go in and come out unchanged —
 * same files, same names, same nesting, same dates — including hidden files and
 * hidden folders, and that a single file can be pulled back out on its own
 * without disturbing anything else.
 */
class VaultEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val passphrase = "correct horse battery staple".toCharArray()
    private val recoveryCode = VaultCrypto.newRecoveryCode()

    /** Fast KDF: the real cost is a setting, and would only slow the suite. */
    private fun makeVault(root: File): Pair<VaultFolder, ByteArray> {
        val master = VaultCrypto.newMasterKey()
        val salt = VaultCrypto.newSalt()
        val aad = "FSV1".toByteArray(Charsets.US_ASCII)
        val passKek = VaultCrypto.deriveKek(passphrase.copyOf(), salt, 1_000)
        val recKek = VaultCrypto.deriveKek(
            VaultCrypto.normaliseRecoveryCode(recoveryCode), salt, 1_000,
        )
        val preamble = VaultPreamble(
            1, VaultCrypto.KDF_PBKDF2_SHA256, 1_000, salt,
            VaultCrypto.wrapKey(master, passKek, aad),
            VaultCrypto.wrapKey(master, recKek, aad),
        )
        val folder = VaultFolder(root)
        root.mkdirs()
        folder.writeKeyFile(preamble)
        return folder to master
    }

    private class Sample(val relative: String, val bytes: ByteArray, val modified: Long)

    private fun buildTree(root: File): List<Sample> {
        val samples = listOf(
            Sample("holiday.jpg", Random(1).nextBytes(40_000), 1_500_000_000_000L),
            Sample("Camera/IMG_0001.jpg", Random(2).nextBytes(120_000), 1_510_000_000_000L),
            Sample("Camera/sub/deep/clip.mp4", Random(3).nextBytes(300_000), 1_520_000_000_000L),
            Sample(".hidden-note.txt", Random(4).nextBytes(2_000), 1_530_000_000_000L),
            Sample(".secret/passport.pdf", Random(5).nextBytes(60_000), 1_540_000_000_000L),
            Sample(".secret/nested/.also-hidden.bin", Random(6).nextBytes(9_000), 1_550_000_000_000L),
            Sample("empty.dat", ByteArray(0), 1_560_000_000_000L),
        )
        samples.forEach { sample ->
            val file = File(root, sample.relative)
            file.parentFile?.mkdirs()
            file.writeBytes(sample.bytes)
            file.setLastModified(sample.modified)
        }
        return samples
    }

    // ── Round trip of a whole folder ───────────────────────────────────

    @Test
    fun `folder locks and unlocks with every file name nesting and date intact`() {
        val root = temp.newFolder("vault")
        val samples = buildTree(root)
        val (folder, master) = makeVault(root)

        val locked = VaultEngine.lockFolder(folder, master)
        assertEquals("all files should encrypt", samples.size, locked.succeeded)
        assertEquals(0, locked.failed)

        // Nothing readable is left behind.
        samples.forEach { assertFalse("${it.relative} should be gone", File(root, it.relative).exists()) }
        assertEquals(samples.size, folder.sealedFiles().size)

        val unlocked = VaultEngine.unlockFolder(folder, master)
        assertEquals(samples.size, unlocked.succeeded)
        assertEquals(0, unlocked.failed)

        samples.forEach { sample ->
            val restored = File(root, sample.relative)
            assertTrue("${sample.relative} missing after unlock", restored.isFile)
            assertArrayEquals("${sample.relative} contents differ", sample.bytes, restored.readBytes())
            assertEquals("${sample.relative} modified date differs", sample.modified, restored.lastModified())
        }
        assertFalse("keyfile should be gone", folder.keyFile.exists())
    }

    @Test
    fun `hidden files and hidden folders are included`() {
        val root = temp.newFolder("hidden")
        buildTree(root)
        val (folder, master) = makeVault(root)

        VaultEngine.lockFolder(folder, master)
        val names = VaultEngine.listContents(folder, master).map { it.second.relativePath }

        assertTrue(names.contains(".hidden-note.txt"))
        assertTrue(names.contains(".secret/passport.pdf"))
        assertTrue(names.contains(".secret/nested/.also-hidden.bin"))
    }

    @Test
    fun `hidden files can be excluded when asked`() {
        val root = temp.newFolder("nohidden")
        buildTree(root)
        val (folder, master) = makeVault(root)

        VaultEngine.lockFolder(folder, master, VaultOptions(includeHidden = false))
        val names = VaultEngine.listContents(folder, master).map { it.second.relativePath }

        assertFalse(names.any { it.startsWith(".") })
        assertTrue("visible files should still be encrypted", names.contains("holiday.jpg"))
        assertTrue("skipped file must remain", File(root, ".hidden-note.txt").isFile)
    }

    // ── Single-file decryption ─────────────────────────────────────────

    @Test
    fun `one file deep inside a hidden folder restores on its own`() {
        val root = temp.newFolder("single")
        val samples = buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        val wanted = ".secret/nested/.also-hidden.bin"
        val entry = VaultEngine.listContents(folder, master)
            .first { it.second.relativePath == wanted }

        val result = VaultEngine.restore(folder, entry.first, master)
        assertTrue(result.ok)

        val restored = File(root, wanted)
        assertTrue("the one file should be back", restored.isFile)
        assertArrayEquals(samples.first { it.relative == wanted }.bytes, restored.readBytes())
        assertEquals(samples.first { it.relative == wanted }.modified, restored.lastModified())

        // Everything else stays locked.
        assertEquals(samples.size, folder.sealedFiles().size)
        assertFalse(File(root, "holiday.jpg").exists())
    }

    @Test
    fun `restoring does not overwrite something already there`() {
        val root = temp.newFolder("collide")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        val entry = VaultEngine.listContents(folder, master)
            .first { it.second.relativePath == "holiday.jpg" }
        File(root, "holiday.jpg").writeText("something the user put back by hand")

        VaultEngine.restore(folder, entry.first, master)

        assertEquals(
            "the existing file must not be replaced",
            "something the user put back by hand",
            File(root, "holiday.jpg").readText(),
        )
        assertTrue("the restored copy should sit alongside it", File(root, "holiday (1).jpg").isFile)
    }

    // ── The vault opens without the app ────────────────────────────────

    @Test
    fun `vault opens after both keyfiles are lost`() {
        val root = temp.newFolder("nokey")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        folder.keyFile.delete()
        folder.keyBackup.delete()
        folder.indexFile.delete()

        val reopened = VaultFolder(root)
        assertNotNull("key material should come from a file", reopened.preamble())
        val recovered = reopened.unlock(passphrase.copyOf())
        assertNotNull("passphrase should still open the vault", recovered)
        assertArrayEquals(master, recovered)
    }

    @Test
    fun `recovery code opens the vault`() {
        val root = temp.newFolder("recovery")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        val opened = VaultFolder(root).unlockWithRecoveryCode(recoveryCode.lowercase())
        assertArrayEquals(master, opened)
    }

    @Test
    fun `a wrong passphrase does not open the vault`() {
        val root = temp.newFolder("wrong")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        assertNull(VaultFolder(root).unlock("not the passphrase".toCharArray()))
    }

    // ── Files added after locking ──────────────────────────────────────

    @Test
    fun `files added later are reported and can be locked too`() {
        val root = temp.newFolder("stragglers")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)
        assertTrue(folder.plaintextStragglers().isEmpty())

        val added = File(root, "Camera/new-photo.jpg")
        added.parentFile.mkdirs()
        added.writeBytes(Random(9).nextBytes(5_000))

        val stragglers = folder.plaintextStragglers()
        assertEquals(1, stragglers.size)
        assertEquals("new-photo.jpg", stragglers.first().name)

        val second = VaultEngine.lockFolder(folder, master)
        assertEquals(1, second.succeeded)
        assertTrue(folder.plaintextStragglers().isEmpty())
    }

    @Test
    fun `the vault's own files are never encrypted`() {
        val root = temp.newFolder("selfsafe")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        assertTrue("keyfile must survive", folder.keyFile.isFile)
        assertTrue("backup keyfile must survive", folder.keyBackup.isFile)
        val names = VaultEngine.listContents(folder, master).map { it.second.name }
        assertFalse(names.contains(VaultFolder.KEYFILE))
        assertFalse(names.contains(VaultFolder.KEYFILE_BACKUP))
    }

    // ── Nothing leaks from outside ─────────────────────────────────────

    @Test
    fun `no original name or folder name is visible on disk`() {
        val root = temp.newFolder("opaque")
        buildTree(root)
        val (folder, master) = makeVault(root)
        VaultEngine.lockFolder(folder, master)

        val visible = StringBuilder()
        root.walkTopDown().forEach { visible.append(it.name).append('/') }
        val listing = visible.toString()

        listOf("holiday", "IMG_0001", "passport", "secret", "Camera", "clip").forEach {
            assertFalse("'$it' is visible in the folder listing", listing.contains(it))
        }
    }

    // ── Interruption partway through a folder ──────────────────────────

    @Test
    fun `stopping partway leaves everything either encrypted or untouched`() {
        val root = temp.newFolder("stop")
        val samples = buildTree(root)
        val (folder, master) = makeVault(root)

        var seen = 0
        val summary = VaultEngine.lockFolder(
            folder, master,
            shouldStop = { seen++ > 3 },
        )
        assertTrue("run should report itself cancelled", summary.cancelled)

        // Every sample is in exactly one of the two safe states.
        val encrypted = VaultEngine.listContents(folder, master).map { it.second.relativePath }.toSet()
        samples.forEach { sample ->
            val plain = File(root, sample.relative)
            val isPlain = plain.isFile && plain.readBytes().contentEquals(sample.bytes)
            assertTrue(
                "${sample.relative} was neither left alone nor encrypted",
                isPlain || encrypted.contains(sample.relative),
            )
        }

        // Carrying on afterwards finishes the job.
        val rest = VaultEngine.lockFolder(folder, master)
        assertEquals(0, rest.failed)
        assertTrue(folder.plaintextStragglers().isEmpty())

        VaultEngine.unlockFolder(folder, master)
        samples.forEach { sample ->
            val restored = File(root, sample.relative)
            assertArrayEquals("${sample.relative} differs", sample.bytes, restored.readBytes())
        }
    }

    @Test
    fun `originals are kept when disposal is refused`() {
        val root = temp.newFolder("keep")
        val samples = buildTree(root)
        val (folder, master) = makeVault(root)

        // Stands in for the Trash being full or a delete failing.
        VaultEngine.lockFolder(folder, master, VaultOptions(removeOriginal = { false }))

        samples.forEach {
            assertTrue("${it.relative} should still be present", File(root, it.relative).isFile)
        }
        assertEquals(samples.size, folder.sealedFiles().size)
    }

    @Test
    fun `log identifiers do not contain the path`() {
        val id = VaultEngine.shortId("/storage/emulated/0/DCIM/Passport_scan.pdf")
        assertFalse(id.contains("Passport"))
        assertFalse(id.contains("DCIM"))
        assertEquals(8, id.length)
    }
}
