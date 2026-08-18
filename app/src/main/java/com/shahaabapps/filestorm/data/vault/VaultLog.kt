package com.shahaabapps.filestorm.data.vault

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VaultLogLevel(val label: String, val blurb: String) {
    OFF("Off", "Nothing is written"),
    ERRORS("Errors only", "Just what went wrong"),
    DETAILED("Detailed", "Every step, for tracing a fault"),
}

/**
 * A log written to a file so a fault can be traced after the fact.
 *
 * It lives in the app's own storage, not in the vault and not on shared
 * storage. What it must never contain is the passphrase, the recovery code, any
 * key material, or file contents — and by default not filenames either, since a
 * log naming `Passport_scan.pdf` gives away exactly what the vault exists to
 * hide. Files are identified by a short stable hash unless the user turns names
 * on deliberately.
 */
object VaultLog {

    private const val FILE_NAME = "vault-log.txt"
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private var directory: File? = null

    fun init(context: Context) {
        directory = context.filesDir
    }

    fun file(): File? = directory?.let { File(it, FILE_NAME) }

    fun detail(message: String) = write(VaultLogLevel.DETAILED, "  ", message)

    fun info(message: String) = write(VaultLogLevel.DETAILED, "· ", message)

    fun error(message: String) = write(VaultLogLevel.ERRORS, "! ", message)

    /**
     * Describes a file for the log. Returns a short hash unless the user has
     * asked for real names, so an ordinary log can be shared without leaking
     * what is in the vault.
     */
    fun name(path: String): String =
        if (VaultPrefs.logFilenames) path.substringAfterLast('/')
        else "file:" + VaultEngine.shortId(path)

    private fun write(level: VaultLogLevel, prefix: String, message: String) {
        val configured = VaultPrefs.logLevel
        if (configured == VaultLogLevel.OFF) return
        if (level == VaultLogLevel.DETAILED && configured != VaultLogLevel.DETAILED) return
        val target = file() ?: return
        runCatching {
            trimIfNeeded(target)
            target.appendText("${stamp.format(Date())} $prefix$message\n")
        }
    }

    /** Keeps the newest half when the limit is reached rather than losing it all. */
    private fun trimIfNeeded(target: File) {
        val limit = VaultPrefs.logSizeMb * 1024L * 1024L
        if (!target.isFile || target.length() < limit) return
        val lines = target.readLines()
        target.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
    }

    fun clear() {
        runCatching { file()?.delete() }
    }

    fun sizeBytes(): Long = file()?.takeIf { it.isFile }?.length() ?: 0L
}
