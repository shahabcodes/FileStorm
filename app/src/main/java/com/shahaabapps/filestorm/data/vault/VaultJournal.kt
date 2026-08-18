package com.shahaabapps.filestorm.data.vault

import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32

/** Where a file had got to when the process last stopped. */
enum class VaultState {
    /** Work announced. Original intact, temp may or may not exist. */
    STARTED,

    /** Encrypted temp written and flushed, but not yet proven correct. */
    WRITTEN,

    /** Temp decrypts back to the original's hash. Safe to put in place. */
    VERIFIED,

    /** Encrypted file is at its final name. Original still present. */
    PLACED,

    /** Original removed. Nothing left to do. */
    DONE,

    /** Gave up on this file. The original was never touched. */
    FAILED,
}

data class VaultRecord(
    val id: String,
    val state: VaultState,
    val source: String,
    val temp: String,
    val target: String,
    val detail: String = "",
)

/**
 * An append-only record of what is happening, written to disk before each step
 * and flushed. Nothing about an in-progress run lives only in memory, so a
 * reboot, a crash, or Android killing the process are all the same case: read
 * this back and carry on.
 *
 * Every line carries a CRC. A line half-written when the power went is detected
 * and ignored, which matters because that is exactly the line most likely to be
 * damaged.
 */
class VaultJournal(private val file: File) {

    /**
     * Synchronised because several files are encrypted at once. Two threads
     * appending at the same moment would interleave their bytes and produce a
     * line that survives its own CRC check but describes neither record.
     */
    @Synchronized
    fun append(record: VaultRecord) {
        val body = listOf(
            record.id,
            record.state.name,
            escape(record.source),
            escape(record.temp),
            escape(record.target),
            escape(record.detail),
        ).joinToString("\t")
        val crc = CRC32().apply { update(body.toByteArray(Charsets.UTF_8)) }.value
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { out ->
            // If the previous write was cut short by a crash the file ends
            // mid-line, and appending straight onto it would fuse the torn
            // record and the new one into a single unreadable line — losing the
            // new record as well as the old. Close the broken line first.
            if (endsMidLine()) out.write('\n'.code)
            out.write("$crc\t$body\n".toByteArray(Charsets.UTF_8))
            out.flush()
            // Without this the record can still be in the page cache when the
            // power goes, which would defeat the point of writing it first.
            runCatching { out.fd.sync() }
        }
    }

    /** The latest state of every file the journal mentions. */
    fun replay(): Map<String, VaultRecord> {
        if (!file.exists()) return emptyMap()
        val latest = LinkedHashMap<String, VaultRecord>()
        file.forEachLine(Charsets.UTF_8) { line ->
            val record = parse(line)
            if (record != null) latest[record.id] = record
        }
        return latest
    }

    /** Records still needing attention, in the order they were started. */
    fun unfinished(): List<VaultRecord> =
        replay().values.filter { it.state != VaultState.DONE && it.state != VaultState.FAILED }

    fun clear() {
        runCatching { file.delete() }
    }

    fun exists(): Boolean = file.exists()

    /** True when the last write was interrupted before its newline landed. */
    private fun endsMidLine(): Boolean = runCatching {
        if (!file.isFile || file.length() == 0L) return false
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(raf.length() - 1)
            raf.readByte() != '\n'.code.toByte()
        }
    }.getOrDefault(false)

    private fun parse(line: String): VaultRecord? {
        if (line.isBlank()) return null
        val cut = line.indexOf('\t')
        if (cut <= 0) return null
        val declared = line.substring(0, cut).toLongOrNull() ?: return null
        val body = line.substring(cut + 1)
        val actual = CRC32().apply { update(body.toByteArray(Charsets.UTF_8)) }.value
        // A torn final write fails here and is skipped, leaving the file at its
        // previous known state rather than at a corrupted one.
        if (declared != actual) return null
        val parts = body.split("\t")
        if (parts.size < 6) return null
        val state = runCatching { VaultState.valueOf(parts[1]) }.getOrNull() ?: return null
        return VaultRecord(
            id = parts[0],
            state = state,
            source = unescape(parts[2]),
            temp = unescape(parts[3]),
            target = unescape(parts[4]),
            detail = unescape(parts[5]),
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    't' -> { out.append('\t'); i += 2 }
                    'n' -> { out.append('\n'); i += 2 }
                    '\\' -> { out.append('\\'); i += 2 }
                    else -> { out.append(c); i++ }
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
