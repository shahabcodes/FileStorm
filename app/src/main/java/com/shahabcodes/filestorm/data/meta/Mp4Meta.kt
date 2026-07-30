package com.shahabcodes.filestorm.data.meta

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads and patches the creation time stored in ISO base-media containers
 * (MP4/M4V/MOV/3GP). Only the timestamp fields inside the mvhd/tkhd/mdhd boxes
 * are rewritten in place — the file size never changes and no re-encoding
 * happens, so the video itself is untouched.
 */
object Mp4Meta {

    /** ISO BMFF timestamps count seconds from 1904-01-01 UTC. */
    private const val EPOCH_OFFSET = 2_082_844_800L

    private data class Box(val type: String, val payloadStart: Long, val payloadEnd: Long)

    val supportedExtensions = setOf("mp4", "m4v", "mov", "3gp", "3g2", "m4a")

    fun isSupported(file: File): Boolean = file.extension.lowercase() in supportedExtensions

    fun readCreation(file: File): Long? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val moov = boxes(raf, 0, raf.length()).firstOrNull { it.type == "moov" } ?: return null
            val mvhd = boxes(raf, moov.payloadStart, moov.payloadEnd)
                .firstOrNull { it.type == "mvhd" } ?: return null
            raf.seek(mvhd.payloadStart)
            val version = raf.readByte().toInt() and 0xFF
            raf.seek(mvhd.payloadStart + 4)
            val seconds = when (version) {
                0 -> raf.readInt().toLong() and 0xFFFFFFFFL
                1 -> raf.readLong()
                else -> return null
            }
            val unix = seconds - EPOCH_OFFSET
            if (unix <= 0) null else unix * 1000
        }
    }.getOrNull()

    /** Returns true when at least the movie header was updated. */
    fun writeCreation(file: File, unixMillis: Long): Boolean = runCatching {
        RandomAccessFile(file, "rw").use { raf ->
            val moov = boxes(raf, 0, raf.length()).firstOrNull { it.type == "moov" }
                ?: return false
            val moovChildren = boxes(raf, moov.payloadStart, moov.payloadEnd)
            val mvhd = moovChildren.firstOrNull { it.type == "mvhd" } ?: return false
            if (!patchTimes(raf, mvhd, unixMillis)) return false

            // Track headers too, so players that read per-track dates agree.
            moovChildren.filter { it.type == "trak" }.forEach { trak ->
                val trakChildren = boxes(raf, trak.payloadStart, trak.payloadEnd)
                trakChildren.firstOrNull { it.type == "tkhd" }
                    ?.let { patchTimes(raf, it, unixMillis) }
                trakChildren.firstOrNull { it.type == "mdia" }?.let { mdia ->
                    boxes(raf, mdia.payloadStart, mdia.payloadEnd)
                        .firstOrNull { it.type == "mdhd" }
                        ?.let { patchTimes(raf, it, unixMillis) }
                }
            }
            true
        }
    }.getOrDefault(false)

    /** Walks the sibling boxes in [start, end), stopping at the first malformed one. */
    private fun boxes(raf: RandomAccessFile, start: Long, end: Long): List<Box> {
        val out = mutableListOf<Box>()
        var pos = start
        while (pos + 8 <= end) {
            raf.seek(pos)
            val declared = raf.readInt().toLong() and 0xFFFFFFFFL
            val typeBytes = ByteArray(4)
            raf.readFully(typeBytes)
            val type = String(typeBytes, Charsets.US_ASCII)
            var header = 8L
            var size = declared
            when (declared) {
                1L -> {
                    if (pos + 16 > end) break
                    size = raf.readLong()
                    header = 16L
                }
                0L -> size = end - pos
            }
            if (size < header || pos + size > end) break
            out.add(Box(type, pos + header, pos + size))
            pos += size
        }
        return out
    }

    /** Full box layout: version(1) flags(3) creation modification … */
    private fun patchTimes(raf: RandomAccessFile, box: Box, unixMillis: Long): Boolean {
        if (box.payloadStart + 4 > box.payloadEnd) return false
        raf.seek(box.payloadStart)
        val version = raf.readByte().toInt() and 0xFF
        val seconds = unixMillis / 1000 + EPOCH_OFFSET
        return when (version) {
            0 -> {
                if (seconds < 0 || seconds > 0xFFFFFFFFL) return false
                if (box.payloadStart + 12 > box.payloadEnd) return false
                raf.seek(box.payloadStart + 4)
                raf.writeInt(seconds.toInt())
                raf.writeInt(seconds.toInt())
                true
            }
            1 -> {
                if (box.payloadStart + 20 > box.payloadEnd) return false
                raf.seek(box.payloadStart + 4)
                raf.writeLong(seconds)
                raf.writeLong(seconds)
                true
            }
            else -> false
        }
    }
}
