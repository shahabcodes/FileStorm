package com.shahaabapps.filestorm.data.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class JobPhase { IDLE, SCANNING, RUNNING, DONE, CANCELLED, FAILED }

enum class MonthState { PENDING, RUNNING, DONE }

data class MonthProgress(
    val label: String,
    val fileCount: Int,
    val totalBytes: Long,
    val doneFiles: Int = 0,
    val failedFiles: Int = 0,
    val skippedFiles: Int = 0,
    val state: MonthState = MonthState.PENDING,
)

data class JobFailure(
    val path: String,
    val name: String,
    val month: String,
    val reason: String,
)

data class JobRunState(
    val jobId: String = "",
    val jobName: String = "",
    val move: Boolean = false,
    val destination: String = "",
    val phase: JobPhase = JobPhase.IDLE,
    val error: String? = null,
    val failures: List<JobFailure> = emptyList(),
    val months: List<MonthProgress> = emptyList(),
    val currentMonthIndex: Int = -1,
    val currentFileName: String = "",
    val totalFiles: Int = 0,
    val doneFiles: Int = 0,
    val failedFiles: Int = 0,
    val skippedFiles: Int = 0,
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val speedBps: Double = 0.0,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
) {
    val isActive: Boolean get() = phase == JobPhase.SCANNING || phase == JobPhase.RUNNING
    val monthsDone: Int get() = months.count { it.state == MonthState.DONE }
    val monthsLeft: Int get() = months.size - monthsDone
    val filesLeft: Int get() = (totalFiles - doneFiles - failedFiles - skippedFiles).coerceAtLeast(0)
    val bytesLeft: Long get() = (totalBytes - doneBytes).coerceAtLeast(0)
    val progress: Float
        get() = when {
            totalBytes > 0L -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalFiles > 0 -> ((doneFiles + failedFiles + skippedFiles).toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }
    val etaSeconds: Long get() = if (speedBps > 1.0) (bytesLeft / speedBps).toLong() else -1
}

/**
 * Runs a monthly organize job: groups source files by modified month and
 * copies/moves each group into destination/MonthYear (e.g. April2026).
 */
object JobRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(JobRunState())
    val state: StateFlow<JobRunState> = _state

    @Volatile
    private var cancelled = false

    private val monthFormat = SimpleDateFormat("MMMMyyyy", Locale.ENGLISH)
    private const val UNKNOWN_LABEL = "UnknownDate"

    fun start(job: OrganizeJob): Boolean {
        if (_state.value.isActive) return false
        // Never organize while a verification pass is reading the same trees.
        if (VerifyRunner.state.value.isActive || VerifyRunner.state.value.cleaning) return false
        cancelled = false
        _state.value = JobRunState(
            jobId = job.id,
            jobName = job.name,
            move = job.move,
            destination = job.destination,
            phase = JobPhase.SCANNING,
            startedAt = System.currentTimeMillis(),
        )
        scope.launch { run(job) }
        return true
    }

    fun cancel() {
        cancelled = true
    }

    fun clearFinished() {
        if (!_state.value.isActive) _state.value = JobRunState()
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(
            phase = JobPhase.FAILED,
            error = message,
            finishedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun run(job: OrganizeJob) {
        // ── Validate ────────────────────────────────────────────────────
        val destDir = File(job.destination)
        val sources = job.sources.map { File(it) }.distinctBy { it.absolutePath }
        if (sources.isEmpty()) return fail("No source folder selected")
        for (src in sources) {
            if (!src.isDirectory) return fail("Source no longer exists: ${src.name}")
            val destPath = destDir.absolutePath
            val srcPath = src.absolutePath
            if (destPath == srcPath) return fail("Destination cannot be the same as a source folder")
            if (destPath.startsWith("$srcPath${File.separator}")) {
                return fail("Destination cannot be inside a source folder")
            }
        }
        if (!destDir.isDirectory && !destDir.mkdirs()) return fail("Could not access destination folder")

        // ── Scan ────────────────────────────────────────────────────────
        val files = mutableListOf<File>()
        for (src in sources) {
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
            _state.value = _state.value.copy(phase = JobPhase.CANCELLED, finishedAt = System.currentTimeMillis())
            return
        }
        if (files.isEmpty()) {
            _state.value = _state.value.copy(
                phase = JobPhase.DONE,
                finishedAt = System.currentTimeMillis(),
            )
            JobStore.markRun(job.id, System.currentTimeMillis())
            return
        }

        // ── Group by month, oldest first ────────────────────────────────
        data class Snapshot(val file: File, val size: Long, val modified: Long)

        val snapshots = files.map { Snapshot(it, it.length(), it.lastModified()) }
        val groups = snapshots
            .groupBy { snap -> if (snap.modified <= 0) UNKNOWN_LABEL else monthFormat.format(Date(snap.modified)) }
            .entries
            .sortedBy { (_, snaps) -> snaps.minOf { if (it.modified <= 0) Long.MAX_VALUE else it.modified } }

        val monthProgress = groups.map { (label, snaps) ->
            MonthProgress(label = label, fileCount = snaps.size, totalBytes = snaps.sumOf { it.size })
        }
        _state.value = _state.value.copy(
            phase = JobPhase.RUNNING,
            months = monthProgress,
            totalFiles = snapshots.size,
            totalBytes = snapshots.sumOf { it.size },
        )

        // ── Transfer ────────────────────────────────────────────────────
        var emaBps = 0.0
        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = 0L

        fun sampleSpeed(bytesDoneNow: Long) {
            val now = System.currentTimeMillis()
            val dt = now - lastSampleTime
            if (dt >= 400) {
                val inst = (bytesDoneNow - lastSampleBytes) * 1000.0 / dt
                emaBps = if (emaBps == 0.0) inst else 0.75 * emaBps + 0.25 * inst
                lastSampleTime = now
                lastSampleBytes = bytesDoneNow
                _state.value = _state.value.copy(speedBps = emaBps)
            }
        }

        fun updateMonth(index: Int, transform: (MonthProgress) -> MonthProgress) {
            val months = _state.value.months.toMutableList()
            months[index] = transform(months[index])
            _state.value = _state.value.copy(months = months)
        }

        groups.forEachIndexed { monthIndex, (label, snaps) ->
            if (cancelled) return@forEachIndexed
            updateMonth(monthIndex) { it.copy(state = MonthState.RUNNING) }
            _state.value = _state.value.copy(currentMonthIndex = monthIndex)

            // Reuse an existing MonthYear folder; only create it when absent.
            val monthDir = File(destDir, label)
            val monthDirOk = monthDir.isDirectory || monthDir.mkdirs()

            fun recordFailure(snap: Snapshot, reason: String) {
                updateMonth(monthIndex) { it.copy(failedFiles = it.failedFiles + 1) }
                _state.value = _state.value.copy(
                    failedFiles = _state.value.failedFiles + 1,
                    failures = _state.value.failures +
                        JobFailure(snap.file.absolutePath, snap.file.name, label, reason),
                )
            }

            for (snap in snaps) {
                if (cancelled) break
                _state.value = _state.value.copy(currentFileName = snap.file.name)

                if (!monthDirOk) {
                    recordFailure(snap, "Could not create folder $label in destination")
                    continue
                }
                if (!snap.file.exists()) {
                    recordFailure(snap, "Source file no longer exists")
                    continue
                }

                // Incremental re-runs: identical file (name + size + mtime) already there → skip.
                // Copies preserve mtime, so a genuine duplicate always matches all three;
                // a mere size coincidence falls through to a safe renamed copy instead.
                val existing = File(monthDir, snap.file.name)
                if (existing.exists() && existing.length() == snap.size &&
                    existing.lastModified() == snap.modified
                ) {
                    if (job.move) snap.file.delete()
                    updateMonth(monthIndex) { it.copy(skippedFiles = it.skippedFiles + 1) }
                    _state.value = _state.value.copy(
                        skippedFiles = _state.value.skippedFiles + 1,
                        doneBytes = _state.value.doneBytes + snap.size,
                    )
                    continue
                }
                val target = uniqueTarget(monthDir, snap.file.name)

                val result = runCatching {
                    var moved = false
                    if (job.move) moved = snap.file.renameTo(target)
                    if (!moved) {
                        copyFile(snap.file, target) { delta ->
                            _state.value = _state.value.copy(doneBytes = _state.value.doneBytes + delta)
                            sampleSpeed(_state.value.doneBytes)
                        }
                        if (job.move && !cancelled) snap.file.delete()
                    } else {
                        _state.value = _state.value.copy(doneBytes = _state.value.doneBytes + snap.size)
                        sampleSpeed(_state.value.doneBytes)
                    }
                }

                if (result.isSuccess) {
                    updateMonth(monthIndex) { it.copy(doneFiles = it.doneFiles + 1) }
                    _state.value = _state.value.copy(doneFiles = _state.value.doneFiles + 1)
                } else if (!cancelled) {
                    recordFailure(
                        snap,
                        result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                            ?: "Could not write file (storage full or permission denied)",
                    )
                }
            }
            updateMonth(monthIndex) { it.copy(state = MonthState.DONE) }
        }

        _state.value = _state.value.copy(
            phase = if (cancelled) JobPhase.CANCELLED else JobPhase.DONE,
            finishedAt = System.currentTimeMillis(),
            speedBps = 0.0,
            currentFileName = "",
        )
        JobStore.markRun(job.id, System.currentTimeMillis())
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.') && base != name) "." + name.substringAfterLast('.') else ""
        var n = 1
        while (target.exists()) {
            target = File(dir, "$base ($n)$ext")
            n++
        }
        return target
    }

    private fun copyFile(src: File, dst: File, onDelta: (Long) -> Unit) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                val buffer = ByteArray(512 * 1024)
                while (true) {
                    if (cancelled) {
                        dst.delete()
                        error("Cancelled")
                    }
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    onDelta(read.toLong())
                }
                output.fd.sync()
            }
        }
        dst.setLastModified(src.lastModified())
    }
}
