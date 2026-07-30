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
    )

    data class Change(
        val file: File,
        val newMillis: Long,
        val source: String,
    )

    data class Outcome(
        val succeeded: Int,
        val failed: Int,
        val exifWritten: Int,
        val errors: List<String>,
    )

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
        return Current(
            fileDate = file.lastModified(),
            exifDate = exifDate,
            exifWritable = ext in writableExif,
            isImage = isImage,
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
        onProgress: (done: Int) -> Unit = {},
    ): Outcome = withContext(Dispatchers.IO) {
        var succeeded = 0
        var failed = 0
        var exifWritten = 0
        val errors = mutableListOf<String>()
        val scanned = mutableListOf<String>()

        changes.forEachIndexed { index, change ->
            val file = change.file
            if (!file.isFile) {
                failed++
                errors.add("${file.name}: file no longer exists")
                onProgress(index + 1)
                return@forEachIndexed
            }

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

            if (writeFileDate) {
                if (file.setLastModified(change.newMillis)) {
                    wroteSomething = true
                } else {
                    itemFailed = true
                    errors.add("${file.name}: could not set file date")
                }
            }

            if (wroteSomething && !itemFailed) succeeded++ else failed++
            if (wroteSomething) scanned.add(file.absolutePath)
            onProgress(index + 1)
        }

        // Let the gallery pick the new dates up.
        if (scanned.isNotEmpty()) {
            runCatching {
                MediaScannerConnection.scanFile(context, scanned.toTypedArray(), null, null)
            }
        }
        Outcome(succeeded, failed, exifWritten, errors.take(20))
    }
}
