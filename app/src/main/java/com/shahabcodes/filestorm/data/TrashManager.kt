package com.shahabcodes.filestorm.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashItem(
    val trashName: String,
    val originalPath: String,
    val name: String,
    val deletedAt: Long,
    val size: Long,
    val isDirectory: Boolean,
    /** Where it is now. Empty for items trashed before trash went per-volume. */
    val trashPath: String = "",
) {
    fun trashFile(): File =
        if (trashPath.isNotEmpty()) File(trashPath) else File(TrashManager.trashDir, trashName)
}

/**
 * Live progress for whichever trash operation is running. Every one of them can
 * take a while on a folder full of media, so they all report through here and a
 * single dialog renders it wherever the user happens to be.
 */
data class TrashProgress(
    val active: Boolean = false,
    val title: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    val currentName: String = "",
    val failed: Int = 0,
) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * Recoverable trash: deleted items move into a hidden app folder with a JSON
 * index remembering where they came from.
 */
object TrashManager {

    var progress by mutableStateOf(TrashProgress())
        private set

    private fun begin(title: String, total: Int, bytesTotal: Long) {
        progress = TrashProgress(
            active = true, title = title, total = total, bytesTotal = bytesTotal,
        )
    }

    private fun step(name: String, bytes: Long, ok: Boolean) {
        val p = progress
        progress = p.copy(
            done = p.done + 1,
            bytesDone = p.bytesDone + bytes,
            currentName = name,
            failed = p.failed + if (ok) 0 else 1,
        )
    }

    private fun finish() {
        progress = TrashProgress()
    }

    /** The primary volume's trash. Still where the index lives. */
    val trashDir: File get() = File(FileRepository.rootPath, ".FileStorm/Trash")
    private val indexFile: File get() = File(FileRepository.rootPath, ".FileStorm/trash-index.json")

    /**
     * The volume [file] sits on.
     *
     * Anything unrecognised resolves to primary storage, which is exactly what
     * happened before trash went per-volume — so an odd mount is no worse off.
     */
    private fun volumeRootOf(file: File): File {
        val primary = FileRepository.rootPath
        val path = file.absolutePath
        if (path == primary || path.startsWith("$primary/")) return File(primary)
        val parts = path.split(File.separatorChar).filter { it.isNotEmpty() }
        // Removable volumes mount at /storage/<id>; /storage/emulated is us.
        if (parts.size >= 2 && parts[0] == "storage" && parts[1] != "emulated") {
            return File("${File.separatorChar}${parts[0]}${File.separatorChar}${parts[1]}")
        }
        return File(primary)
    }

    /**
     * Trash on the same volume as the file, so deleting is a rename.
     *
     * A rename costs nothing and cannot half-finish, whatever the file's size.
     * Sending an SD card's files to a trash on internal storage meant a real
     * copy: minutes for a large video, internal space spent to free none, and
     * a chance of running out partway.
     *
     * A volume that will not take a trash folder — read-only, or gone — falls
     * back to primary, where the copy path still applies.
     */
    private fun trashDirFor(file: File): File {
        val dir = File(volumeRootOf(file), ".FileStorm/Trash")
        if (dir.isDirectory || dir.mkdirs()) return dir
        return trashDir.also { it.mkdirs() }
    }

    /**
     * Moves [src] to [target], copying only when a rename cannot work.
     *
     * A copy that runs out of space leaves a partial behind. Left alone it is
     * invisible to the index, so it can never be restored or emptied, and it
     * holds the very space that just ran out.
     */
    private fun relocate(src: File, target: File): Boolean {
        if (src.renameTo(target)) return true
        val copied = runCatching {
            src.copyRecursively(target, overwrite = false) && src.deleteRecursively()
        }.getOrDefault(false)
        if (!copied) runCatching { target.deleteRecursively() }
        return copied
    }

    var items by mutableStateOf<List<TrashItem>>(emptyList())
        private set

    suspend fun refresh() = withContext(Dispatchers.IO) {
        items = readIndex().filter { it.trashFile().exists() }
    }

    suspend fun moveToTrash(entries: List<FsEntry>): Int = withContext(Dispatchers.IO) {
        val index = readIndex().toMutableList()
        var failed = 0
        begin("Moving to Trash", entries.size, entries.sumOf { it.size })
        for (entry in entries) {
            val src = entry.toFile()
            if (!src.exists()) {
                step(entry.name, entry.size, true)
                continue
            }
            val trashName = "${System.currentTimeMillis()}_${entry.name}"
            val target = File(trashDirFor(src), trashName)
            val ok = relocate(src, target)
            if (ok) {
                index.add(
                    TrashItem(
                        trashName = trashName,
                        originalPath = entry.path,
                        name = entry.name,
                        deletedAt = System.currentTimeMillis(),
                        size = entry.size,
                        isDirectory = entry.isDirectory,
                        trashPath = target.absolutePath,
                    )
                )
            } else failed++
            step(entry.name, entry.size, ok)
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
        finish()
        failed
    }

    suspend fun restore(selection: List<TrashItem>): Int = withContext(Dispatchers.IO) {
        val index = readIndex().toMutableList()
        var failed = 0
        begin("Restoring", selection.size, selection.sumOf { it.size })
        for (item in selection) {
            val src = item.trashFile()
            if (!src.exists()) {
                index.removeAll { it.trashName == item.trashName }
                step(item.name, item.size, true)
                continue
            }
            var target = File(item.originalPath)
            target.parentFile?.mkdirs()
            if (target.exists()) {
                val base = item.name.substringBeforeLast('.', item.name)
                val ext = if (item.name.contains('.') && base != item.name)
                    "." + item.name.substringAfterLast('.') else ""
                var n = 1
                while (target.exists()) {
                    target = File(target.parentFile, "$base ($n)$ext")
                    n++
                }
            }
            val ok = relocate(src, target)
            if (ok) index.removeAll { it.trashName == item.trashName } else failed++
            step(item.name, item.size, ok)
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
        finish()
        failed
    }

    suspend fun deleteForever(selection: List<TrashItem>) = withContext(Dispatchers.IO) {
        val index = readIndex().toMutableList()
        begin("Deleting permanently", selection.size, selection.sumOf { it.size })
        for (item in selection) {
            val ok = item.trashFile().deleteRecursively()
            index.removeAll { it.trashName == item.trashName }
            step(item.name, item.size, ok)
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
        finish()
    }

    /**
     * Deletes item by item rather than wiping the folder in one call, so the
     * user can see it working — emptying a full trash is not instant.
     */
    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        val index = readIndex()
        begin("Emptying Trash", index.size, index.sumOf { it.size })
        for (item in index) {
            val ok = runCatching { item.trashFile().deleteRecursively() }.getOrDefault(false)
            step(item.name, item.size, ok)
        }
        // Anything the index did not know about goes too — including partials
        // from older versions, which had no cleanup and could leave them.
        val dirs = (index.mapNotNull { it.trashFile().parentFile } + trashDir)
            .distinctBy { it.absolutePath }
        dirs.forEach { runCatching { it.deleteRecursively() } }
        writeIndex(emptyList())
        items = emptyList()
        finish()
    }

    private fun readIndex(): List<TrashItem> = runCatching {
        if (!indexFile.exists()) return emptyList()
        val array = JSONArray(indexFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    TrashItem(
                        trashName = o.getString("trashName"),
                        originalPath = o.getString("originalPath"),
                        name = o.getString("name"),
                        deletedAt = o.getLong("deletedAt"),
                        size = o.optLong("size", 0L),
                        isDirectory = o.optBoolean("isDirectory", false),
                        trashPath = o.optString("trashPath", ""),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun writeIndex(index: List<TrashItem>) {
        runCatching {
            indexFile.parentFile?.mkdirs()
            val array = JSONArray()
            index.forEach { item ->
                array.put(
                    JSONObject()
                        .put("trashName", item.trashName)
                        .put("originalPath", item.originalPath)
                        .put("name", item.name)
                        .put("deletedAt", item.deletedAt)
                        .put("size", item.size)
                        .put("isDirectory", item.isDirectory)
                        .put("trashPath", item.trashPath)
                )
            }
            indexFile.writeText(array.toString())
        }
    }
}
