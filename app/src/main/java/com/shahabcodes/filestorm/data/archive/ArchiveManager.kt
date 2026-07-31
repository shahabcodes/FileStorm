package com.shahabcodes.filestorm.data.archive

import com.shahabcodes.filestorm.data.FileRepository
import com.shahabcodes.filestorm.data.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Creates and unpacks zip archives with live progress. */
object ArchiveManager {

    data class Progress(
        val currentName: String,
        val doneFiles: Int,
        val totalFiles: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        val startedAt: Long,
    ) {
        val fraction: Float
            get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
        val etaSeconds: Long
            get() {
                if (bytesDone <= 0) return -1
                val elapsed = System.currentTimeMillis() - startedAt
                val perByte = elapsed.toDouble() / bytesDone
                return ((bytesTotal - bytesDone) * perByte / 1000).toLong()
            }
    }

    data class Outcome(
        val ok: Boolean,
        val files: Int,
        val bytes: Long,
        val target: String,
        val error: String? = null,
    )

    val supportedArchives = setOf("zip")

    fun isArchive(file: File): Boolean = file.extension.lowercase() in supportedArchives

    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
    }

    /** Packs [entries] (files and whole folders) into [target]. */
    suspend fun zip(
        entries: List<FsEntry>,
        target: File,
        onProgress: (Progress) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        cancelled = false
        val startedAt = System.currentTimeMillis()

        // Flatten the selection into (file, entry name) pairs first so totals and
        // progress are accurate before a single byte is written.
        val planned = mutableListOf<Pair<File, String>>()
        entries.forEach { entry ->
            val file = entry.toFile()
            if (!file.exists()) return@forEach
            if (file.isDirectory) {
                val base = file.parentFile?.absolutePath ?: ""
                val queue = ArrayDeque<File>()
                queue.add(file)
                while (queue.isNotEmpty()) {
                    val dir = queue.removeFirst()
                    val children = dir.listFiles() ?: continue
                    if (children.isEmpty()) {
                        planned.add(dir to dir.absolutePath.removePrefix("$base/") + "/")
                    }
                    children.forEach { child ->
                        if (child.isDirectory) queue.add(child)
                        else planned.add(child to child.absolutePath.removePrefix("$base/"))
                    }
                }
            } else {
                planned.add(file to file.name)
            }
        }

        if (planned.isEmpty()) {
            return@withContext Outcome(false, 0, 0, target.absolutePath, "Nothing to compress")
        }

        val totalBytes = planned.sumOf { if (it.first.isDirectory) 0L else it.first.length() }
        var bytesDone = 0L
        var doneFiles = 0

        val result = runCatching {
            ZipOutputStream(FileOutputStream(target).buffered()).use { zos ->
                val buffer = ByteArray(256 * 1024)
                planned.forEach { (file, entryName) ->
                    if (cancelled) error("Cancelled")
                    onProgress(
                        Progress(file.name, doneFiles, planned.size, bytesDone, totalBytes, startedAt)
                    )
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.closeEntry()
                    } else {
                        val zipEntry = ZipEntry(entryName).apply { time = file.lastModified() }
                        zos.putNextEntry(zipEntry)
                        FileInputStream(file).use { input ->
                            while (true) {
                                if (cancelled) error("Cancelled")
                                val read = input.read(buffer)
                                if (read < 0) break
                                zos.write(buffer, 0, read)
                                bytesDone += read
                                onProgress(
                                    Progress(
                                        file.name, doneFiles, planned.size,
                                        bytesDone, totalBytes, startedAt,
                                    )
                                )
                            }
                        }
                        zos.closeEntry()
                    }
                    doneFiles++
                }
            }
        }

        FileRepository.invalidate(target.parent)
        if (result.isFailure) {
            target.delete()
            return@withContext Outcome(
                false, doneFiles, bytesDone, target.absolutePath,
                result.exceptionOrNull()?.message ?: "Could not create the archive",
            )
        }
        Outcome(true, doneFiles, target.length(), target.absolutePath)
    }

    /** Unpacks [archive] into [destination], refusing entries that escape it. */
    suspend fun unzip(
        archive: File,
        destination: File,
        onProgress: (Progress) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        cancelled = false
        val startedAt = System.currentTimeMillis()

        val totals = runCatching {
            ZipFile(archive).use { zf ->
                var count = 0
                var bytes = 0L
                val e = zf.entries()
                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    if (!entry.isDirectory) {
                        count++
                        bytes += if (entry.size > 0) entry.size else 0L
                    }
                }
                count to bytes
            }
        }.getOrElse { 0 to 0L }

        if (!destination.exists() && !destination.mkdirs()) {
            return@withContext Outcome(false, 0, 0, destination.absolutePath, "Could not create the destination")
        }

        var doneFiles = 0
        var bytesDone = 0L
        val destRoot = destination.canonicalPath

        val result = runCatching {
            ZipInputStream(FileInputStream(archive).buffered()).use { zis ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    if (cancelled) error("Cancelled")
                    val entry = zis.nextEntry ?: break
                    val outFile = File(destination, entry.name)
                    // Zip-slip guard: an entry must never resolve outside the target.
                    if (!outFile.canonicalPath.startsWith(destRoot + File.separator) &&
                        outFile.canonicalPath != destRoot
                    ) {
                        zis.closeEntry()
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        onProgress(
                            Progress(
                                outFile.name, doneFiles, totals.first,
                                bytesDone, totals.second, startedAt,
                            )
                        )
                        FileOutputStream(outFile).use { output ->
                            while (true) {
                                if (cancelled) error("Cancelled")
                                val read = zis.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                bytesDone += read
                                onProgress(
                                    Progress(
                                        outFile.name, doneFiles, totals.first,
                                        bytesDone, totals.second, startedAt,
                                    )
                                )
                            }
                        }
                        if (entry.time > 0) outFile.setLastModified(entry.time)
                        doneFiles++
                    }
                    zis.closeEntry()
                }
            }
        }

        FileRepository.invalidate(destination.absolutePath)
        FileRepository.invalidate(destination.parent)
        if (result.isFailure) {
            return@withContext Outcome(
                false, doneFiles, bytesDone, destination.absolutePath,
                result.exceptionOrNull()?.message ?: "Could not extract the archive",
            )
        }
        Outcome(true, doneFiles, bytesDone, destination.absolutePath)
    }

    /** "photos" -> "photos.zip", avoiding an existing name. */
    fun uniqueArchive(folder: File, baseName: String): File {
        var candidate = File(folder, "$baseName.zip")
        var n = 1
        while (candidate.exists()) {
            candidate = File(folder, "$baseName ($n).zip")
            n++
        }
        return candidate
    }
}
