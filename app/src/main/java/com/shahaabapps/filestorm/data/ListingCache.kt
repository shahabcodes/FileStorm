package com.shahaabapps.filestorm.data

import java.io.File

/**
 * The directory listing cache, written to disk so it survives the process.
 *
 * In memory alone the cache is lost the moment the app is swiped out of
 * recents, and the next visit to a folder of tens of thousands of files pays
 * for the whole walk again. That walk is not one syscall per file either:
 * [FsEntry.from] asks for the type, the size, the modified time, and — for a
 * folder — its child count, which is a directory read of its own.
 *
 * Rereading a few hundred kilobytes of text beats tens of thousands of stats by
 * a wide margin, so a cold start can paint immediately and only re-walk folders
 * that actually changed.
 *
 * The format is deliberately not JSON: one tab-separated line per entry, parsed
 * with [String.split]. A parser is the wrong place to spend the time saved.
 */
object ListingCache {

    /** Bumped when the line format changes, so old files are simply ignored. */
    private const val VERSION = "1"

    /**
     * Small folders are quick to walk and would cost more in cache files than
     * they save, so only the ones that actually hurt are kept.
     */
    private const val MIN_ENTRIES = 150

    /** Enough for the folders anyone revisits; the rest is churn. */
    private const val MAX_FILES = 40

    private val dir: File get() = File(FileRepository.rootPath, ".FileStorm/listings")

    private fun fileFor(path: String): File = File(dir, path.hashCode().toString() + ".tsv")

    /**
     * The cached listing for [path], or null if there is none, it is stale, or
     * it was written for different settings.
     */
    fun read(path: String, stamp: Long, showHidden: Boolean): List<FsEntry>? = runCatching {
        val file = fileFor(path)
        if (!file.isFile) return null
        val lines = file.readLines()
        if (lines.size < 2) return null

        // The header carries the path itself, because two paths can hash alike.
        val header = lines[0].split('\t')
        if (header.size < 4) return null
        if (header[0] != VERSION) return null
        if (header[1] != path) return null
        if (header[2].toLongOrNull() != stamp) return null
        if (header[3].toBooleanStrictOrNull() != showHidden) return null

        val entries = ArrayList<FsEntry>(lines.size - 1)
        for (i in 1 until lines.size) {
            val f = lines[i].split('\t')
            if (f.size < 5) continue
            val name = f[0]
            val isDirectory = f[1] == "1"
            entries.add(
                FsEntry(
                    path = "$path${File.separatorChar}$name",
                    name = name,
                    isDirectory = isDirectory,
                    size = f[2].toLongOrNull() ?: 0L,
                    lastModified = f[3].toLongOrNull() ?: 0L,
                    kind = FsEntry.kindOf(name, isDirectory),
                    childCount = f[4].toIntOrNull() ?: -1,
                )
            )
        }
        entries
    }.getOrNull()

    /** Records [entries] for [path]. Silently does nothing for small folders. */
    fun write(path: String, stamp: Long, showHidden: Boolean, entries: List<FsEntry>) {
        if (entries.size < MIN_ENTRIES) return
        runCatching {
            dir.mkdirs()
            val text = buildString(entries.size * 48) {
                append(VERSION).append('\t')
                    .append(path).append('\t')
                    .append(stamp).append('\t')
                    .append(showHidden).append('\n')
                entries.forEach { e ->
                    // A tab or newline in a filename would corrupt the line, so
                    // such a folder simply is not cached rather than half-read.
                    if (e.name.contains('\t') || e.name.contains('\n')) return@runCatching
                    append(e.name).append('\t')
                        .append(if (e.isDirectory) '1' else '0').append('\t')
                        .append(e.size).append('\t')
                        .append(e.lastModified).append('\t')
                        .append(e.childCount).append('\n')
                }
            }
            fileFor(path).writeText(text)
            prune()
        }
    }

    fun clear() {
        runCatching { dir.deleteRecursively() }
    }

    /** Keeps the newest [MAX_FILES]; the rest were one-off visits. */
    private fun prune() {
        runCatching {
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            if (files.size <= MAX_FILES) return
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_FILES)
                .forEach { it.delete() }
        }
    }
}
