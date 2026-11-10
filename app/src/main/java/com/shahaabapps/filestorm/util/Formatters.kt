package com.shahaabapps.filestorm.util

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

    /**
     * A size short enough to sit under a chart bar, but still carrying its
     * unit — "290K", "420M". Dropping the unit entirely makes 290 KB and
     * 420 MB read as neighbours.
     */
    fun compactBytes(value: Long): String {
        val full = bytes(value)
        val unit = full.substringAfter(' ', "")
        return full.substringBefore(' ') + unit.take(1)
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

    /** Midnight either side of today, so "is this today" is plain long maths. */
    private class DayBounds(
        val yesterdayStart: Long,
        val todayStart: Long,
        val tomorrowStart: Long,
    )

    @Volatile
    private var dayBounds: DayBounds? = null

    /**
     * Held until the day turns over.
     *
     * Every visible row asks for a date, on every recomposition, and working it
     * out used to cost three `Calendar.getInstance()` calls — each one reading
     * the clock, the locale and the timezone. Scrolling a large folder spent
     * most of a frame doing nothing but rebuilding calendars.
     */
    private fun bounds(): DayBounds {
        val now = System.currentTimeMillis()
        dayBounds?.let { if (now >= it.todayStart && now < it.tomorrowStart) return it }
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        // Via Calendar rather than subtracting a fixed day, so the clocks
        // changing does not make yesterday an hour long or an hour short.
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 2)
        return DayBounds(yesterdayStart, todayStart, cal.timeInMillis)
            .also { dayBounds = it }
    }

    fun fileDate(millis: Long): String {
        if (millis <= 0) return "—"
        val b = bounds()
        return when {
            millis >= b.todayStart && millis < b.tomorrowStart ->
                "Today, " + timeFmt.format(Date(millis))
            millis >= b.yesterdayStart && millis < b.todayStart ->
                "Yesterday, " + timeFmt.format(Date(millis))
            else -> dayMonthFmt.format(Date(millis))
        }
    }

    fun fullDate(millis: Long): String = if (millis <= 0) "—" else fullFmt.format(Date(millis))

    fun shortDate(millis: Long): String = dayMonthFmt.format(Date(millis))

}
