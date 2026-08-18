package com.shahaabapps.filestorm.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageStats(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = totalBytes - freeBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

object FileRepository {

    val rootPath: String = Environment.getExternalStorageDirectory().absolutePath

    private class Cached(val stamp: Long, val showHidden: Boolean, val entries: List<FsEntry>) {
        /**
         * Sorting 30,000 entries costs a partition, two sorts and a concat. The
         * browser asks for the same order almost every time, so each order is
         * kept once rather than rebuilt on every visit.
         */
        val sorted = java.util.concurrent.ConcurrentHashMap<String, List<FsEntry>>()
    }

    private fun sortKey(field: SortField, ascending: Boolean) = "${field.name}:$ascending"

    /**
     * Directory listings are cached per path and reused while the folder's own
     * timestamp is unchanged, so returning to a folder holding tens of thousands
     * of files does not re-stat every entry.
     */
    private val listCache = java.util.concurrent.ConcurrentHashMap<String, Cached>()

    fun invalidate(path: String? = null) {
        if (path == null) listCache.clear() else listCache.remove(path)
    }

    /**
     * What is already in memory for [path], or null. Touches no disk at all, so
     * it is safe to call while composing.
     *
     * The browser builds a new ViewModel every time you walk into a folder, so
     * without this a revisit starts from an empty list and shows a spinner even
     * though every entry is already known. Painting from here first makes going
     * back into a large folder instant; [list] still runs behind it and
     * replaces this if the folder actually changed.
     */
    fun peek(
        path: String,
        field: SortField = Prefs.sortField,
        ascending: Boolean = Prefs.sortAscending,
        showHidden: Boolean = Prefs.showHidden,
    ): List<FsEntry>? {
        val cached = listCache[path] ?: return null
        if (cached.showHidden != showHidden) return null
        // Deliberately no freshness check: a stat here would be disk I/O on the
        // main thread, and list() corrects anything stale a moment later.
        return cached.sorted[sortKey(field, ascending)]
    }

    suspend fun list(
        path: String,
        field: SortField = Prefs.sortField,
        ascending: Boolean = Prefs.sortAscending,
        showHidden: Boolean = Prefs.showHidden,
    ): List<FsEntry> = withContext(Dispatchers.IO) {
        val dir = File(path)
        val stamp = dir.lastModified()
        var cached = listCache[path]
        if (cached == null || cached.stamp != stamp || cached.showHidden != showHidden) {
            // The in-memory cache dies with the process, so a folder of tens of
            // thousands of files was re-walked from scratch every cold start.
            // Disk holds the same listing and is read in a fraction of the time.
            ListingCache.read(path, stamp, showHidden)?.let { stored ->
                val restored = Cached(stamp, showHidden, stored)
                listCache[path] = restored
                val key = sortKey(field, ascending)
                return@withContext restored.sorted[key]
                    ?: sortEntries(stored, field, ascending).also { restored.sorted[key] = it }
            }
            val children = dir.listFiles() ?: return@withContext emptyList()
            val fresh = ArrayList<FsEntry>(children.size)
            for (child in children) {
                if (child.name == ".FileStorm") continue
                // A half-written archive is not a file the user has.
                if (child.name.endsWith(com.shahaabapps.filestorm.data.archive.ArchiveManager.PART_SUFFIX)) continue
                if (!showHidden && child.name.startsWith(".")) continue
                fresh.add(FsEntry.from(child))
            }
            cached = Cached(stamp, showHidden, fresh)
            listCache[path] = cached
            ListingCache.write(path, stamp, showHidden, fresh)
        }
        val key = sortKey(field, ascending)
        cached.sorted[key] ?: sortEntries(cached.entries, field, ascending)
            .also { cached.sorted[key] = it }
    }

    /** Folders always group first; the chosen order applies within each group. */
    fun sortEntries(entries: List<FsEntry>, field: SortField, ascending: Boolean): List<FsEntry> {
        val (dirs, files) = entries.partition { it.isDirectory }
        fun apply(list: List<FsEntry>): List<FsEntry> {
            val comparator: Comparator<FsEntry> = when (field) {
                SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                SortField.DATE -> compareBy { it.lastModified }
                SortField.SIZE -> compareBy { it.size }
                SortField.TYPE -> compareBy<FsEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.extension }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            }
            return list.sortedWith(if (ascending) comparator else comparator.reversed())
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
                    if (child.name == ".FileStorm") continue
                // A half-written archive is not a file the user has.
                if (child.name.endsWith(com.shahaabapps.filestorm.data.archive.ArchiveManager.PART_SUFFIX)) continue
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

    private val kindCache = java.util.concurrent.ConcurrentHashMap<FileKind, Pair<Long, List<FsEntry>>>()

    fun invalidateKinds() = kindCache.clear()

    /** Category scans walk the whole tree, so results are reused for a while. */
    suspend fun filesByKind(
        kind: FileKind,
        limit: Int = 2000,
        maxAgeMillis: Long = 5 * 60 * 1000L,
    ): List<FsEntry> = withContext(Dispatchers.IO) {
        kindCache[kind]?.let { (stamp, cached) ->
            if (System.currentTimeMillis() - stamp < maxAgeMillis) return@withContext cached
        }
        scanKind(kind, limit).also { kindCache[kind] = System.currentTimeMillis() to it }
    }

    private suspend fun scanKind(kind: FileKind, limit: Int): List<FsEntry> =
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
                    if (child.name == ".FileStorm") continue
                // A half-written archive is not a file the user has.
                if (child.name.endsWith(com.shahaabapps.filestorm.data.archive.ArchiveManager.PART_SUFFIX)) continue
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

    fun createFolder(parent: String, name: String): Boolean {
        invalidate(parent)
        return File(parent, name.trim()).mkdirs()
    }

    fun rename(entry: FsEntry, newName: String): Boolean {
        val target = File(entry.toFile().parentFile, newName.trim())
        if (target.exists()) return false
        invalidate(entry.toFile().parent)
        return entry.toFile().renameTo(target)
    }

    suspend fun delete(entries: List<FsEntry>): Int = withContext(Dispatchers.IO) {
        entries.forEach { invalidate(it.toFile().parent) }
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
