package com.shahaabapps.filestorm.data.arrange

import com.shahaabapps.filestorm.data.FileKind
import com.shahaabapps.filestorm.data.FsEntry
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

enum class ArrangeMode {
    /** Group photos and videos into MonthYear folders. */
    MONTHLY,

    /** Pull every file out of subfolders into the chosen folder. */
    FLATTEN,
}

enum class ArrangePhase {
    IDLE,
    SCANNING,
    REVIEW_PLAN,
    MOVING,
    REVIEW_CLEANUP,
    CLEANING,
    DONE,
    CANCELLED,
    FAILED,
}

data class MonthPlan(
    val label: String,
    val files: Int,
    val bytes: Long,
    val folderExists: Boolean,
)

data class ArrangeState(
    val root: String = "",
    val mode: ArrangeMode = ArrangeMode.MONTHLY,
    val phase: ArrangePhase = ArrangePhase.IDLE,
    /** Plain-language description of exactly what is happening right now. */
    val message: String = "",
    val error: String? = null,
    val scannedFiles: Int = 0,
    val scannedFolders: Int = 0,
    val months: List<MonthPlan> = emptyList(),
    val totalFiles: Int = 0,
    val totalBytes: Long = 0L,
    val movedFiles: Int = 0,
    val skippedFiles: Int = 0,
    val failedFiles: Int = 0,
    val bytesDone: Long = 0L,
    val speedBps: Double = 0.0,
    val currentFile: String = "",
    val currentMonth: String = "",
    val emptyFolders: List<String> = emptyList(),
    val deletedFolders: Int = 0,
    val errors: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
) {
    val isBusy: Boolean
        get() = phase == ArrangePhase.SCANNING || phase == ArrangePhase.MOVING ||
            phase == ArrangePhase.CLEANING
    val needsInput: Boolean
        get() = phase == ArrangePhase.REVIEW_PLAN || phase == ArrangePhase.REVIEW_CLEANUP
    val filesLeft: Int get() = (totalFiles - movedFiles - skippedFiles - failedFiles).coerceAtLeast(0)
    val bytesLeft: Long get() = (totalBytes - bytesDone).coerceAtLeast(0)
    val progress: Float
        get() = if (totalBytes > 0) (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val etaSeconds: Long get() = if (speedBps > 1.0) (bytesLeft / speedBps).toLong() else -1
    val newFolderCount: Int get() = months.count { !it.folderExists }
}

/**
 * Collects every photo and video from a folder tree into MonthYear folders at
 * the top level, then offers to remove the folders left empty behind them.
 * Each phase pauses for the user where something irreversible is about to run.
 */
object ArrangeRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ArrangeState())
    val state: StateFlow<ArrangeState> = _state

    @Volatile
    private var cancelled = false

    private val monthFormat = SimpleDateFormat("MMMMyyyy", Locale.ENGLISH)
    private const val UNKNOWN = "UnknownDate"
    private const val FLATTEN_LABEL = "__flatten__"

    /** Files grouped by month label, filled during the scan. */
    private var planned: Map<String, List<File>> = emptyMap()

    fun reset() {
        if (!_state.value.isBusy) {
            planned = emptyMap()
            _state.value = ArrangeState()
        }
    }

    fun cancel() {
        cancelled = true
    }

    /** Phase 1: walk every subfolder and work out the month layout. */
    fun scan(root: String, mode: ArrangeMode = ArrangeMode.MONTHLY) {
        if (_state.value.isBusy) return
        cancelled = false
        _state.value = ArrangeState(
            root = root,
            mode = mode,
            phase = ArrangePhase.SCANNING,
            message = if (mode == ArrangeMode.FLATTEN)
                "Looking through every subfolder for files to bring together…"
            else "Looking through every subfolder for photos and videos…",
            startedAt = System.currentTimeMillis(),
        )
        scope.launch {
            val rootDir = File(root)
            if (!rootDir.isDirectory) {
                _state.value = _state.value.copy(
                    phase = ArrangePhase.FAILED,
                    error = "That folder no longer exists",
                )
                return@launch
            }

            val media = mutableListOf<File>()
            var folderCount = 0
            val queue = ArrayDeque<File>()
            queue.add(rootDir)
            while (queue.isNotEmpty()) {
                if (cancelled) {
                    _state.value = _state.value.copy(phase = ArrangePhase.CANCELLED, message = "Scan cancelled")
                    return@launch
                }
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (child.name == ".FileStorm" || child.name.startsWith(".")) continue
                    if (child.isDirectory) {
                        folderCount++
                        queue.add(child)
                    } else {
                        val kind = FsEntry.kindOf(child.name, false)
                        val wanted = mode == ArrangeMode.FLATTEN ||
                            kind == FileKind.IMAGE || kind == FileKind.VIDEO
                        if (wanted) {
                            media.add(child)
                            if (media.size % 100 == 0) {
                                _state.value = _state.value.copy(
                                    scannedFiles = media.size,
                                    scannedFolders = folderCount,
                                    message = "Found ${media.size} file(s) so far…",
                                )
                            }
                        }
                    }
                }
            }

            // Files already sitting in a correctly named month folder are left alone.
            val groups = if (mode == ArrangeMode.FLATTEN) {
                val loose = media.filter { it.parentFile?.absolutePath != rootDir.absolutePath }
                if (loose.isEmpty()) emptyMap() else mapOf(FLATTEN_LABEL to loose)
            } else media
                .groupBy { file ->
                    val modified = file.lastModified()
                    if (modified <= 0) UNKNOWN else monthFormat.format(Date(modified))
                }
                .filterKeys { it.isNotEmpty() }
                .mapValues { (label, files) ->
                    // Skip files that are already directly inside their month folder.
                    files.filter { it.parentFile?.name != label || it.parentFile?.parentFile?.absolutePath != rootDir.absolutePath }
                }
                .filterValues { it.isNotEmpty() }

            planned = groups
            val monthPlans = if (mode == ArrangeMode.FLATTEN) {
                groups.map { (_, files) ->
                    MonthPlan(
                        label = File(rootDir.absolutePath).name.ifEmpty { "This folder" },
                        files = files.size,
                        bytes = files.sumOf { it.length() },
                        folderExists = true,
                    )
                }
            } else groups.entries
                .sortedBy { (_, files) -> files.minOf { it.lastModified().takeIf { m -> m > 0 } ?: Long.MAX_VALUE } }
                .map { (label, files) ->
                    MonthPlan(
                        label = label,
                        files = files.size,
                        bytes = files.sumOf { it.length() },
                        folderExists = File(rootDir, label).isDirectory,
                    )
                }

            _state.value = _state.value.copy(
                phase = ArrangePhase.REVIEW_PLAN,
                scannedFiles = media.size,
                scannedFolders = folderCount,
                months = monthPlans,
                totalFiles = monthPlans.sumOf { it.files },
                totalBytes = monthPlans.sumOf { it.bytes },
                message = when {
                    monthPlans.isEmpty() && mode == ArrangeMode.FLATTEN ->
                        "Nothing to move — every file already sits directly in this folder."
                    monthPlans.isEmpty() ->
                        "Nothing to arrange — every photo and video is already in place."
                    mode == ArrangeMode.FLATTEN ->
                        "Scanned $folderCount folders. ${monthPlans.sumOf { it.files }} file(s) will be " +
                            "brought into this one folder."
                    else ->
                        "Scanned $folderCount folders. ${monthPlans.sumOf { it.files }} files will move into " +
                            "${monthPlans.size} month folders (${monthPlans.count { !it.folderExists }} to create)."
                },
            )
        }
    }

    /** Phase 2: perform the moves the user just approved. */
    fun startMoving() {
        val current = _state.value
        if (current.phase != ArrangePhase.REVIEW_PLAN) return
        cancelled = false
        _state.value = current.copy(
            phase = ArrangePhase.MOVING,
            message = "Moving files into month folders…",
        )
        scope.launch { move() }
    }

    private fun move() {
        val rootDir = File(_state.value.root)
        var emaBps = 0.0
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        fun sample() {
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            if (dt >= 400) {
                val inst = (_state.value.bytesDone - lastBytes) * 1000.0 / dt
                emaBps = if (emaBps == 0.0) inst else 0.75 * emaBps + 0.25 * inst
                lastTime = now
                lastBytes = _state.value.bytesDone
                _state.value = _state.value.copy(speedBps = emaBps)
            }
        }

        val errors = mutableListOf<String>()
        val flatten = _state.value.mode == ArrangeMode.FLATTEN
        for ((label, files) in planned) {
            if (cancelled) break
            val monthDir = if (flatten) rootDir else File(rootDir, label)
            val existed = monthDir.isDirectory
            val ready = existed || monthDir.mkdirs()
            _state.value = _state.value.copy(
                currentMonth = if (flatten) rootDir.name else label,
                message = when {
                    flatten -> "Bringing files into ${rootDir.name.ifEmpty { "this folder" }}…"
                    existed -> "Adding to existing folder $label…"
                    else -> "Created folder $label, moving files in…"
                },
            )
            if (!ready) {
                _state.value = _state.value.copy(
                    failedFiles = _state.value.failedFiles + files.size,
                    errors = _state.value.errors + "Could not create folder $label",
                )
                continue
            }

            for (file in files) {
                if (cancelled) break
                val size = file.length()
                _state.value = _state.value.copy(currentFile = file.name)
                if (!file.exists()) {
                    _state.value = _state.value.copy(failedFiles = _state.value.failedFiles + 1)
                    continue
                }
                val existing = File(monthDir, file.name)
                if (existing.exists() && existing.length() == size &&
                    existing.lastModified() == file.lastModified()
                ) {
                    file.delete()
                    _state.value = _state.value.copy(
                        skippedFiles = _state.value.skippedFiles + 1,
                        bytesDone = _state.value.bytesDone + size,
                    )
                    sample()
                    continue
                }
                val target = uniqueTarget(monthDir, file.name)
                val ok = runCatching {
                    if (!file.renameTo(target)) {
                        copyFile(file, target)
                        if (!cancelled) file.delete()
                    }
                }.onFailure { errors.add("${file.name}: ${it.message ?: "move failed"}") }.isSuccess

                _state.value = if (ok) {
                    _state.value.copy(
                        movedFiles = _state.value.movedFiles + 1,
                        bytesDone = _state.value.bytesDone + size,
                    )
                } else {
                    _state.value.copy(
                        failedFiles = _state.value.failedFiles + 1,
                        bytesDone = _state.value.bytesDone + size,
                        errors = (_state.value.errors + errors.takeLast(1)).takeLast(20),
                    )
                }
                sample()
            }
        }

        if (cancelled) {
            _state.value = _state.value.copy(
                phase = ArrangePhase.CANCELLED,
                message = "Stopped. Files already moved stay where they are.",
                finishedAt = System.currentTimeMillis(),
                speedBps = 0.0,
            )
            return
        }

        // Phase 3: find folders that the move left behind.
        _state.value = _state.value.copy(
            message = "Checking which folders are now empty…",
            currentFile = "",
            speedBps = 0.0,
        )
        val empties = findEmptyDirs(rootDir).map { it.absolutePath }
        _state.value = _state.value.copy(
            phase = ArrangePhase.REVIEW_CLEANUP,
            emptyFolders = empties,
            message = if (empties.isEmpty()) {
                "All files moved. No empty folders were left behind."
            } else {
                "All files moved. ${empties.size} folder(s) are now empty and can be removed."
            },
        )
    }

    /** Phase 4: remove the empty folders the user approved. */
    fun cleanUp() {
        val current = _state.value
        if (current.phase != ArrangePhase.REVIEW_CLEANUP) return
        _state.value = current.copy(
            phase = ArrangePhase.CLEANING,
            message = "Removing ${current.emptyFolders.size} empty folder(s)…",
        )
        scope.launch {
            var deleted = 0
            // Deepest first: the list is already child-before-parent.
            current.emptyFolders.forEach { path ->
                val dir = File(path)
                if (dir.isDirectory && (dir.listFiles()?.isEmpty() != false) && dir.delete()) deleted++
            }
            finish(deleted)
        }
    }

    fun skipCleanUp() {
        if (_state.value.phase != ArrangePhase.REVIEW_CLEANUP) return
        finish(0)
    }

    private fun finish(deletedFolders: Int) {
        val s = _state.value
        _state.value = s.copy(
            phase = ArrangePhase.DONE,
            deletedFolders = deletedFolders,
            finishedAt = System.currentTimeMillis(),
            message = buildString {
                if (s.mode == ArrangeMode.FLATTEN) {
                    append("Done. ${s.movedFiles} file(s) brought into one folder")
                } else {
                    append("Done. ${s.movedFiles} file(s) arranged into ${s.months.size} month folder(s)")
                }
                if (s.skippedFiles > 0) append(", ${s.skippedFiles} already in place")
                if (s.failedFiles > 0) append(", ${s.failedFiles} failed")
                if (deletedFolders > 0) append(", $deletedFolders empty folder(s) removed")
                append(".")
            },
        )
    }

    /** Post-order walk: a folder counts as empty when all its children are too. */
    private fun findEmptyDirs(root: File): List<File> {
        val result = mutableListOf<File>()
        fun visit(dir: File): Boolean {
            val children = dir.listFiles() ?: return false
            var empty = true
            for (child in children) {
                if (child.isDirectory) {
                    if (!visit(child)) empty = false
                } else {
                    empty = false
                }
            }
            if (empty && dir.absolutePath != root.absolutePath) result.add(dir)
            return empty
        }
        visit(root)
        return result
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

    private fun copyFile(src: File, dst: File) {
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
                }
                output.fd.sync()
            }
        }
        dst.setLastModified(src.lastModified())
    }
}
