package com.shahabcodes.filestorm.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The cards the dashboard can show. Each one is opt-in and, crucially, nothing
 * is computed for a card that is switched off — [StorageInsights] is told which
 * cards are enabled and skips the work for the rest, so a disabled card costs
 * exactly nothing rather than being calculated and then hidden.
 */
enum class DashboardCard(
    val key: String,
    val label: String,
    val blurb: String,
    /** True when this card's data comes from the whole-storage walk. */
    val needsScan: Boolean,
) {
    STORAGE("storage", "Storage", "Used and free space, with a breakdown by file type", true),
    RECLAIM("reclaim", "Reclaim Space", "Trash, duplicates, empty folders and zero-byte files", true),
    BIGGEST_FILES("biggest", "Biggest Files", "The largest files on the device, newest scan", true),
    LARGEST_FOLDERS("folders", "Largest Folders", "Which folders are holding the most", true),
    GROWTH("growth", "Storage Growth", "How much you added each month", true),
    RECENT("recent", "Recent Files", "Everything added or changed in the last week", true),
    APPS("apps", "Apps", "Per-app storage — needs Usage Access from system settings", false),
}

/** Which dashboard cards are switched on, and in which order they appear. */
object DashboardPrefs {
    private lateinit var sp: SharedPreferences

    private val defaults = setOf(
        DashboardCard.STORAGE,
        DashboardCard.RECLAIM,
        DashboardCard.BIGGEST_FILES,
        DashboardCard.LARGEST_FOLDERS,
    )

    var enabled by mutableStateOf(defaults)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("filestorm_dashboard_cards", Context.MODE_PRIVATE)
        val stored = sp.getStringSet("enabled", null)
        enabled = if (stored == null) defaults
        else DashboardCard.entries.filter { it.key in stored }.toSet()
    }

    fun isEnabled(card: DashboardCard): Boolean = card in enabled

    fun setEnabled(card: DashboardCard, value: Boolean) {
        enabled = if (value) enabled + card else enabled - card
        sp.edit().putStringSet("enabled", enabled.map { it.key }.toSet()).apply()
    }

    /** Cards needing the storage walk. Empty means the walk never runs. */
    fun scanningCards(): Set<DashboardCard> = enabled.filter { it.needsScan }.toSet()
}
