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
 * Recoverable trash: deleted items move into a hidden app folder with a JSON
 * index remembering where they came from.
 */
object TrashManager {

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
        for (entry in entries) {
            val src = entry.toFile()
            if (!src.exists()) continue
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
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
        failed
    }

    suspend fun restore(selection: List<TrashItem>): Int = withContext(Dispatchers.IO) {
        val index = readIndex().toMutableList()
        var failed = 0
        for (item in selection) {
            val src = item.trashFile()
            if (!src.exists()) {
                index.removeAll { it.trashName == item.trashName }
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
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
        failed
    }

    suspend fun deleteForever(selection: List<TrashItem>) = withContext(Dispatchers.IO) {
        val index = readIndex().toMutableList()
        for (item in selection) {
            item.trashFile().deleteRecursively()
            index.removeAll { it.trashName == item.trashName }
        }
        writeIndex(index)
        items = index.filter { it.trashFile().exists() }
    }

    suspend fun emptyTrash() = withContext(Dispatchers.IO) {
        trashDir.deleteRecursively()
        writeIndex(emptyList())
        items = emptyList()
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
