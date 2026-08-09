package com.shahabcodes.filestorm.data.vault

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class VaultPhase { SCANNING, RESUMING, ENCRYPTING, DECRYPTING, CLEANING, DONE }

/** One file currently being worked on. Several at once when workers > 1. */
data class ActiveFile(val name: String, val done: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** Everything the progress screen needs, published as each file is handled. */
data class VaultProgress(
    val phase: VaultPhase,
    val fileIndex: Int = 0,
    val fileCount: Int = 0,
    val currentName: String = "",
    val fileBytesDone: Long = 0,
    val fileBytesTotal: Long = 0,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val startedAt: Long = 0,
    val workers: Int = 1,
    /** Smoothed, so the figure settles instead of swinging file to file. */
    val speedBps: Double = 0.0,
    /** Everything in flight right now, which is more than one when workers > 1. */
    val activeFiles: List<ActiveFile> = emptyList(),
    /** Named as they happen rather than only in the summary at the end. */
    val recentFailures: List<VaultFailure> = emptyList(),
) {
    val elapsedSeconds: Long
        get() = if (startedAt > 0) (System.currentTimeMillis() - startedAt) / 1000 else 0
    val fraction: Float
        get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
    val etaSeconds: Long
        get() {
            if (bytesDone <= 0 || startedAt <= 0) return -1
            val elapsed = System.currentTimeMillis() - startedAt
            val perByte = elapsed.toDouble() / bytesDone
            return ((bytesTotal - bytesDone) * perByte / 1000).toLong()
        }
}

data class VaultFailure(val path: String, val reason: String)

data class VaultSummary(
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val bytes: Long,
    val failures: List<VaultFailure>,
    val cancelled: Boolean,
)

/** Everything the user can configure about a run. */
data class VaultOptions(
    val verifyAfterWrite: Boolean = true,
    val includeHidden: Boolean = true,
    val chunkSize: Int = VaultContainer.DEFAULT_CHUNK_SIZE,
    /**
     * How an original is disposed of once its encrypted copy is verified.
     * Returning false means the original stays, which is safe — the run simply
     * reports it. The app supplies a version of this that moves to Trash.
     */
    val removeOriginal: (File) -> Boolean = { it.delete() },
    /**
     * Supplies a small preview for a file, encrypted alongside it. Android
     * decoding lives outside the core, so it arrives as bytes.
     */
    val thumbnailFor: (File) -> ByteArray? = { null },
    /**
     * How many files are handled at once. Storage handles more than one request
     * at a time, and per-file overhead — opening, flushing, renaming — dominates
     * on folders full of small files, so this is where the real speed is. One
     * keeps the old behaviour exactly.
     */
    val workers: Int = 1,
)

/**
 * Locking and unlocking whole folders, and pulling individual files back out.
 *
 * Encrypted files are stored under two-character shard folders with opaque
 * names, so neither the filenames nor the folder structure of the original is
 * visible from outside. The true path travels inside each file's encrypted
 * header, which is what lets the structure be rebuilt exactly on unlock — and
 * lets a single file be restored to the right place on its own.
 */
object VaultEngine {

    fun interface Listener {
        fun onProgress(progress: VaultProgress)
    }

    /** Encrypts everything in [folder] that is not already encrypted. */
    fun lockFolder(
        folder: VaultFolder,
        masterKey: ByteArray,
        options: VaultOptions = VaultOptions(),
        listener: Listener? = null,
        shouldStop: () -> Boolean = { false },
        log: ((String) -> Unit)? = null,
    ): VaultSummary {
        val preamble = folder.preamble()
            ?: return VaultSummary(0, 0, 0, 0, listOf(VaultFailure(folder.root.path, "Not a vault")), false)
        val journal = folder.journal
        val startedAt = System.currentTimeMillis()

        listener?.onProgress(VaultProgress(VaultPhase.RESUMING, startedAt = startedAt))
        val recovered = VaultOperations.recover(journal, masterKey) { record, action ->
            log?.invoke("resume: $action for ${shortId(record.source)}")
        }
        if (recovered > 0) log?.invoke("resumed $recovered interrupted file(s)")

        listener?.onProgress(VaultProgress(VaultPhase.SCANNING, startedAt = startedAt))
        val pending = folder.plaintextStragglers(options.includeHidden)
        val totalBytes = pending.sumOf { it.length() }
        log?.invoke("locking ${pending.size} file(s), ${totalBytes} bytes")

        val succeeded = AtomicInteger()
        val failed = AtomicInteger()
        val skipped = AtomicInteger()
        val started = AtomicInteger()
        val bytesDone = AtomicLong()
        val failures = ConcurrentLinkedQueue<VaultFailure>()
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)

        // Everything in flight, so the screen can show four bars rather than
        // one that jumps between files.
        val active = LinkedHashMap<String, ActiveFile>()
        var emaBps = 0.0
        var lastSampleAt = startedAt
        var lastSampleBytes = 0L

        // Progress is published from whichever thread just moved, so the emit
        // itself is synchronised to keep the numbers self-consistent.
        val emit = { phase: VaultPhase, name: String, fileDone: Long, fileTotal: Long ->
            synchronized(active) {
                if (name.isNotEmpty()) {
                    if (fileTotal > 0 && fileDone < fileTotal) {
                        active[name] = ActiveFile(name, fileDone, fileTotal)
                    } else {
                        active.remove(name)
                    }
                }
                val now = System.currentTimeMillis()
                val done = bytesDone.get()
                val gap = now - lastSampleAt
                if (gap >= 400) {
                    // Smoothed rather than instantaneous: a raw figure swings
                    // wildly as files start and finish.
                    val instant = (done - lastSampleBytes) * 1000.0 / gap
                    emaBps = if (emaBps <= 0.0) instant else 0.75 * emaBps + 0.25 * instant
                    lastSampleAt = now
                    lastSampleBytes = done
                }
                listener?.onProgress(
                    VaultProgress(
                        phase = phase,
                        fileIndex = started.get(), fileCount = pending.size,
                        currentName = name,
                        fileBytesDone = fileDone, fileBytesTotal = fileTotal,
                        bytesDone = done, bytesTotal = totalBytes,
                        succeeded = succeeded.get(), failed = failed.get(),
                        skipped = skipped.get(), startedAt = startedAt,
                        workers = options.workers,
                        speedBps = emaBps,
                        activeFiles = active.values.toList(),
                        recentFailures = failures.toList().takeLast(5),
                    )
                )
            }
        }

        val work = { source: File ->
            if (shouldStop()) {
                cancelled.set(true)
            } else if (!source.isFile) {
                skipped.incrementAndGet()
            } else {
                started.incrementAndGet()
                val relative = source.relativeToOrSelf(folder.root).path
                    .replace(File.separatorChar, '/')
                val target = folder.allocate()
                val temp = File(target.parentFile, target.name + ".tmp")
                val scratch = File(target.parentFile, target.name + ".check")
                var reported = 0L

                emit(VaultPhase.ENCRYPTING, source.name, 0L, source.length())

                val outcome = VaultOperations.encryptFile(
                    source = source, target = target, temp = temp, scratch = scratch,
                    masterKey = masterKey, preamble = preamble, relativePath = relative,
                    journal = journal,
                    verify = options.verifyAfterWrite,
                    removeOriginal = false,
                    chunkSize = options.chunkSize,
                    created = 0L, accessed = 0L,
                    thumbnail = options.thumbnailFor(source),
                    progress = { done, total ->
                        // Publish the delta, so the running total stays correct
                        // however many files are in flight.
                        bytesDone.addAndGet(done - reported)
                        reported = done
                        emit(VaultPhase.ENCRYPTING, source.name, done, total)
                    },
                )

                if (outcome.ok) {
                    // Disposal is the caller's policy — delete, or move to
                    // Trash. Either way it happens only after verification.
                    if (options.removeOriginal(source)) {
                        journal.append(
                            VaultRecord(
                                id = source.absolutePath.hashCode().toString(16) + "-" +
                                    (outcome.info?.size ?: 0),
                                state = VaultState.DONE,
                                source = source.absolutePath,
                                temp = temp.absolutePath,
                                target = target.absolutePath,
                            )
                        )
                    }
                    succeeded.incrementAndGet()
                    bytesDone.addAndGet((outcome.info?.size ?: 0L) - reported)
                    synchronized(active) { active.remove(source.name) }
                } else {
                    failed.incrementAndGet()
                    failures.add(VaultFailure(relative, outcome.error ?: "unknown"))
                    log?.invoke("failed ${shortId(source.absolutePath)}: ${outcome.error}")
                    target.delete()
                    temp.delete()
                    bytesDone.addAndGet(-reported)
                    synchronized(active) { active.remove(source.name) }
                }
                scratch.delete()
                emit(VaultPhase.ENCRYPTING, "", 0L, 0L)
            }
            Unit
        }

        runPool(pending, options.workers, work)

        listener?.onProgress(VaultProgress(VaultPhase.CLEANING, startedAt = startedAt))
        removeEmptyFolders(folder)
        if (!cancelled.get() && failed.get() == 0) journal.clear()

        listener?.onProgress(
            VaultProgress(
                phase = VaultPhase.DONE,
                fileCount = pending.size, bytesDone = bytesDone.get(), bytesTotal = totalBytes,
                succeeded = succeeded.get(), failed = failed.get(), skipped = skipped.get(),
                startedAt = startedAt, workers = options.workers,
            )
        )
        return VaultSummary(
            succeeded.get(), failed.get(), skipped.get(), bytesDone.get(),
            failures.toList(), cancelled.get(),
        )
    }

    /** Restores every file, rebuilding the original folder structure. */
    fun unlockFolder(
        folder: VaultFolder,
        masterKey: ByteArray,
        options: VaultOptions = VaultOptions(),
        listener: Listener? = null,
        shouldStop: () -> Boolean = { false },
        log: ((String) -> Unit)? = null,
    ): VaultSummary {
        val startedAt = System.currentTimeMillis()
        listener?.onProgress(VaultProgress(VaultPhase.SCANNING, startedAt = startedAt))
        val sealed = folder.sealedFiles()
        val totalBytes = sealed.sumOf { it.length() }

        var succeeded = 0
        var failed = 0
        var bytesDone = 0L
        val failures = ArrayList<VaultFailure>()
        var cancelled = false

        sealed.forEachIndexed { index, file ->
            if (shouldStop()) {
                cancelled = true
                return@forEachIndexed
            }
            val before = bytesDone
            val name = runCatching { VaultContainer.readInfo(file, masterKey).name }
                .getOrDefault(file.name)
            val result = restore(folder, file, masterKey, destinationRoot = folder.root) { done, total ->
                val now = System.currentTimeMillis()
                val elapsed = (now - startedAt).coerceAtLeast(1)
                listener?.onProgress(
                    VaultProgress(
                        phase = VaultPhase.DECRYPTING,
                        fileIndex = index + 1, fileCount = sealed.size,
                        currentName = name,
                        fileBytesDone = done, fileBytesTotal = total,
                        bytesDone = before + done, bytesTotal = totalBytes,
                        succeeded = succeeded, failed = failed,
                        startedAt = startedAt,
                        speedBps = (before + done) * 1000.0 / elapsed,
                        activeFiles = listOf(ActiveFile(name, done, total)),
                        recentFailures = failures.takeLast(5),
                    )
                )
            }
            if (result.ok) {
                succeeded++
                bytesDone = before + (result.info?.size ?: 0L)
                file.delete()
            } else {
                failed++
                failures.add(VaultFailure(file.name, result.error ?: "unknown"))
                log?.invoke("failed to restore ${file.name}: ${result.error}")
            }
        }

        if (!cancelled && failed == 0) {
            folder.removeVaultFiles()
            log?.invoke("vault removed; folder is plain again")
        }
        listener?.onProgress(
            VaultProgress(
                phase = VaultPhase.DONE, fileCount = sealed.size,
                bytesDone = bytesDone, bytesTotal = totalBytes,
                succeeded = succeeded, failed = failed, startedAt = startedAt,
            )
        )
        return VaultSummary(succeeded, failed, 0, bytesDone, failures, cancelled)
    }

    /**
     * Restores one file. Works at any depth, including from a hidden folder,
     * and leaves everything else locked — the point being that getting one file
     * out should never mean unlocking the lot.
     */
    fun restore(
        folder: VaultFolder,
        sealedFile: File,
        masterKey: ByteArray,
        destinationRoot: File = folder.root,
        progress: VaultContainer.Progress? = null,
    ): VaultOutcome = runCatching {
        val info = VaultContainer.readInfo(sealedFile, masterKey)
        val target = uniqueTarget(File(destinationRoot, info.relativePath))
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")

        VaultContainer.decrypt(sealedFile, temp, masterKey, progress)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        // The dates the whole design exists to preserve.
        if (info.modified > 0) target.setLastModified(info.modified)
        VaultOutcome(ok = true, info = info)
    }.getOrElse { VaultOutcome(ok = false, error = it.message ?: it::class.java.simpleName) }

    /** Contents of the vault, for browsing without decrypting anything. */
    fun listContents(folder: VaultFolder, masterKey: ByteArray): List<Pair<File, VaultFileInfo>> =
        folder.sealedFiles().mapNotNull { file ->
            runCatching { file to VaultContainer.readInfo(file, masterKey) }.getOrNull()
        }

    /** Never silently replaces something already sitting at the target path. */
    private fun uniqueTarget(desired: File): File {
        if (!desired.exists()) return desired
        val base = desired.name.substringBeforeLast('.', desired.name)
        val ext = desired.name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = File(
                desired.parentFile,
                if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext",
            )
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** Folders left empty once their contents were encrypted. */
    private fun removeEmptyFolders(folder: VaultFolder) {
        val dirs = ArrayList<File>()
        val queue = ArrayDeque<File>()
        queue.add(folder.root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            dir.listFiles()?.forEach { if (it.isDirectory) { dirs.add(it); queue.add(it) } }
        }
        // Deepest first, so emptying a child can leave its parent removable.
        dirs.sortByDescending { it.path.count { c -> c == File.separatorChar } }
        dirs.forEach { dir ->
            if (dir.parentFile == folder.root && dir.name.length == 2) return@forEach
            if (dir.list()?.isEmpty() == true) dir.delete()
        }
    }

    /**
     * Runs [work] over [items], one at a time when [workers] is 1 so the
     * single-threaded path stays exactly as it was and as it was tested.
     */
    private fun <T> runPool(items: List<T>, workers: Int, work: (T) -> Unit) {
        val lanes = workers.coerceIn(1, 8)
        if (lanes == 1) {
            items.forEach(work)
            return
        }
        val pool = Executors.newFixedThreadPool(lanes)
        try {
            items.map { item -> pool.submit { work(item) } }.forEach { future ->
                runCatching { future.get() }
            }
        } finally {
            pool.shutdown()
            runCatching { pool.awaitTermination(1, TimeUnit.MINUTES) }
        }
    }

    /** Stable, non-identifying reference for logs. */
    fun shortId(path: String): String =
        Integer.toHexString(path.hashCode()).padStart(8, '0')
}
