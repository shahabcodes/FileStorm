package com.shahabcodes.filestorm.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SortMode(val label: String) {
    NAME("Name"),
    DATE_NEWEST("Newest first"),
    DATE_OLDEST("Oldest first"),
    SIZE_LARGEST("Largest first"),
    TYPE("Type"),
}

data class StorageStats(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

object FileRepository {

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    suspend fun list(path: String, sort: SortMode, showHidden: Boolean = Prefs.showHidden): List<FsEntry> =
        withContext(Dispatchers.IO) {
            val dir = File(path)
            val children = dir.listFiles() ?: return@withContext emptyList()
            val entries = children
                .filter { showHidden || !it.name.startsWith(".") }
                .map { FsEntry.from(it) }
            sortEntries(entries, sort)
        }

    fun sortEntries(entries: List<FsEntry>, sort: SortMode): List<FsEntry> {
        val (dirs, files) = entries.partition { it.isDirectory }
        fun apply(list: List<FsEntry>): List<FsEntry> = when (sort) {
            SortMode.NAME -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            SortMode.DATE_NEWEST -> list.sortedByDescending { it.lastModified }
            SortMode.DATE_OLDEST -> list.sortedBy { it.lastModified }
            SortMode.SIZE_LARGEST -> list.sortedByDescending { it.size }
            SortMode.TYPE -> list.sortedWith(compareBy({ it.extension }, { it.name.lowercase() }))
        }
        return apply(dirs) + apply(files)
    }

    suspend fun search(rootDir: String, query: String, limit: Int = 400): List<FsEntry> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FsEntry>()
            val q = query.trim().lowercase()
            if (q.isEmpty()) return@withContext results
            val showHidden = Prefs.showHidden
            val queue = ArrayDeque<File>()
            queue.add(File(rootDir))
            while (queue.isNotEmpty() && results.size < limit) {
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (!showHidden && child.name.startsWith(".")) continue
                    if (child.name.lowercase().contains(q)) {
                        results.add(FsEntry.from(child))
                        if (results.size >= limit) break
                    }
                    if (child.isDirectory) queue.add(child)
                }
            }
            results
        }

    suspend fun filesByKind(kind: FileKind, limit: Int = 2000): List<FsEntry> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FsEntry>()
            val showHidden = Prefs.showHidden
            val queue = ArrayDeque<File>()
            queue.add(File(rootPath))
            while (queue.isNotEmpty() && results.size < limit) {
                val dir = queue.removeFirst()
                if (!showHidden && dir.name.startsWith(".")) continue
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    if (!showHidden && child.name.startsWith(".")) continue
                    if (child.isDirectory) {
                        if (child.name != "Android") queue.add(child)
                    } else if (FsEntry.kindOf(child.name, false) == kind) {
                        results.add(FsEntry.from(child))
                        if (results.size >= limit) break
                    }
                }
            }
            results.sortedByDescending { it.lastModified }
        }

    fun storageStats(): StorageStats {
        val stat = StatFs(rootPath)
        return StorageStats(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }

    fun createFolder(parent: String, name: String): Boolean =
        File(parent, name.trim()).mkdirs()

    fun rename(entry: FsEntry, newName: String): Boolean {
        val target = File(entry.toFile().parentFile, newName.trim())
        if (target.exists()) return false
        return entry.toFile().renameTo(target)
    }

    suspend fun delete(entries: List<FsEntry>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        entries.forEach { if (!it.toFile().deleteRecursively()) failed++ }
        failed
    }

    data class FolderStats(val files: Int, val folders: Int, val bytes: Long, val scanning: Boolean)

    /** Recursively counts files/folders and sums bytes, emitting progress as it scans. */
    suspend fun folderStats(path: String, onProgress: (FolderStats) -> Unit): FolderStats =
        withContext(Dispatchers.IO) {
            var files = 0
            var folders = 0
            var bytes = 0L
            var lastEmit = 0L
            val queue = ArrayDeque<File>()
            queue.add(File(path))
            while (queue.isNotEmpty()) {
                val dir = queue.removeFirst()
                val children = dir.listFiles() ?: continue
                for (c in children) {
                    if (c.isDirectory) {
                        folders++
                        queue.add(c)
                    } else {
                        files++
                        bytes += c.length()
                    }
                }
                val now = System.currentTimeMillis()
                if (now - lastEmit > 120) {
                    lastEmit = now
                    onProgress(FolderStats(files, folders, bytes, scanning = true))
                }
            }
            FolderStats(files, folders, bytes, scanning = false)
        }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Deletion that survives leaving the screen. */
    fun deleteAsync(entries: List<FsEntry>, onDone: () -> Unit = {}) {
        repoScope.launch {
            delete(entries)
            onDone()
        }
    }
}
