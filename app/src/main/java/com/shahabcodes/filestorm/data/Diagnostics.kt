package com.shahabcodes.filestorm.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory trace of the events that matter for the "wrong file opens" report.
 * Recording is always on and costs a string per event; the overlay that shows it
 * is opt-in from Settings, so a trace already exists when the bug is noticed.
 */
object Diagnostics {

    private const val MAX_EVENTS = 300
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val counter = AtomicInteger(0)

    val events = mutableStateListOf<String>()

    /** Unique id per composition instance, used to prove screen reuse. */
    fun nextInstanceId(): Int = counter.incrementAndGet()

    fun log(tag: String, message: String) {
        val line = "${stamp.format(Date())}  $tag  $message"
        // Only mirror to logcat while diagnostics are on, so a shipped build
        // never writes file paths to the system log.
        if (Prefs.diagnostics) android.util.Log.d("FileStormDiag", line)
        events.add(line)
        while (events.size > MAX_EVENTS) events.removeAt(0)
    }

    fun clear() = events.clear()

    fun dump(): String = buildString {
        appendLine("File Storm diagnostics")
        appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("android: ${android.os.Build.VERSION.RELEASE} (sdk ${android.os.Build.VERSION.SDK_INT})")
        appendLine("events: ${events.size}")
        appendLine("---")
        events.forEach { appendLine(it) }
    }

    /** Compact description of a file for the trace. */
    fun describe(entry: FsEntry): String =
        "${entry.name} [${entry.kind}] ${entry.path}"
}
