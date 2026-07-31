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
) {
    fun trashFile(): File = File(TrashManager.trashDir, trashName)
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

    val trashDir: File get() = File(FileRepository.rootPath, ".FileStorm/Trash")
    private val indexFile: File get() = File(FileRepository.rootPath, ".FileStorm/trash-index.json")

    var items by mutableStateOf<List<TrashItem>>(emptyList())
        private set

    suspend fun refresh() = withContext(Dispatchers.IO) {
        items = readIndex().filter { it.trashFile().exists() }
    }

    suspend fun moveToTrash(entries: List<FsEntry>): Int = withContext(Dispatchers.IO) {
        trashDir.mkdirs()
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
            val target = File(trashDir, trashName)
            val ok = src.renameTo(target) || runCatching {
                src.copyRecursively(target, overwrite = false) && src.deleteRecursively()
            }.getOrDefault(false)
            if (ok) {
                index.add(
                    TrashItem(
                        trashName = trashName,
                        originalPath = entry.path,
                        name = entry.name,
                        deletedAt = System.currentTimeMillis(),
                        size = entry.size,
                        isDirectory = entry.isDirectory,
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
            val ok = src.renameTo(target) || runCatching {
                src.copyRecursively(target, overwrite = false) && src.deleteRecursively()
            }.getOrDefault(false)
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
        // Anything the index did not know about goes too.
        runCatching { trashDir.deleteRecursively() }
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
                )
            }
            indexFile.writeText(array.toString())
        }
    }
}
