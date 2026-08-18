package com.shahaabapps.filestorm.data.jobs

import com.shahaabapps.filestorm.data.FsEntry
import com.shahaabapps.filestorm.data.TrashManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VerifyPhase { IDLE, SCANNING, VERIFYING, DONE, CANCELLED, FAILED }

data class VerifyIssue(val path: String, val name: String, val reason: String)

data class VerifyState(
    val jobId: String = "",
    val jobName: String = "",
    val move: Boolean = false,
    val destination: String = "",
    val phase: VerifyPhase = VerifyPhase.IDLE,
    val error: String? = null,
    val totalFiles: Int = 0,
    val checkedFiles: Int = 0,
    val verifiedFiles: Int = 0,
    val issues: List<VerifyIssue> = emptyList(),
    val verifiedPaths: List<String> = emptyList(),
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val speedBps: Double = 0.0,
    val currentFileName: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val cleaning: Boolean = false,
    val cleanedCount: Int = -1,
) {
    val isActive: Boolean get() = phase == VerifyPhase.SCANNING || phase == VerifyPhase.VERIFYING
    val filesLeft: Int get() = (totalFiles - checkedFiles).coerceAtLeast(0)
    val bytesLeft: Long get() = (totalBytes - doneBytes).coerceAtLeast(0)
    val progress: Float
        get() = when {
            totalBytes > 0L -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalFiles > 0 -> (checkedFiles.toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }
    val etaSeconds: Long get() = if (speedBps > 1.0) (bytesLeft / speedBps).toLong() else -1
    val allVerified: Boolean get() = phase == VerifyPhase.DONE && issues.isEmpty() && totalFiles > 0
}

/**
 * Proves a job's transfers are complete: every remaining source file must have a
 * byte-for-byte identical copy in its destination month folder before the app
 * declares it safe to delete. Cleanup moves verified files to the recoverable Trash.
 */
object VerifyRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(VerifyState())
    val state: StateFlow<VerifyState> = _state

    @Volatile
    private var cancelled = false

    private val monthFormat = SimpleDateFormat("MMMMyyyy", Locale.ENGLISH)
    private const val UNKNOWN_LABEL = "UnknownDate"

    fun start(job: OrganizeJob): Boolean {
        if (_state.value.isActive || _state.value.cleaning) return false
        // Never verify while a job is still writing files.
        if (JobRunner.state.value.isActive) return false
        cancelled = false
        _state.value = VerifyState(
            jobId = job.id,
            jobName = job.name,
            move = job.move,
            destination = job.destination,
            phase = VerifyPhase.SCANNING,
            startedAt = System.currentTimeMillis(),
        )
        scope.launch { run(job) }
        return true
    }

    fun cancel() {
        cancelled = true
    }

    fun clearFinished() {
        if (!_state.value.isActive && !_state.value.cleaning) _state.value = VerifyState()
    }

    /** Moves the verified source files to Trash. Only callable after a completed pass. */
    fun cleanUp() {
        val s = _state.value
        if (s.phase != VerifyPhase.DONE || s.cleaning || s.verifiedPaths.isEmpty()) return
        _state.value = s.copy(cleaning = true)
        scope.launch {
            val entries = s.verifiedPaths.mapNotNull { path ->
                val f = File(path)
                if (f.exists()) FsEntry.from(f) else null
            }
            val failed = TrashManager.moveToTrash(entries)
            _state.value = _state.value.copy(
                cleaning = false,
                cleanedCount = entries.size - failed,
                verifiedPaths = emptyList(),
            )
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(
            phase = VerifyPhase.FAILED,
            error = message,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun run(job: OrganizeJob) {
        val destDir = File(job.destination)
        if (!destDir.isDirectory) return fail("Destination folder not found")
        val sources = job.sources.map { File(it) }.distinctBy { it.absolutePath }
        if (sources.none { it.isDirectory }) return fail("No source folder exists any more")

        // ── Scan sources with the same rules the job uses ───────────────
        val files = mutableListOf<File>()
        for (src in sources) {
            if (!src.isDirectory) continue
            if (job.includeSubfolders) {
                val queue = ArrayDeque<File>()
                queue.add(src)
                while (queue.isNotEmpty()) {
                    if (cancelled) break
                    val dir = queue.removeFirst()
                    val children = dir.listFiles() ?: continue
                    for (child in children) {
                        if (child.name == ".FileStorm") continue
                        if (child.name.startsWith(".")) continue
                        if (child.isDirectory) queue.add(child) else files.add(child)
                    }
                }
            } else {
                src.listFiles()?.forEach { child ->
                    if (child.isFile && !child.name.startsWith(".")) files.add(child)
                }
            }
        }
        if (cancelled) {
            _state.value = _state.value.copy(phase = VerifyPhase.CANCELLED, finishedAt = System.currentTimeMillis())
            return
        }
        if (files.isEmpty()) {
            // Nothing left in the sources — for a move job that IS the success state.
            _state.value = _state.value.copy(
                phase = VerifyPhase.DONE,
                finishedAt = System.currentTimeMillis(),
            )
            return
        }

        _state.value = _state.value.copy(
            phase = VerifyPhase.VERIFYING,
            totalFiles = files.size,
            totalBytes = files.sumOf { it.length() },
        )

        var emaBps = 0.0
        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = 0L
        fun sampleSpeed() {
            val now = System.currentTimeMillis()
            val dt = now - lastSampleTime
            if (dt >= 400) {
                val inst = (_state.value.doneBytes - lastSampleBytes) * 1000.0 / dt
                emaBps = if (emaBps == 0.0) inst else 0.75 * emaBps + 0.25 * inst
                lastSampleTime = now
                lastSampleBytes = _state.value.doneBytes
                _state.value = _state.value.copy(speedBps = emaBps)
            }
        }

        // Cache month-folder listings so renamed copies "(1)" are found cheaply.
        val monthListings = mutableMapOf<String, List<File>>()

        for (file in files) {
            if (cancelled) break
            _state.value = _state.value.copy(currentFileName = file.name)

            val size = file.length()
            val modified = file.lastModified()
            val label = if (modified <= 0) UNKNOWN_LABEL else monthFormat.format(Date(modified))
            val monthDir = File(destDir, label)
            // Baseline so streamed comparison deltas never double-count with the
            // fixed per-file total applied after the verdict.
            val bytesBaseline = _state.value.doneBytes
            var firstComparison = true
            val onDelta: (Long) -> Unit = { delta ->
                if (firstComparison) {
                    _state.value = _state.value.copy(doneBytes = _state.value.doneBytes + delta)
                    sampleSpeed()
                }
            }

            val verdict: String? = if (!monthDir.isDirectory) {
                "No $label folder in destination"
            } else {
                val exact = File(monthDir, file.name)
                val base = file.name.substringBeforeLast('.', file.name)
                val ext = if (file.name.contains('.') && base != file.name)
                    "." + file.name.substringAfterLast('.') else ""
                val listing = monthListings.getOrPut(label) { monthDir.listFiles()?.toList() ?: emptyList() }
                val candidates = buildList {
                    if (exact.isFile) add(exact)
                    listing.forEach { c ->
                        if (c.isFile && c.name != file.name &&
                            c.name.startsWith("$base (") && c.name.endsWith(ext) && c.length() == size
                        ) add(c)
                    }
                }
                when {
                    candidates.isEmpty() -> "Not found in $label"
                    candidates.none { it.length() == size } -> "Copy in $label has a different size"
                    else -> {
                        val match = candidates.firstOrNull { c ->
                            val equal = c.length() == size && contentsEqual(file, c, onDelta)
                            firstComparison = false
                            equal
                        }
                        if (match != null) null else "Copy in $label differs from source"
                    }
                }
            }

            val s = _state.value
            _state.value = if (verdict == null) {
                s.copy(
                    checkedFiles = s.checkedFiles + 1,
                    verifiedFiles = s.verifiedFiles + 1,
                    verifiedPaths = s.verifiedPaths + file.absolutePath,
                    doneBytes = bytesBaseline + size,
                )
            } else {
                s.copy(
                    checkedFiles = s.checkedFiles + 1,
                    issues = s.issues + VerifyIssue(file.absolutePath, file.name, verdict),
                    doneBytes = bytesBaseline + size,
                )
            }
            sampleSpeed()
        }

        _state.value = _state.value.copy(
            phase = if (cancelled) VerifyPhase.CANCELLED else VerifyPhase.DONE,
            finishedAt = System.currentTimeMillis(),
            speedBps = 0.0,
            currentFileName = "",
        )
    }

    /** Streaming byte-for-byte comparison; reports read bytes via onDelta. */
    private fun contentsEqual(a: File, b: File, onDelta: (Long) -> Unit): Boolean {
        if (a.length() != b.length()) return false
        FileInputStream(a).use { ia ->
            FileInputStream(b).use { ib ->
                val bufA = ByteArray(512 * 1024)
                val bufB = ByteArray(512 * 1024)
                while (true) {
                    if (cancelled) return false
                    val readA = ia.readNBytesCompat(bufA)
                    val readB = ib.readNBytesCompat(bufB)
                    if (readA != readB) return false
                    if (readA <= 0) return true
                    for (i in 0 until readA) {
                        if (bufA[i] != bufB[i]) return false
                    }
                    onDelta(readA.toLong())
                }
            }
        }
    }

    /** Fills the buffer as fully as possible; returns bytes read or -1 at EOF. */
    private fun FileInputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return if (total == 0) -1 else total
            total += read
        }
        return total
    }
}
