package com.shahaabapps.filestorm.data.vault

import java.io.File
import java.io.FileOutputStream

/** The points at which work is committed to disk, in order. */
enum class VaultStep {
    ANNOUNCE,
    WRITE_TEMP,
    VERIFY_TEMP,
    PLACE,
    RECORD_PLACED,
    REMOVE_ORIGINAL,
    RECORD_DONE,
}

data class VaultOutcome(
    val ok: Boolean,
    val info: VaultFileInfo? = null,
    val error: String? = null,
)

/**
 * Encrypting one file, arranged so that stopping anywhere is survivable.
 *
 * The order matters more than anything else here. The original is only removed
 * once the encrypted file is at its final name *and* has been proven to decrypt
 * back to the original's hash. So at every instant one of these is true:
 *
 *  - the original is still there, and any leftover temp can simply be deleted
 *  - the encrypted file is complete and verified, and the original is redundant
 *
 * There is no moment where both are gone, which is the only guarantee that
 * really matters when the thing being encrypted is someone's photos.
 */
object VaultOperations {

    /**
     * [onStep] runs just before each committed step. Recovery is tested by
     * throwing from it, which reproduces a crash at that exact point far more
     * reliably than trying to kill a process at the right moment.
     */
    fun encryptFile(
        source: File,
        target: File,
        temp: File,
        scratch: File,
        masterKey: ByteArray,
        preamble: VaultPreamble,
        relativePath: String,
        journal: VaultJournal,
        id: String = source.absolutePath.hashCode().toString(16) + "-" + source.length(),
        verify: Boolean = true,
        removeOriginal: Boolean = true,
        chunkSize: Int = VaultContainer.DEFAULT_CHUNK_SIZE,
        created: Long = 0L,
        accessed: Long = 0L,
        thumbnail: ByteArray? = null,
        progress: VaultContainer.Progress? = null,
        onStep: ((VaultStep) -> Unit)? = null,
    ): VaultOutcome {
        fun record(state: VaultState, detail: String = "") =
            journal.append(VaultRecord(id, state, source.absolutePath, temp.absolutePath, target.absolutePath, detail))

        return try {
            onStep?.invoke(VaultStep.ANNOUNCE)
            record(VaultState.STARTED)

            onStep?.invoke(VaultStep.WRITE_TEMP)
            temp.parentFile?.mkdirs()
            val info = VaultContainer.encrypt(
                source = source,
                target = temp,
                masterKey = masterKey,
                preamble = preamble,
                relativePath = relativePath,
                created = created,
                accessed = accessed,
                thumbnail = thumbnail,
                chunkSize = chunkSize,
                progress = progress,
            )
            fsync(temp)
            record(VaultState.WRITTEN)

            onStep?.invoke(VaultStep.VERIFY_TEMP)
            if (verify) {
                // Decrypt checks the per-chunk tags, the length and the hash,
                // so a successful read here is the proof we need.
                VaultContainer.decrypt(temp, scratch, masterKey)
                scratch.delete()
            }
            record(VaultState.VERIFIED)

            onStep?.invoke(VaultStep.PLACE)
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }

            onStep?.invoke(VaultStep.RECORD_PLACED)
            record(VaultState.PLACED)

            onStep?.invoke(VaultStep.REMOVE_ORIGINAL)
            if (removeOriginal) source.delete()

            onStep?.invoke(VaultStep.RECORD_DONE)
            record(VaultState.DONE)
            VaultOutcome(ok = true, info = info)
        } catch (t: Throwable) {
            VaultOutcome(ok = false, error = t.message ?: t::class.java.simpleName)
        }
    }

    /**
     * Finishes or unwinds whatever the journal says was in flight. Safe to run
     * at any time, including when nothing was interrupted, and safe to run
     * twice.
     */
    fun recover(
        journal: VaultJournal,
        masterKey: ByteArray,
        removeOriginal: Boolean = true,
        onRecovered: ((VaultRecord, String) -> Unit)? = null,
    ): Int {
        val pending = journal.unfinished()
        var handled = 0
        pending.forEach { record ->
            val source = File(record.source)
            val temp = File(record.temp)
            val target = File(record.target)

            when (record.state) {
                // Nothing was proven, so throw away the half-written temp and
                // leave the original for the run to pick up again.
                VaultState.STARTED, VaultState.WRITTEN -> {
                    temp.delete()
                    journal.append(record.copy(state = VaultState.FAILED, detail = "interrupted; original kept"))
                    onRecovered?.invoke(record, "discarded unverified temp")
                }

                // Proven correct but not yet in place: finish the move.
                VaultState.VERIFIED -> {
                    val placed = when {
                        target.exists() -> true
                        temp.exists() -> temp.renameTo(target)
                        else -> false
                    }
                    if (placed && verifyOnDisk(target, masterKey)) {
                        journal.append(record.copy(state = VaultState.PLACED))
                        if (removeOriginal) source.delete()
                        journal.append(record.copy(state = VaultState.DONE))
                        onRecovered?.invoke(record, "completed a verified file")
                    } else {
                        temp.delete()
                        journal.append(record.copy(state = VaultState.FAILED, detail = "could not place; original kept"))
                        onRecovered?.invoke(record, "kept the original")
                    }
                }

                // In place already; only the original's removal was missed.
                VaultState.PLACED -> {
                    if (verifyOnDisk(target, masterKey)) {
                        if (removeOriginal) source.delete()
                        journal.append(record.copy(state = VaultState.DONE))
                        onRecovered?.invoke(record, "removed the leftover original")
                    } else {
                        journal.append(record.copy(state = VaultState.FAILED, detail = "target unreadable; original kept"))
                        onRecovered?.invoke(record, "kept the original")
                    }
                }

                VaultState.DONE, VaultState.FAILED -> Unit
            }
            handled++
        }
        return handled
    }

    /** Cheap re-check that a placed file really does decrypt. */
    private fun verifyOnDisk(target: File, masterKey: ByteArray): Boolean = runCatching {
        if (!target.isFile) return false
        VaultContainer.readInfo(target, masterKey)
        true
    }.getOrDefault(false)

    private fun fsync(file: File) {
        runCatching {
            FileOutputStream(file, true).use { it.fd.sync() }
        }
    }
}
