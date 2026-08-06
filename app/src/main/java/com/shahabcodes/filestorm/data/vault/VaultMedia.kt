package com.shahabcodes.filestorm.data.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.shahabcodes.filestorm.data.FileKind
import com.shahabcodes.filestorm.data.FsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The Android half of the vault: making previews, and getting a file back out
 * far enough to look at without taking it out of the vault.
 *
 * Decrypted copies live in the app's own cache, never on shared storage, and
 * are wiped whenever the vault locks — otherwise looking at one photo would
 * quietly leave a readable copy behind.
 */
object VaultMedia {

    private const val THUMB_MAX = 256
    private const val THUMB_QUALITY = 72
    private const val CACHE_DIR = "vault-open"

    private var cacheRoot: File? = null

    fun init(context: Context) {
        cacheRoot = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        // Anything left from a previous run is a decrypted file nobody asked
        // to keep.
        clearCache()
    }

    /** A small JPEG preview, or null for anything without a picture. */
    fun thumbnailFor(file: File): ByteArray? = runCatching {
        val kind = FsEntry.kindOf(file.name, false)
        val bitmap = when (kind) {
            FileKind.IMAGE -> decodeImage(file)
            FileKind.VIDEO -> decodeVideoFrame(file)
            else -> null
        } ?: return null
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }.getOrNull()

    private fun decodeImage(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > THUMB_MAX || bounds.outHeight / sample > THUMB_MAX) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun decodeVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.getScaledFrameAtTime(
                -1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_MAX, THUMB_MAX,
            ) ?: retriever.frameAtTime
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun thumbnailBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    /**
     * Decrypts far enough to open, into app-private cache. The original stays
     * encrypted; this is a copy that exists only until the vault locks.
     */
    suspend fun openCopy(
        sealedFile: File,
        masterKey: ByteArray,
        info: VaultFileInfo,
    ): File? = withContext(Dispatchers.IO) {
        val root = cacheRoot ?: return@withContext null
        root.mkdirs()
        // Keeps the real extension so players and decoders behave normally,
        // while the name itself stays unrelated to anything on disk.
        val extension = info.name.substringAfterLast('.', "")
        val target = File(
            root,
            sealedFile.nameWithoutExtension + if (extension.isEmpty()) "" else ".$extension",
        )
        if (target.isFile && target.length() == info.size) return@withContext target
        runCatching {
            VaultContainer.decrypt(sealedFile, target, masterKey)
            if (info.modified > 0) target.setLastModified(info.modified)
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    fun clearCache() {
        runCatching { cacheRoot?.listFiles()?.forEach { it.delete() } }
    }

    fun cachedBytes(): Long =
        cacheRoot?.listFiles()?.sumOf { it.length() } ?: 0L
}
