package com.shahabcodes.filestorm.data.dup

import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import com.shahabcodes.filestorm.data.TrashManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

enum class DupPhase { IDLE, SCANNING, COMPARING, DONE, CANCELLED, FAILED }

/** Two hand-picked folders, or a sweep of everything on internal storage. */
enum class DupScope { TWO_FOLDERS, WHOLE_STORAGE }

data class DupPair(
    val id: Int,
    val pathA: String,
    val pathB: String,
    val name: String,
    val size: Long,
)

data class DupState(
    val searchScope: DupScope = DupScope.TWO_FOLDERS,
    val folder1: String = "",
    val folder2: String = "",
    val deepCompare: Boolean = false,
    val includeHidden: Boolean = true,
    val phase: DupPhase = DupPhase.IDLE,
    val error: String? = null,
    val scannedFiles: Int = 0,
    val pairs: List<DupPair> = emptyList(),
    val candidateCount: Int = 0,
    val comparedCount: Int = 0,
    val totalBytes: Long = 0L,
    val doneBytes: Long = 0L,
    val speedBps: Double = 0.0,
    val currentFileName: String = "",
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val cleaning: Boolean = false,
    val cleanedCount: Int = -1,
    val cleanedBytes: Long = 0L,
) {
    val isActive: Boolean get() = phase == DupPhase.SCANNING || phase == DupPhase.COMPARING
    val wholeStorage: Boolean get() = searchScope == DupScope.WHOLE_STORAGE
    /** Distinct files that would be freed, since one group can span many pairs. */
    val extraCopies: Int get() = pairs.map { it.pathB }.distinct().size
    val wastedBytes: Long get() = pairs.sumOf { it.size }
    val progress: Float
        get() = when {
            phase == DupPhase.COMPARING && totalBytes > 0 ->
                (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            phase == DupPhase.COMPARING && candidateCount > 0 ->
                (comparedCount.toFloat() / candidateCount).coerceIn(0f, 1f)
            else -> 0f
        }
    val etaSeconds: Long
        get() = if (phase == DupPhase.COMPARING && speedBps > 1.0)
            ((totalBytes - doneBytes).coerceAtLeast(0) / speedBps).toLong() else -1
}

/**
 * Finds files that exist in BOTH folders (recursively) with the same name, size
 * and type. Optional deep compare proves the contents are identical before a
 * pair is reported. Cleanup moves one chosen side to the recoverable Trash.
 */
object DuplicateFinder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(DupState())
    val state: StateFlow<DupState> = _state

    @Volatile
    private var cancelled = false

    /**
     * Sweeps all of internal storage and groups identical files wherever they
     * live. Each group keeps its first copy and reports the rest as extras.
     */
    fun startWholeStorage(deepCompare: Boolean, includeHidden: Boolean = true): Boolean {
        if (_state.value.isActive || _state.value.cleaning) return false
        cancelled = false
        _state.value = DupState(
            searchScope = DupScope.WHOLE_STORAGE,
            folder1 = FileRepository.rootPath,
            folder2 = "",
            deepCompare = deepCompare,
            includeHidden = includeHidden,
            phase = DupPhase.SCANNING,
            startedAt = System.currentTimeMillis(),
        )
        scope.launch { runWholeStorage(deepCompare, includeHidden) }
        return true
    }

    fun start(
        folder1: String,
        folder2: String,
        deepCompare: Boolean,
        includeHidden: Boolean = true,
    ): Boolean {
        if (_state.value.isActive || _state.value.cleaning) return false
        val f1 = File(folder1)
        val f2 = File(folder2)
        cancelled = false
        _state.value = DupState(
            searchScope = DupScope.TWO_FOLDERS,
            folder1 = folder1,
            folder2 = folder2,
            deepCompare = deepCompare,
            includeHidden = includeHidden,
            phase = DupPhase.SCANNING,
            startedAt = System.currentTimeMillis(),
        )
        if (!f1.isDirectory || !f2.isDirectory) {
            _state.value = _state.value.copy(phase = DupPhase.FAILED, error = "Pick two existing folders")
            return true
        }
        if (f1.absolutePath == f2.absolutePath) {
            _state.value = _state.value.copy(phase = DupPhase.FAILED, error = "The two folders must be different")
            return true
        }
        val p1 = f1.absolutePath + File.separator
        val p2 = f2.absolutePath + File.separator
        if (p1.startsWith(p2) || p2.startsWith(p1)) {
            _state.value = _state.value.copy(
                phase = DupPhase.FAILED,
                error = "One folder is inside the other — pick two separate folders",
            )
            return true
        }
        scope.launch { run(f1, f2, deepCompare, includeHidden) }
        return true
    }

    fun cancel() {
        cancelled = true
    }

    fun clearFinished() {
        if (!_state.value.isActive && !_state.value.cleaning) _state.value = DupState()
    }

    /** Moves one side of the selected pairs to Trash. side 1 = folder1's copies. */
    fun deleteSide(side: Int, selectedIds: Set<Int>) {
        val s = _state.value
        if (s.phase != DupPhase.DONE || s.cleaning) return
        val victims = s.pairs
            .filter { it.id in selectedIds }
            .map { if (side == 1) it.pathA else it.pathB }
            .distinct()
        if (victims.isEmpty()) return
        _state.value = s.copy(cleaning = true)
        scope.launch {
            val entries = victims.mapNotNull { path ->
                val f = File(path)
                if (f.exists()) FsEntry.from(f) else null
            }
            val bytes = entries.sumOf { it.size }
            val failed = TrashManager.moveToTrash(entries)
            val remaining = _state.value.pairs.filterNot { it.id in selectedIds }
            _state.value = _state.value.copy(
                cleaning = false,
                cleanedCount = entries.size - failed,
                cleanedBytes = bytes,
                pairs = remaining,
            )
        }
    }

    /**
     * Whole-storage sweep. Android/data and Android/obb are app-private sandboxes
     * that are unreadable on modern Android, but Android/media is not — that is
     * where WhatsApp and friends keep real user files, so it stays in scope.
     */
    private suspend fun runWholeStorage(deepCompare: Boolean, includeHidden: Boolean) {
        val root = File(FileRepository.rootPath)
        val all = scanFiles(root, includeHidden, wholeStorage = true)
        if (cancelled || all == null) {
            _state.value = _state.value.copy(
                phase = DupPhase.CANCELLED,
                finishedAt = System.currentTimeMillis(),
            )
            return
        }
        _state.value = _state.value.copy(scannedFiles = all.size)

        val groups = HashMap<String, MutableList<File>>()
        for (f in all) {
            if (f.length() <= 0L) continue // empty files match everything; not useful
            val key = f.name.lowercase() + "|" + f.length()
            groups.getOrPut(key) { mutableListOf() }.add(f)
        }
        var id = 0
        val candidates = mutableListOf<DupPair>()
        for (group in groups.values) {
            if (cancelled) break
            if (group.size < 2) continue
            // Keep the oldest copy — it is the likeliest original — and report
            // every other copy as an extra that can be reclaimed.
            val sorted = group.sortedWith(compareBy({ it.lastModified() }, { it.absolutePath }))
            val keeper = sorted.first()
            for (extra in sorted.drop(1)) {
                candidates.add(
                    DupPair(id++, keeper.absolutePath, extra.absolutePath, extra.name, extra.length())
                )
            }
        }
        finish(candidates, deepCompare)
    }

    private fun scanFiles(
        root: File,
        includeHidden: Boolean,
        wholeStorage: Boolean = false,
    ): List<File>? {
        val out = mutableListOf<File>()
        val queue = ArrayDeque<File>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            if (cancelled) return null
            val dir = queue.removeFirst()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                // The app's own trash is never a duplicate candidate.
                if (child.name == ".FileStorm") continue
                if (!includeHidden && child.name.startsWith(".")) continue
                if (wholeStorage && dir.absolutePath == root.absolutePath.trimEnd(File.separatorChar) &&
                    child.name == "Android"
                ) {
                    // Descend into Android/media only; the rest is app-private.
                    val media = File(child, "media")
                    if (media.isDirectory) queue.add(media)
                    continue
                }
                if (child.isDirectory) queue.add(child)
                else {
                    out.add(child)
                    if (out.size % 250 == 0) {
                        _state.value = _state.value.copy(scannedFiles = _state.value.scannedFiles + 250)
                    }
                }
            }
        }
        return out
    }

    private suspend fun run(f1: File, f2: File, deepCompare: Boolean, includeHidden: Boolean) {
        val files1 = scanFiles(f1, includeHidden)
        val files2 = if (files1 != null) scanFiles(f2, includeHidden) else null
        if (cancelled || files1 == null || files2 == null) {
            _state.value = _state.value.copy(phase = DupPhase.CANCELLED, finishedAt = System.currentTimeMillis())
            return
        }
        _state.value = _state.value.copy(scannedFiles = files1.size + files2.size)

        // Match on name (case-insensitive, which also matches the type/extension) + exact size.
        val index1 = HashMap<String, MutableList<File>>()
        for (f in files1) {
            val key = f.name.lowercase() + "|" + f.length()
            index1.getOrPut(key) { mutableListOf() }.add(f)
        }
        var id = 0
        val candidates = mutableListOf<DupPair>()
        for (f in files2) {
            if (cancelled) break
            val key = f.name.lowercase() + "|" + f.length()
            val matches = index1[key] ?: continue
            if (matches.isEmpty()) continue
            // Pair with the first unclaimed copy on side 1 so one original
            // never "covers" for two different side-2 files.
            val partner = matches.removeAt(0)
            candidates.add(DupPair(id++, partner.absolutePath, f.absolutePath, f.name, f.length()))
        }
        if (cancelled) {
            _state.value = _state.value.copy(phase = DupPhase.CANCELLED, finishedAt = System.currentTimeMillis())
            return
        }

        finish(candidates, deepCompare)
    }

    /** Shared tail: optionally prove the contents match, then publish the result. */
    private suspend fun finish(candidates: List<DupPair>, deepCompare: Boolean) {
        if (cancelled) {
            _state.value = _state.value.copy(
                phase = DupPhase.CANCELLED,
                finishedAt = System.currentTimeMillis(),
            )
            return
        }
        if (!deepCompare) {
            _state.value = _state.value.copy(
                phase = DupPhase.DONE,
                pairs = candidates,
                candidateCount = candidates.size,
                finishedAt = System.currentTimeMillis(),
            )
            return
        }

        // Deep compare pass: only byte-identical pairs survive.
        _state.value = _state.value.copy(
            phase = DupPhase.COMPARING,
            candidateCount = candidates.size,
            totalBytes = candidates.sumOf { it.size },
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

        val confirmed = mutableListOf<DupPair>()
        for (pair in candidates) {
            if (cancelled) break
            _state.value = _state.value.copy(currentFileName = pair.name)
            val baseline = _state.value.doneBytes
            val equal = runCatching {
                contentsEqual(File(pair.pathA), File(pair.pathB)) { delta ->
                    _state.value = _state.value.copy(doneBytes = _state.value.doneBytes + delta)
                    sampleSpeed()
                }
            }.getOrDefault(false)
            if (equal) confirmed.add(pair)
            _state.value = _state.value.copy(
                comparedCount = _state.value.comparedCount + 1,
                doneBytes = baseline + pair.size,
                pairs = confirmed.toList(),
            )
            sampleSpeed()
        }

        _state.value = _state.value.copy(
            phase = if (cancelled) DupPhase.CANCELLED else DupPhase.DONE,
            pairs = confirmed,
            finishedAt = System.currentTimeMillis(),
            speedBps = 0.0,
            currentFileName = "",
        )
    }

    private fun contentsEqual(a: File, b: File, onDelta: (Long) -> Unit): Boolean {
        if (!a.isFile || !b.isFile || a.length() != b.length()) return false
        FileInputStream(a).use { ia ->
            FileInputStream(b).use { ib ->
                val bufA = ByteArray(512 * 1024)
                val bufB = ByteArray(512 * 1024)
                while (true) {
                    if (cancelled) return false
                    val readA = ia.readFully(bufA)
                    val readB = ib.readFully(bufB)
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

    private fun FileInputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return if (total == 0) -1 else total
            total += read
        }
        return total
    }
}
