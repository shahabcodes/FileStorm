package com.shahabcodes.filestorm.data.vault

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * A folder that has been turned into a vault.
 *
 * Everything needed to open it lives here rather than in the app: the keyfile
 * holds the salt and the wrapped master key, and every encrypted file repeats
 * them. Uninstalling the app, wiping its data, or moving the folder to another
 * device changes nothing.
 */
class VaultFolder(val root: File) {

    val keyFile: File get() = File(root, KEYFILE)
    val keyBackup: File get() = File(root, KEYFILE_BACKUP)
    val indexFile: File get() = File(root, INDEX)
    val journalFile: File get() = File(root, JOURNAL)

    val journal: VaultJournal get() = VaultJournal(journalFile)

    fun isVault(): Boolean = keyFile.isFile || keyBackup.isFile

    /** Reads key material. Falls back to the backup, then to any file in the vault. */
    fun preamble(): VaultPreamble? {
        readPreambleFile(keyFile)?.let { return it }
        readPreambleFile(keyBackup)?.let { return it }
        // Last resort: every container repeats the key material, so one
        // surviving file is enough to open the vault even with both keyfiles
        // gone.
        return sealedFiles().asSequence()
            .mapNotNull { VaultContainer.readPreamble(it) }
            .firstOrNull()
    }

    fun unlock(passphrase: CharArray): ByteArray? =
        preamble()?.let { VaultContainer.openWithPassphrase(it, passphrase) }

    fun unlockWithRecoveryCode(code: String): ByteArray? =
        preamble()?.let { VaultContainer.openWithRecoveryCode(it, code) }

    /** Every encrypted file, across all shards. */
    fun sealedFiles(): List<File> {
        val out = ArrayList<File>()
        root.listFiles()?.forEach { shard ->
            if (shard.isDirectory && shard.name.length == 2 && shard.name.all { it.isLetterOrDigit() }) {
                shard.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == VaultContainer.EXTENSION) out.add(file)
                }
            } else if (shard.isFile && shard.extension == VaultContainer.EXTENSION) {
                out.add(shard)
            }
        }
        return out
    }

    /**
     * Files sitting in the vault that are not encrypted — anything added since
     * it was locked. Reported rather than silently swept up, because a file
     * appearing here is usually the user putting it there on purpose.
     */
    fun plaintextStragglers(includeHidden: Boolean = true): List<File> {
        val out = ArrayList<File>()
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { child ->
                val name = child.name
                if (name in RESERVED) return@forEach
                if (!includeHidden && name.startsWith(".")) return@forEach
                if (child.isDirectory) {
                    if (!isShard(child)) queue.add(child)
                } else if (child.extension != VaultContainer.EXTENSION) {
                    out.add(child)
                }
            }
        }
        return out
    }

    private fun isShard(dir: File): Boolean =
        dir.parentFile == root && dir.name.length == 2 && dir.name.all { it.isLetterOrDigit() }

    /** A fresh, unused location for one encrypted file. */
    fun allocate(): File {
        while (true) {
            val name = VaultCrypto.randomBytes(16).joinToString("") { "%02x".format(it) }
            val shard = File(root, name.substring(0, 2))
            val candidate = File(shard, "${name}.${VaultContainer.EXTENSION}")
            if (!candidate.exists()) {
                shard.mkdirs()
                return candidate
            }
        }
    }

    fun writeKeyFile(preamble: VaultPreamble) {
        // Two copies, written one after the other, so losing one to a bad write
        // still leaves a good one.
        writePreambleFile(keyFile, preamble)
        writePreambleFile(keyBackup, preamble)
    }

    private fun writePreambleFile(file: File, preamble: VaultPreamble) {
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(KEYFILE_MAGIC.toByteArray(Charsets.US_ASCII))
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
    }

    private fun readPreambleFile(file: File): VaultPreamble? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            val magic = ByteArray(4).also { input.readFully(it) }
            if (String(magic, Charsets.US_ASCII) != KEYFILE_MAGIC) return null
            val version = input.readUnsignedByte()
            val kdfId = input.readUnsignedByte()
            val iterations = input.readInt()
            val salt = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            val pass = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            val recovery = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
            VaultPreamble(version, kdfId, iterations, salt, pass, recovery)
        }
    }.getOrNull()

    /** Removes the vault's own files once a folder has been fully unlocked. */
    fun removeVaultFiles() {
        listOf(keyFile, keyBackup, indexFile, journalFile).forEach { it.delete() }
        root.listFiles()?.forEach { if (isShard(it) && it.list()?.isEmpty() != false) it.delete() }
    }

    companion object {
        const val KEYFILE = ".fsvault"
        const val KEYFILE_BACKUP = ".fsvault.bak"
        const val INDEX = ".fsindex"
        const val JOURNAL = ".fsjournal"
        private const val KEYFILE_MAGIC = "FSVK"

        val RESERVED = setOf(KEYFILE, KEYFILE_BACKUP, INDEX, JOURNAL)

        fun isVault(root: File): Boolean = VaultFolder(root).isVault()

        /** Turns a plain folder into a vault. Does not encrypt anything yet. */
        fun create(
            root: File,
            passphrase: CharArray,
            recoveryCode: String,
            strength: VaultCrypto.Strength = VaultCrypto.Strength.STANDARD,
        ): Pair<VaultFolder, ByteArray> {
            val masterKey = VaultCrypto.newMasterKey()
            val preamble = VaultContainer.newPreamble(masterKey, passphrase, recoveryCode, strength)
            val folder = VaultFolder(root)
            root.mkdirs()
            folder.writeKeyFile(preamble)
            return folder to masterKey
        }
    }
}
