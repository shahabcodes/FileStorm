package com.shahabcodes.filestorm.transfer

import com.shahabcodes.filestorm.data.FsEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class TransferOp { COPY, MOVE }

enum class ItemStatus { PENDING, IN_PROGRESS, DONE, FAILED, SKIPPED }

data class TransferItem(
    val sourcePath: String,
    val name: String,
    val isDirectory: Boolean,
    val totalBytes: Long,
    val status: ItemStatus = ItemStatus.PENDING,
    val bytesDone: Long = 0L,
    val error: String? = null,
)

enum class JobState { IDLE, PREPARING, RUNNING, DONE, CANCELLED }

data class TransferJob(
    val op: TransferOp = TransferOp.COPY,
    val destination: String = "",
    val items: List<TransferItem> = emptyList(),
    val state: JobState = JobState.IDLE,
    val totalBytes: Long = 0L,
    val bytesDone: Long = 0L,
    val speedBps: Double = 0.0,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val currentIndex: Int = -1,
) {
    val progress: Float get() = if (totalBytes == 0L) 0f else (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f)
    val etaSeconds: Long
        get() {
            if (speedBps <= 1.0) return -1
            return ((totalBytes - bytesDone) / speedBps).toLong()
        }
    val doneCount: Int get() = items.count { it.status == ItemStatus.DONE }
    val failedCount: Int get() = items.count { it.status == ItemStatus.FAILED }
    val isActive: Boolean get() = state == JobState.PREPARING || state == JobState.RUNNING
}

/**
 * Singleton transfer engine. The foreground service keeps the process alive and
 * mirrors this state into a notification; the UI observes [state] directly.
 */
object TransferManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    private val _state = MutableStateFlow(TransferJob())
    val state: StateFlow<TransferJob> = _state

    @Volatile
    private var cancelled = false

    fun start(entries: List<FsEntry>, destination: String, op: TransferOp) {
        if (_state.value.isActive) return
        cancelled = false
        val items = entries.map {
            TransferItem(
                sourcePath = it.path,
                name = it.name,
                isDirectory = it.isDirectory,
                totalBytes = if (it.isDirectory) -1L else it.size,
            )
        }
        _state.value = TransferJob(
            op = op,
            destination = destination,
            items = items,
            state = JobState.PREPARING,
            startedAt = System.currentTimeMillis(),
        )
        runningJob = scope.launch { run() }
    }

    fun cancel() {
        cancelled = true
    }

    fun clearFinished() {
        if (!_state.value.isActive) _state.value = TransferJob()
    }

    private suspend fun run() {
        // Pass 1: measure directories so totals/ETA are accurate.
        var items = _state.value.items.map { item ->
            if (item.isDirectory) item.copy(totalBytes = directorySize(File(item.sourcePath))) else item
        }
        val grandTotal = items.sumOf { it.totalBytes.coerceAtLeast(0) }
        _state.value = _state.value.copy(items = items, totalBytes = grandTotal, state = JobState.RUNNING)

        val destDir = File(_state.value.destination)
        var bytesSoFar = 0L

        // Speed: exponential moving average sampled on every buffer write.
        var emaBps = 0.0
        var lastSampleTime = System.currentTimeMillis()
        var lastSampleBytes = 0L

        fun onBytes(globalDone: Long) {
            val now = System.currentTimeMillis()
            val dt = now - lastSampleTime
            if (dt >= 400) {
                val instBps = (globalDone - lastSampleBytes) * 1000.0 / dt
                emaBps = if (emaBps == 0.0) instBps else 0.75 * emaBps + 0.25 * instBps
                lastSampleTime = now
                lastSampleBytes = globalDone
                _state.value = _state.value.copy(bytesDone = globalDone, speedBps = emaBps)
            }
        }

        items.forEachIndexed { index, item ->
            if (cancelled) return@forEachIndexed
            items = updateItem(items, index) { it.copy(status = ItemStatus.IN_PROGRESS) }
            _state.value = _state.value.copy(items = items, currentIndex = index)

            val src = File(item.sourcePath)
            val result = runCatching {
                if (!src.exists()) error("Source no longer exists")
                if (src.absolutePath == destDir.absolutePath ||
                    destDir.absolutePath.startsWith(src.absolutePath + File.separator)
                ) error("Cannot transfer a folder into itself")

                val target = uniqueTarget(destDir, src.name)
                var moved = false
                if (_state.value.op == TransferOp.MOVE) {
                    // Same-volume fast path: instant rename, no byte copy.
                    moved = src.renameTo(target)
                }
                if (!moved) {
                    copyRecursive(src, target) { delta ->
                        bytesSoFar += delta
                        val cur = _state.value
                        val updated = updateItem(cur.items, index) {
                            it.copy(bytesDone = it.bytesDone + delta)
                        }
                        _state.value = cur.copy(items = updated)
                        onBytes(bytesSoFar)
                    }
                    if (_state.value.op == TransferOp.MOVE && !cancelled) {
                        if (!src.deleteRecursively()) error("Copied, but could not remove source")
                    }
                } else {
                    bytesSoFar += item.totalBytes.coerceAtLeast(0)
                    onBytes(bytesSoFar)
                }
            }

            items = _state.value.items
            items = if (cancelled && result.isFailure) {
                updateItem(items, index) { it.copy(status = ItemStatus.SKIPPED, error = "Cancelled") }
            } else if (result.isSuccess) {
                updateItem(items, index) { it.copy(status = ItemStatus.DONE, bytesDone = it.totalBytes.coerceAtLeast(0)) }
            } else {
                updateItem(items, index) {
                    it.copy(status = ItemStatus.FAILED, error = result.exceptionOrNull()?.message ?: "Unknown error")
                }
            }
            bytesSoFar = items.sumOf { i -> if (i.status == ItemStatus.DONE) i.totalBytes.coerceAtLeast(0) else i.bytesDone }
            _state.value = _state.value.copy(items = items, bytesDone = bytesSoFar)
        }

        val remaining = _state.value.items.map {
            if (it.status == ItemStatus.PENDING || it.status == ItemStatus.IN_PROGRESS)
                it.copy(status = ItemStatus.SKIPPED, error = "Cancelled") else it
        }
        _state.value = _state.value.copy(
            items = remaining,
            state = if (cancelled) JobState.CANCELLED else JobState.DONE,
            finishedAt = System.currentTimeMillis(),
            speedBps = 0.0,
        )
    }

    private inline fun updateItem(
        items: List<TransferItem>,
        index: Int,
        transform: (TransferItem) -> TransferItem,
    ): List<TransferItem> = items.toMutableList().also { it[index] = transform(it[index]) }

    private fun directorySize(dir: File): Long {
        var total = 0L
        val queue = ArrayDeque<File>()
        queue.add(dir)
        while (queue.isNotEmpty()) {
            val f = queue.removeFirst()
            val children = f.listFiles() ?: continue
            for (c in children) {
                if (c.isDirectory) queue.add(c) else total += c.length()
            }
        }
        return total
    }

    /** "photo.jpg" -> "photo (1).jpg" when the name is taken. */
    private fun uniqueTarget(destDir: File, name: String): File {
        var target = File(destDir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.') && base != name) "." + name.substringAfterLast('.') else ""
        var n = 1
        while (target.exists()) {
            target = File(destDir, "$base ($n)$ext")
            n++
        }
        return target
    }

    private fun copyRecursive(src: File, dst: File, onDelta: (Long) -> Unit) {
        if (cancelled) error("Cancelled")
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) error("Could not create folder ${dst.name}")
            src.listFiles()?.forEach { child ->
                copyRecursive(child, File(dst, child.name), onDelta)
            }
        } else {
            dst.parentFile?.let { if (!it.exists()) it.mkdirs() }
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
}
