package com.shahabcodes.filestorm.data.meta

import java.util.Calendar

/**
 * Recovers a capture date from common camera / messenger filename conventions.
 * Used when a file's EXIF metadata has been stripped (WhatsApp does this).
 */
object FilenameDate {

    data class Parsed(
        val millis: Long,
        val hasTime: Boolean,
        val source: String,
    )

    private class Pattern(
        val label: String,
        val regex: Regex,
        val hasTime: Boolean,
        val epoch: Boolean = false,
    )

    // Ordered: patterns carrying a time win over date-only ones.
    private val patterns = listOf(
        Pattern(
            "WhatsApp name",
            Regex("""(?:IMG|VID|AUD|PTT|DOC)-(\d{4})(\d{2})(\d{2})-WA\d+""", RegexOption.IGNORE_CASE),
            hasTime = false,
        ),
        Pattern(
            "Camera name",
            Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})[_\-. ](\d{2})(\d{2})(\d{2})(?!\d)"""),
            hasTime = true,
        ),
        Pattern(
            "Camera name",
            Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})[_\-. ](\d{2})(\d{2})(\d{2})\d{1,3}(?!\d)"""),
            hasTime = true,
        ),
        Pattern(
            "Screenshot name",
            Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})[_\-. ](\d{2})[\-.:](\d{2})[\-.:](\d{2})"""),
            hasTime = true,
        ),
        Pattern(
            "Dated name",
            Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})[_\-. ](\d{2})(\d{2})(\d{2})(?!\d)"""),
            hasTime = true,
        ),
        Pattern(
            "Dated name",
            Regex("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)"""),
            hasTime = false,
        ),
        Pattern(
            "Dated name",
            Regex("""(?<!\d)(\d{4})(\d{2})(\d{2})(?!\d)"""),
            hasTime = false,
        ),
        Pattern(
            "Timestamp name",
            Regex("""(?<!\d)(1\d{12})(?!\d)"""),
            hasTime = true,
            epoch = true,
        ),
    )

    fun parse(fileName: String): Parsed? {
        val base = fileName.substringBeforeLast('.', fileName)
        for (pattern in patterns) {
            val match = pattern.regex.find(base) ?: continue
            val millis = if (pattern.epoch) {
                match.groupValues[1].toLongOrNull() ?: continue
            } else {
                val g = match.groupValues
                build(
                    year = g[1].toIntOrNull() ?: continue,
                    month = g[2].toIntOrNull() ?: continue,
                    day = g[3].toIntOrNull() ?: continue,
                    hour = g.getOrNull(4)?.toIntOrNull() ?: 12,
                    minute = g.getOrNull(5)?.toIntOrNull() ?: 0,
                    second = g.getOrNull(6)?.toIntOrNull() ?: 0,
                ) ?: continue
            }
            if (!plausible(millis)) continue
            return Parsed(millis, pattern.hasTime, pattern.label)
        }
        return null
    }

    private fun build(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long? {
        if (month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.isLenient = false
        return runCatching {
            cal.set(year, month - 1, day, hour, minute, second)
            cal.timeInMillis
        }.getOrNull()
    }

    /** Rejects absurd dates so random digit runs never become timestamps. */
    private fun plausible(millis: Long): Boolean {
        val now = System.currentTimeMillis()
        val earliest = Calendar.getInstance().apply {
            clear()
            set(1995, 0, 1)
        }.timeInMillis
        return millis in earliest..(now + 24L * 60 * 60 * 1000)
    }
}
