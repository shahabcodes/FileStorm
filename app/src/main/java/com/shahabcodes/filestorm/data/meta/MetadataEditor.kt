package com.shahabcodes.filestorm.data.meta

import android.content.Context
import android.media.MediaScannerConnection
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads and rewrites photo dates: EXIF tags plus the file's modified time. */
object MetadataEditor {

    /** Formats androidx ExifInterface expects: "yyyy:MM:dd HH:mm:ss". */
    private val exifFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    /** Formats ExifInterface can write back; others get a file-timestamp-only update. */
    private val writableExif = setOf("jpg", "jpeg", "png", "webp")
    private val readableExif = writableExif + setOf("heic", "heif", "dng", "tiff", "tif", "avif")

    data class Current(
        val fileDate: Long,
        val exifDate: Long?,
        val exifWritable: Boolean,
        val isImage: Boolean,
        val videoDate: Long? = null,
        val videoWritable: Boolean = false,
        val isVideo: Boolean = false,
    )

    data class Change(
        val file: File,
        val newMillis: Long,
        val source: String,
    )

    /** Live detail for the progress dialog. */
    data class Progress(
        val done: Int,
        val total: Int,
        val currentName: String,
        val currentIsVideo: Boolean,
        val exifWritten: Int,
        val videoWritten: Int,
        val fileDatesSet: Int,
        val failed: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        val startedAt: Long,
    ) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
        val elapsedSeconds: Long get() = (System.currentTimeMillis() - startedAt) / 1000
        val etaSeconds: Long
            get() {
                if (done == 0) return -1
                val perItem = (System.currentTimeMillis() - startedAt).toDouble() / done
                return ((total - done) * perItem / 1000).toLong()
            }
    }

    data class Outcome(
        val succeeded: Int,
        val failed: Int,
        val exifWritten: Int,
        val videoWritten: Int,
        val errors: List<String>,
    )

    /**
     * True when the file's dates already agree with [target], so there is
     * nothing to rewrite. Filename dates without a time only need the same day.
     */
    fun alreadyCorrect(file: File, target: Long, hasTime: Boolean): Boolean {
        val current = read(file)
        val primary = when {
            current.isVideo -> current.videoDate
            current.isImage -> current.exifDate
            else -> null
        }
        // A missing primary date is exactly what we are here to repair.
        if ((current.isVideo || current.isImage) && primary == null) return false
        val close = { value: Long -> if (hasTime) kotlin.math.abs(value - target) <= 120_000L else sameDay(value, target) }
        if (primary != null && !close(primary)) return false
        return close(current.fileDate)
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
        val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun read(file: File): Current {
        val ext = file.extension.lowercase()
        val isImage = ext in readableExif
        var exifDate: Long? = null
        if (isImage) {
            runCatching {
                val exif = ExifInterface(file.absolutePath)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                if (raw != null) exifDate = exifFormat.parse(raw)?.time
            }
        }
        val isVideo = Mp4Meta.isSupported(file)
        return Current(
            fileDate = file.lastModified(),
            exifDate = exifDate,
            exifWritable = ext in writableExif,
            isImage = isImage,
            videoDate = if (isVideo) Mp4Meta.readCreation(file) else null,
            videoWritable = isVideo,
            isVideo = isVideo,
        )
    }

    /**
     * Applies new dates. EXIF is written first because saving it rewrites the
     * file (which would otherwise bump the modified time we just set).
     */
    suspend fun apply(
        context: Context,
        changes: List<Change>,
        writeExif: Boolean,
        writeFileDate: Boolean,
        writeVideoMeta: Boolean = false,
        onProgress: (Progress) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        var succeeded = 0
        var failed = 0
        var exifWritten = 0
        var videoWritten = 0
        var fileDatesSet = 0
        var bytesDone = 0L
        val errors = mutableListOf<String>()
        val scanned = mutableListOf<String>()

        val startedAt = System.currentTimeMillis()
        val bytesTotal = changes.sumOf { runCatching { it.file.length() }.getOrDefault(0L) }

        fun report(done: Int, name: String, isVideo: Boolean) {
            onProgress(
                Progress(
                    done = done,
                    total = changes.size,
                    currentName = name,
                    currentIsVideo = isVideo,
                    exifWritten = exifWritten,
                    videoWritten = videoWritten,
                    fileDatesSet = fileDatesSet,
                    failed = failed,
                    bytesDone = bytesDone,
                    bytesTotal = bytesTotal,
                    startedAt = startedAt,
                )
            )
        }

        changes.forEachIndexed { index, change ->
            val file = change.file
            val isVideo = Mp4Meta.isSupported(file)
            report(index, file.name, isVideo)
            if (!file.isFile) {
                failed++
                errors.add("${file.name}: file no longer exists")
                report(index + 1, file.name, isVideo)
                return@forEachIndexed
            }
            val size = runCatching { file.length() }.getOrDefault(0L)

            var wroteSomething = false
            var itemFailed = false

            if (writeExif && file.extension.lowercase() in writableExif) {
                val result = runCatching {
                    val exif = ExifInterface(file.absolutePath)
                    val stamp = exifFormat.format(Date(change.newMillis))
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
                    exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
                    exif.saveAttributes()
                }
                if (result.isSuccess) {
                    exifWritten++
                    wroteSomething = true
                } else {
                    itemFailed = true
                    errors.add("${file.name}: ${result.exceptionOrNull()?.message ?: "could not write EXIF"}")
                }
            }

            if (writeVideoMeta && isVideo) {
                if (Mp4Meta.writeCreation(file, change.newMillis)) {
                    videoWritten++
                    wroteSomething = true
                } else {
                    itemFailed = true
                    errors.add("${file.name}: could not write video creation time")
                }
            }

            if (writeFileDate) {
                if (file.setLastModified(change.newMillis)) {
                    fileDatesSet++
                    wroteSomething = true
                } else {
                    itemFailed = true
                    errors.add("${file.name}: could not set file date")
                }
            }

            if (wroteSomething && !itemFailed) succeeded++ else failed++
            if (wroteSomething) scanned.add(file.absolutePath)
            bytesDone += size
            report(index + 1, file.name, isVideo)
        }

        // Let the gallery pick the new dates up.
        if (scanned.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(context, scanned.toTypedArray(), null, null)
            }
        }
        Outcome(succeeded, failed, exifWritten, videoWritten, errors.take(20))
    }
}
