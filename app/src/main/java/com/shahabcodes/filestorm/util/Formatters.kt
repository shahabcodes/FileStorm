package com.shahabcodes.filestorm.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    fun bytes(value: Long): String {
        if (value < 0) return "—"
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var unit = -1
        while (v >= 1024 && unit < units.lastIndex) {
            v /= 1024
            unit++
        }
        return if (v >= 100) "${v.roundToInt()} ${units[unit]}"
        else String.format(Locale.US, "%.1f %s", v, units[unit])
    }

    fun speed(bytesPerSecond: Double): String {
        if (bytesPerSecond <= 0) return "—"
        return bytes(bytesPerSecond.toLong()) + "/s"
    }

    /** Thousands separators for counts that can run into six figures. */
    fun compactCount(value: Int): String {
        if (value < 1000) return value.toString()
        return String.format(Locale.US, "%,d", value)
    }

    fun eta(seconds: Long): String {
        if (seconds < 0) return "—"
        if (seconds < 60) return "${seconds}s"
        val m = seconds / 60
        val s = seconds % 60
        if (m < 60) return "${m}m ${s}s"
        val h = m / 60
        return "${h}h ${m % 60}m"
    }

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.US)
    private val dayMonthFmt = SimpleDateFormat("d MMM yyyy", Locale.US)
    private val fullFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US)

    fun fileDate(millis: Long): String {
        if (millis <= 0) return "—"
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = Date(millis) }
        return when {
            sameDay(now, then) -> "Today, " + timeFmt.format(Date(millis))
            isYesterday(now, then) -> "Yesterday, " + timeFmt.format(Date(millis))
            else -> dayMonthFmt.format(Date(millis))
        }
    }

    fun fullDate(millis: Long): String = if (millis <= 0) "—" else fullFmt.format(Date(millis))

    fun shortDate(millis: Long): String = dayMonthFmt.format(Date(millis))

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(y, then)
    }
}
